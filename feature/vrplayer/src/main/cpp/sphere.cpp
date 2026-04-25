#include "sphere.h"

#include "log.h"
#include "math_util.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include <atomic>
#include <cmath>
#include <vector>

namespace vrplayer {

namespace {

constexpr float kRadius = 50.f;
constexpr int kRings = 48;
constexpr int kSegs = 96;

// Equirect: u = (yaw + π) / 2π, v = (pitch + π/2) / π.
// We invert face winding so we render the inside of the sphere.
constexpr const char* kVS = R"glsl(#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uMvp;
uniform mat4 uTexMatrix;
out vec2 vUv;
void main() {
    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
)glsl";

constexpr const char* kFS = R"glsl(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vUv;
uniform samplerExternalOES uTex;
out vec4 fragColor;
void main() {
    fragColor = texture(uTex, vUv);
}
)glsl";

GLuint sProgram = 0;
GLint sLocMvp = -1;
GLint sLocTexMat = -1;
GLuint sVao360 = 0, sVbo360 = 0, sIbo360 = 0;
GLuint sVao180 = 0, sVbo180 = 0, sIbo180 = 0;
GLsizei sIndexCount360 = 0;
GLsizei sIndexCount180 = 0;
// Cross-thread: written from JNI (UI thread), read from native render
// thread. Use atomics so the compiler can't reorder/cache reads.
std::atomic<Sphere::Mode> sMode{Sphere::Mode::Off};
std::atomic<float> sYawOffsetRad{0.f};

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("Sphere shader: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

struct Mesh {
    std::vector<float> verts;     // [x y z u v] interleaved
    std::vector<uint16_t> idx;
};

// uMin/uMax let us clamp the equirect U range; for 180° hemisphere we map
// the front half of the sphere to the full UV [0..1] so the video fills it.
Mesh buildSphere(float yawStart, float yawEnd, float uMin, float uMax) {
    Mesh out;
    out.verts.reserve((kRings + 1) * (kSegs + 1) * 5);
    for (int r = 0; r <= kRings; ++r) {
        const float v = static_cast<float>(r) / kRings;       // [0..1] top→bottom
        const float pitch = (v - 0.5f) * static_cast<float>(M_PI);  // [-π/2..π/2]
        const float cy = std::sin(pitch);
        const float cr = std::cos(pitch);
        for (int s = 0; s <= kSegs; ++s) {
            const float t = static_cast<float>(s) / kSegs;
            const float yaw = yawStart + (yawEnd - yawStart) * t;
            const float cx = std::cos(yaw);
            const float cz = std::sin(yaw);
            out.verts.push_back(kRadius * cr * cx);
            out.verts.push_back(kRadius * cy);
            out.verts.push_back(kRadius * cr * cz);
            const float u = uMin + (uMax - uMin) * t;
            out.verts.push_back(u);
            out.verts.push_back(v);
        }
    }
    // Inverted winding (CCW seen from the inside).
    for (int r = 0; r < kRings; ++r) {
        for (int s = 0; s < kSegs; ++s) {
            const uint16_t a = static_cast<uint16_t>(r * (kSegs + 1) + s);
            const uint16_t b = static_cast<uint16_t>(a + 1);
            const uint16_t c = static_cast<uint16_t>(a + (kSegs + 1));
            const uint16_t d = static_cast<uint16_t>(c + 1);
            out.idx.push_back(a); out.idx.push_back(c); out.idx.push_back(b);
            out.idx.push_back(b); out.idx.push_back(c); out.idx.push_back(d);
        }
    }
    return out;
}

void uploadMesh(const Mesh& m, GLuint& vao, GLuint& vbo, GLuint& ibo,
                GLsizei& count) {
    glGenVertexArrays(1, &vao);
    glGenBuffers(1, &vbo);
    glGenBuffers(1, &ibo);
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, m.verts.size() * sizeof(float),
                 m.verts.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(0));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(3 * sizeof(float)));
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.idx.size() * sizeof(uint16_t),
                 m.idx.data(), GL_STATIC_DRAW);
    glBindVertexArray(0);
    count = static_cast<GLsizei>(m.idx.size());
}

}  // namespace

