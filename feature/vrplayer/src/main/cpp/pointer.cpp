#include "pointer.h"

#include "log.h"
#include "math_util.h"

#include <GLES3/gl3.h>

namespace vrplayer {

namespace {

constexpr const char* kVS = R"glsl(#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uMvp;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
}
)glsl";

constexpr const char* kFS = R"glsl(#version 300 es
precision mediump float;
out vec4 fragColor;
void main() {
    fragColor = vec4(0.4, 0.85, 1.0, 1.0);
}
)glsl";

GLuint sProgram = 0;
GLuint sVao = 0;
GLuint sVbo = 0;
GLint sLocMvp = -1;

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("Pointer shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

bool Pointer::init() {
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

    const float verts[] = {
        0.f, 0.f, 0.f,
        0.f, 0.f, -1.5f,
    };
    glGenVertexArrays(1, &sVao);
    glGenBuffers(1, &sVbo);
    glBindVertexArray(sVao);
    glBindBuffer(GL_ARRAY_BUFFER, sVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float),
                          reinterpret_cast<void*>(0));
    glBindVertexArray(0);
    return true;
}

void Pointer::shutdown() {
    if (sVbo) glDeleteBuffers(1, &sVbo);
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sVbo = sVao = sProgram = 0;
}

void Pointer::draw(const XrPosef& aim, const float* proj, const float* view) {
    float model[16];
    mu::poseToMat(aim, model);
    float mv[16];
    mu::multiply(view, model, mv);
    float mvp[16];
    mu::multiply(proj, mv, mvp);

    glUseProgram(sProgram);
    glUniformMatrix4fv(sLocMvp, 1, GL_FALSE, mvp);
    glLineWidth(2.f);
    glBindVertexArray(sVao);
    glDrawArrays(GL_LINES, 0, 2);
    glBindVertexArray(0);
}

}  // namespace vrplayer
