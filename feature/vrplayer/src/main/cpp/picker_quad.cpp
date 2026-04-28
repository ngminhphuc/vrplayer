#include "picker_quad.h"

#include "log.h"
#include "math_util.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

namespace vrplayer {

namespace {

constexpr float kHalfW = 0.6f;     // 1.2 m wide
constexpr float kHalfH = 0.4f;     // 0.8 m tall
constexpr float kY = 1.5f;          // eye-level
constexpr float kZ = -1.4f;         // 1.4 m in front

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
bool sVisible = false;

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("PickerQuad shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

bool PickerQuad::init() {
    GLuint vs = compile(GL_VERTEX_SHADER, kVS);
    GLuint fs = compile(GL_FRAGMENT_SHADER, kFS);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return false;
    }
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

    const float v[] = {
        // pos                          uv
        -kHalfW, -kHalfH + kY, kZ, 0.f, 1.f,
         kHalfW, -kHalfH + kY, kZ, 1.f, 1.f,
        -kHalfW,  kHalfH + kY, kZ, 0.f, 0.f,
         kHalfW,  kHalfH + kY, kZ, 1.f, 0.f,
    };
    glGenVertexArrays(1, &sVao);
    glGenBuffers(1, &sVbo);
    glBindVertexArray(sVao);
    glBindBuffer(GL_ARRAY_BUFFER, sVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(v), v, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(0));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(3 * sizeof(float)));
    glBindVertexArray(0);
    return true;
}

void PickerQuad::shutdown() {
    if (sVbo) glDeleteBuffers(1, &sVbo);
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sVbo = sVao = sProgram = 0;
}

void PickerQuad::setVisible(bool v) { sVisible = v; }
bool PickerQuad::visible() { return sVisible; }

void PickerQuad::draw(uint32_t externalOesTexId, const float* proj,
                      const float* view, const float* texMatrix) {
    if (!sVisible) return;
    glUseProgram(sProgram);

    float mvp[16];
    mu::multiply(proj, view, mvp);
    glUniformMatrix4fv(sLocMvp, 1, GL_FALSE, mvp);
    if (sLocTexMat >= 0) {
        glUniformMatrix4fv(sLocTexMat, 1, GL_FALSE, texMatrix);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalOesTexId);
    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
}

bool PickerQuad::hitTest(const XrPosef& aim, float* outU, float* outV) {
    // Ray origin = aim.position, direction = aim rotated -Z axis.
    const float ox = aim.position.x;
    const float oy = aim.position.y;
    const float oz = aim.position.z;
    // Quaternion-rotate (0, 0, -1) by aim.orientation.
    const float qx = aim.orientation.x;
    const float qy = aim.orientation.y;
    const float qz = aim.orientation.z;
    const float qw = aim.orientation.w;
    // d = q * (0,0,-1) * q^-1 — simplified.
    const float dx = -2.f * (qx * qz + qw * qy);
    const float dy = -2.f * (qy * qz - qw * qx);
    const float dz = -(1.f - 2.f * (qx * qx + qy * qy));

    if (dz >= -1e-4f) return false;  // ray pointing forward into +z half
    const float t = (kZ - oz) / dz;
    if (t <= 0.f) return false;

    const float hx = ox + t * dx;
    const float hy = oy + t * dy;
    if (hx < -kHalfW || hx > kHalfW) return false;
    if (hy < kY - kHalfH || hy > kY + kHalfH) return false;

    *outU = (hx + kHalfW) / (2.f * kHalfW);
    *outV = 1.f - (hy - (kY - kHalfH)) / (2.f * kHalfH);
    return true;
}

}  // namespace vrplayer