bool Sphere::init() {
    GLuint vs = compile(GL_VERTEX_SHADER, kVS);
    GLuint fs = compile(GL_FRAGMENT_SHADER, kFS);
    if (!vs || !fs) return false;
    sProgram = glCreateProgram();
    glAttachShader(sProgram, vs);
    glAttachShader(sProgram, fs);
    glLinkProgram(sProgram);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint linked = 0;
    glGetProgramiv(sProgram, GL_LINK_STATUS, &linked);
    if (!linked) return false;
    sLocMvp = glGetUniformLocation(sProgram, "uMvp");
    sLocTexMat = glGetUniformLocation(sProgram, "uTexMatrix");

    const float twoPi = static_cast<float>(2 * M_PI);
    const float pi = static_cast<float>(M_PI);
    const float halfPi = static_cast<float>(M_PI / 2);

    // OpenXR forward is -Z. Vertex formula is x = R*cos(yaw), z = R*sin(yaw),
    // so -Z corresponds to yaw = 3π/2 (or equivalently -π/2). Standard
    // equirectangular video has its forward at u = 0.5; therefore the yaw
    // range that maps the front to u = 0.5 is [π/2, π/2 + 2π].
    Mesh full = buildSphere(halfPi, halfPi + twoPi, 0.f, 1.f);
    uploadMesh(full, sVao360, sVbo360, sIbo360, sIndexCount360);

    // VR180 hemisphere: center the front half on -Z. Yaw range [-π, 0]
    // means the centre at -π/2 = -Z is at u = 0.5; left edge at -π = +X
    // (left of viewer), right edge at 0 = +X actually +X… correction:
    // yaw=-π maps to (R*cos(-π), 0, R*sin(-π)) = (-R, 0, 0) = -X (left),
    // yaw= 0 maps to (R, 0, 0) = +X (right), centre yaw=-π/2 = -Z (front).
    Mesh half = buildSphere(-pi, 0.f, 0.f, 1.f);
    uploadMesh(half, sVao180, sVbo180, sIbo180, sIndexCount180);
    return true;
}

void Sphere::shutdown() {
    if (sIbo360) glDeleteBuffers(1, &sIbo360);
    if (sVbo360) glDeleteBuffers(1, &sVbo360);
    if (sVao360) glDeleteVertexArrays(1, &sVao360);
    if (sIbo180) glDeleteBuffers(1, &sIbo180);
    if (sVbo180) glDeleteBuffers(1, &sVbo180);
    if (sVao180) glDeleteVertexArrays(1, &sVao180);
    if (sProgram) glDeleteProgram(sProgram);
    sIbo360 = sVbo360 = sVao360 = 0;
    sIbo180 = sVbo180 = sVao180 = 0;
    sProgram = 0;
}

void Sphere::setMode(Mode m) { sMode.store(m, std::memory_order_release); }
Sphere::Mode Sphere::mode() { return sMode.load(std::memory_order_acquire); }
void Sphere::setYawOffsetDeg(float deg) {
    sYawOffsetRad.store(deg * static_cast<float>(M_PI) / 180.f, std::memory_order_release);
}

void Sphere::draw(uint32_t tex, const float* proj, const float* view,
                  const float* texMatrix) {
    const Mode m = sMode.load(std::memory_order_acquire);
    if (m == Mode::Off) return;
    GLuint vao = (m == Mode::Equirect360) ? sVao360 : sVao180;
    GLsizei count =
        (m == Mode::Equirect360) ? sIndexCount360 : sIndexCount180;
    if (!vao || !count) return;

    glUseProgram(sProgram);
    // Apply yaw offset by rotating the view; mathematically equivalent to
    // rotating the model. We modify the view's xz columns in-place via a
    // small helper.
    float yawMat[16];
    mu::identity(yawMat);
    const float yawOffset = sYawOffsetRad.load(std::memory_order_acquire);
    const float c = std::cos(yawOffset);
    const float s = std::sin(yawOffset);
    yawMat[0] = c;  yawMat[2] = s;
    yawMat[8] = -s; yawMat[10] = c;
    float rotated[16];
    mu::multiply(view, yawMat, rotated);
    float mvp[16];
    mu::multiply(proj, rotated, mvp);
    glUniformMatrix4fv(sLocMvp, 1, GL_FALSE, mvp);
    if (sLocTexMat >= 0) {
        glUniformMatrix4fv(sLocTexMat, 1, GL_FALSE, texMatrix);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
    glBindVertexArray(vao);
    glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_SHORT, nullptr);
    glBindVertexArray(0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
}

}  // namespace vrplayer
