#include "skybox.h"

#include "log.h"

#include <GLES3/gl3.h>

#include <atomic>

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

// 4 procedural environments. Branching on a uniform int — Adreno can
// constant-fold this at draw time, and the shader only runs for ~3
// fullscreen pixels (post-Z-fail) so cost is negligible compared to
// the video sphere.
constexpr const char* kFS = R"glsl(#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform int uMode;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec3 c;
    float t = vUv.y;
    if (uMode == 0) {
        // Black Void — pitch black with faint indigo at zenith.
        c = mix(vec3(0.005, 0.005, 0.01), vec3(0.02, 0.02, 0.05), t);
    } else if (uMode == 1) {
        // Modern Cinema — warm floor, neutral mid, cool ceiling. Faint
        // vignette adds depth without needing an actual room mesh.
        vec3 floorC = vec3(0.06, 0.04, 0.03);
        vec3 midC   = vec3(0.04, 0.04, 0.05);
        vec3 ceilC  = vec3(0.02, 0.03, 0.05);
        c = (t < 0.5) ? mix(floorC, midC, t * 2.0) : mix(midC, ceilC, (t - 0.5) * 2.0);
        float vignette = smoothstep(0.0, 0.4, 1.0 - distance(vUv, vec2(0.5)));
        c *= 0.6 + 0.4 * vignette;
    } else if (uMode == 2) {
        // Drive-In night — deep navy → black at horizon, with a soft
        // "ground" band beneath horizon.
        if (t < 0.45) {
            c = mix(vec3(0.015, 0.012, 0.008), vec3(0.02, 0.02, 0.025), t / 0.45);
        } else {
            c = mix(vec3(0.03, 0.04, 0.08), vec3(0.005, 0.005, 0.02), (t - 0.45) / 0.55);
        }
    } else {
        // Space — black with sparse stars. Bucket UV, sample hash,
        // threshold to pick a few percent of cells.
        c = vec3(0.0);
        vec2 g = vUv * 240.0;
        vec2 cell = floor(g);
        float h = hash(cell);
        if (h > 0.992) {
            float bright = (h - 0.992) / 0.008;
            vec2 f = fract(g) - 0.5;
            float d = max(0.0, 1.0 - length(f) * 4.0);
            c += vec3(bright) * d;
        }
        // Faint Milky Way band.
        float band = smoothstep(0.0, 0.05, 0.05 - abs(vUv.y - 0.55));
        c += band * vec3(0.01, 0.01, 0.02);
    }
    fragColor = vec4(c, 1.0);
}
)glsl";

GLuint sProgram = 0;
GLuint sVao = 0;
GLint sLocMode = -1;

// Cross-thread: written from JNI (UI thread) via Skybox::setMode, read
// from native render thread in Skybox::draw.
std::atomic<Skybox::Mode> sMode{Skybox::Mode::BlackVoid};

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
    sLocMode = glGetUniformLocation(sProgram, "uMode");
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
    glUniform1i(sLocMode, static_cast<int>(sMode.load(std::memory_order_acquire)));
    glBindVertexArray(sVao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);
    glDepthMask(GL_TRUE);
    glEnable(GL_DEPTH_TEST);
}

void Skybox::setMode(Mode m) { sMode.store(m, std::memory_order_release); }
Skybox::Mode Skybox::mode() { return sMode.load(std::memory_order_acquire); }

}  // namespace vrplayer
