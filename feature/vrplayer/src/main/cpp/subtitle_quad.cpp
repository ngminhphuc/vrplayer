#include "subtitle_quad.h"

#include "log.h"
#include "math_util.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

namespace vrplayer {

namespace {

constexpr float kHalfW = 1.0f;     // 2.0 m wide
constexpr float kHalfH = 0.15f;    // 0.3 m tall
constexpr float kY = 0.6f;          // below eye level
constexpr float kZ = -3.0f;         // 3 m in front (deeper than picker)

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

// Premultiplied alpha blend: SurfaceTexture from a transparent
// Compose surface delivers cleared regions as RGBA(0,0,0,0), so
// straight texture sample + GL_ONE / GL_ONE_MINUS_SRC_ALPHA gives
// us a clean overlay against the cinema screen behind.
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
        VRP_LOGE("SubtitleQuad shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

bool SubtitleQuad::init() {
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

void SubtitleQuad::shutdown() {
    if (sVbo) glDeleteBuffers(1, &sVbo);
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sVbo = sVao = sProgram = 0;
}

void SubtitleQuad::setVisible(bool v) { sVisible = v; }
bool SubtitleQuad::visible() { return sVisible; }

void SubtitleQuad::draw(uint32_t externalOesTexId, const float* proj,
                        const float* view, const float* texMatrix) {
    if (!sVisible) return;
    glUseProgram(sProgram);

    float mvp[16];
    mu::multiply(proj, view, mvp);
    glUniformMatrix4fv(sLocMvp, 1, GL_FALSE, mvp);
    if (sLocTexMat >= 0) {
        glUniformMatrix4fv(sLocTexMat, 1, GL_FALSE, texMatrix);
    }
    // Alpha-blend so the transparent regions of the Compose surface
    // (everything outside the cue text) reveal the cinema screen
    // behind the quad. Straight (non-premultiplied) blend matches
    // Android Surface alpha semantics.
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDepthMask(GL_FALSE);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalOesTexId);
    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glDepthMask(GL_TRUE);
    glDisable(GL_BLEND);
}

}  // namespace vrplayer
