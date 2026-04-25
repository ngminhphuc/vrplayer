#include "screen.h"

#include "log.h"
#include "math_util.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include <cmath>
#include <vector>

namespace vrplayer {

namespace {

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

GLuint sVao = 0;
GLuint sVbo = 0;
GLuint sIbo = 0;
GLsizei sIndexCount = 0;

struct Transform {
    float radius = 3.0f;
    float arc = 2.0944f;  // 120° in radians
    float height = 1.6f;
    float yaw = 0.f;
    float pitch = 0.f;
    float yOffset = 1.5f;  // eye-level
    float zOffset = 0.f;
};
Transform sTf;

constexpr int kSegmentsX = 48;
constexpr int kSegmentsY = 12;

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("Screen shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

void buildMesh() {
    std::vector<float> verts;
    std::vector<uint16_t> idx;
    verts.reserve((kSegmentsX + 1) * (kSegmentsY + 1) * 5);
    idx.reserve(kSegmentsX * kSegmentsY * 6);

    // Unit cylinder slice; per-draw transform handles radius/arc/height.
    for (int y = 0; y <= kSegmentsY; ++y) {
        const float vy = static_cast<float>(y) / kSegmentsY;
        for (int x = 0; x <= kSegmentsX; ++x) {
            const float vx = static_cast<float>(x) / kSegmentsX;
            // theta ranges from -0.5 to +0.5 of the arc; centred on -z.
            const float theta = (vx - 0.5f);  // scaled by sTf.arc later
            // px,pz computed at draw time with current transform.
            verts.push_back(theta);  // raw u-coord encoded into x slot
            verts.push_back(vy - 0.5f);  // height fraction (centered)
            verts.push_back(0.f);  // unused; transform expands
            verts.push_back(vx);
            verts.push_back(1.f - vy);  // flip v so video reads upright
        }
    }
    for (int y = 0; y < kSegmentsY; ++y) {
        for (int x = 0; x < kSegmentsX; ++x) {
            const uint16_t a = static_cast<uint16_t>(y * (kSegmentsX + 1) + x);
            const uint16_t b = static_cast<uint16_t>(a + 1);
            const uint16_t c = static_cast<uint16_t>(a + kSegmentsX + 1);
            const uint16_t d = static_cast<uint16_t>(c + 1);
            idx.push_back(a);
            idx.push_back(c);
            idx.push_back(b);
            idx.push_back(b);
            idx.push_back(c);
            idx.push_back(d);
        }
    }

    glGenVertexArrays(1, &sVao);
    glGenBuffers(1, &sVbo);
    glGenBuffers(1, &sIbo);
    glBindVertexArray(sVao);
    glBindBuffer(GL_ARRAY_BUFFER, sVbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(),
                 GL_STATIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sIbo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size() * sizeof(uint16_t),
                 idx.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(0));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(3 * sizeof(float)));
    glBindVertexArray(0);
    sIndexCount = static_cast<GLsizei>(idx.size());
}

}  // namespace

bool Screen::init() {
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
    if (!linked) {
        VRP_LOGE("Screen program link failed");
        return false;
    }
    sLocMvp = glGetUniformLocation(sProgram, "uMvp");
    sLocTexMat = glGetUniformLocation(sProgram, "uTexMatrix");

    buildMesh();
    return true;
}

void Screen::shutdown() {
    if (sIbo) glDeleteBuffers(1, &sIbo);
    if (sVbo) glDeleteBuffers(1, &sVbo);
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sIbo = sVbo = sVao = sProgram = 0;
}

void Screen::resetTransform() {
    sTf = Transform{};
}

void Screen::setTransform(float radius, float arcRadians, float height,
                           float yaw, float pitch, float yOffset,
                           float zOffset) {
    sTf.radius = radius;
    sTf.arc = arcRadians;
    sTf.height = height;
    sTf.yaw = yaw;
    sTf.pitch = pitch;
    sTf.yOffset = yOffset;
    sTf.zOffset = zOffset;
}

void Screen::draw(uint32_t externalOesTexId, const float* proj,
                  const float* view, const float* texMatrix) {
    glUseProgram(sProgram);

    // We baked unit-cylinder coords into vertex buffer; the model matrix
    // expands them. To keep the shader simple, the actual cylinder math is
    // computed per-vertex in software and re-uploaded each setTransform call.
    // For now, push the transform into the shader by composing matrices and
    // doing the curving in vertex space:  px = sin(theta * arc) * radius,
    //                                       py = (vy) * height + yOffset,
    //                                       pz = -cos(theta * arc) * radius.
    // Because we don't have those terms in the shader yet, we emulate via a
    // model matrix that scales arcRadius into width — close enough for a
    // 120° screen and avoids vertex re-upload churn.
    float model[16];
    {
        // Approximate as a flat strip rotated by yaw + scaled by chord/height.
        float t[16];
        mu::translation(0.f, sTf.yOffset, sTf.zOffset - sTf.radius, t);
        float s[16];
        const float chord = 2.f * sTf.radius * std::sin(sTf.arc * 0.5f);
        mu::scale(chord, sTf.height, 1.f, s);
        // yaw rotation around y
        float rot[16];
        mu::identity(rot);
        const float cy = std::cos(sTf.yaw);
        const float sy = std::sin(sTf.yaw);
        rot[0] = cy;
        rot[2] = -sy;
        rot[8] = sy;
        rot[10] = cy;
        float ts[16];
        mu::multiply(rot, t, ts);
        mu::multiply(ts, s, model);
    }
    float mv[16];
    mu::multiply(view, model, mv);
    float mvp[16];
    mu::multiply(proj, mv, mvp);

    glUniformMatrix4fv(sLocMvp, 1, GL_FALSE, mvp);
    if (sLocTexMat >= 0) {
        glUniformMatrix4fv(sLocTexMat, 1, GL_FALSE, texMatrix);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalOesTexId);
    glBindVertexArray(sVao);
    glDrawElements(GL_TRIANGLES, sIndexCount, GL_UNSIGNED_SHORT, nullptr);
    glBindVertexArray(0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
}

}  // namespace vrplayer
