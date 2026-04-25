#include "quad.h"

#include "log.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include <cstring>

namespace vrplayer {

namespace {

constexpr const char* kVS = R"glsl(#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uTexMatrix;
out vec2 vUv;
void main() {
    vec4 p = uProj * uView * vec4(aPos, 1.0);
    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
    gl_Position = p;
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
GLuint sVbo = 0;
GLuint sVao = 0;
GLint  sLocProj = -1;
GLint  sLocView = -1;
GLint  sLocTexMat = -1;

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("Shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

bool Quad::init() {
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
        char log[1024];
        glGetProgramInfoLog(sProgram, sizeof(log), nullptr, log);
        VRP_LOGE("Quad program link failed: %s", log);
        return false;
    }
    sLocProj = glGetUniformLocation(sProgram, "uProj");
    sLocView = glGetUniformLocation(sProgram, "uView");
    sLocTexMat = glGetUniformLocation(sProgram, "uTexMatrix");

    // 16:9 quad, 2 m wide, centred at z = -3 m.
    const float w = 1.0f, h = 0.5625f, z = -3.0f;
    const float verts[] = {
        // pos                 uv
        -w, -h, z,             0.f, 1.f,
         w, -h, z,             1.f, 1.f,
         w,  h, z,             1.f, 0.f,
        -w, -h, z,             0.f, 1.f,
         w,  h, z,             1.f, 0.f,
        -w,  h, z,             0.f, 0.f,
    };
    glGenVertexArrays(1, &sVao);
    glGenBuffers(1, &sVbo);
    glBindVertexArray(sVao);
    glBindBuffer(GL_ARRAY_BUFFER, sVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(0));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float),
                          reinterpret_cast<void*>(3 * sizeof(float)));
    glBindVertexArray(0);
    return true;
}

void Quad::shutdown() {
    if (sVbo) glDeleteBuffers(1, &sVbo);
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sVbo = sVao = sProgram = 0;
}

void Quad::draw(uint32_t externalOesTexId, const float* proj, const float* view,
                const float* texMatrix) {
    glUseProgram(sProgram);
    glUniformMatrix4fv(sLocProj, 1, GL_FALSE, proj);
    glUniformMatrix4fv(sLocView, 1, GL_FALSE, view);
    if (sLocTexMat >= 0) {
        glUniformMatrix4fv(sLocTexMat, 1, GL_FALSE, texMatrix);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, externalOesTexId);

    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glBindVertexArray(0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
}

}  // namespace vrplayer
