#include "skybox.h"

#include "log.h"

#include <GLES3/gl3.h>

namespace vrplayer {

namespace {

constexpr const char* kVS = R"glsl(#version 300 es
const vec2 verts[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
out vec2 vUv;
void main() {
    vec2 p = verts[gl_VertexID];
    vUv = (p + 1.0) * 0.5;
    gl_Position = vec4(p, 1.0, 1.0);
}
)glsl";

constexpr const char* kFS = R"glsl(#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
void main() {
    // Soft vertical gradient: pitch-black floor → faint indigo top.
    float t = vUv.y;
    vec3 c = mix(vec3(0.005, 0.005, 0.01), vec3(0.02, 0.02, 0.05), t);
    fragColor = vec4(c, 1.0);
}
)glsl";

GLuint sProgram = 0;
GLuint sVao = 0;

GLuint compile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        VRP_LOGE("Skybox shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

}  // namespace

bool Skybox::init() {
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
        VRP_LOGE("Skybox program link failed");
        return false;
    }
    glGenVertexArrays(1, &sVao);
    return true;
}

void Skybox::shutdown() {
    if (sVao) glDeleteVertexArrays(1, &sVao);
    if (sProgram) glDeleteProgram(sProgram);
    sVao = sProgram = 0;
}

void Skybox::draw() {
    glDisable(GL_DEPTH_TEST);
    glDepthMask(GL_FALSE);
    glUseProgram(sProgram);
    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);
    glDepthMask(GL_TRUE);
    glEnable(GL_DEPTH_TEST);
}

}  // namespace vrplayer
