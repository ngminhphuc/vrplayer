#include "gl_renderer.h"

#include "log.h"
#include "quad.h"
#include "video_bridge.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

#include <cmath>

namespace vrplayer {

namespace {

GLuint sFbo = 0;
GLuint sDepthRb = 0;
int32_t sDepthW = 0;
int32_t sDepthH = 0;

void ensureDepth(int32_t w, int32_t h) {
    if (sDepthRb && w == sDepthW && h == sDepthH) return;
    if (sDepthRb) glDeleteRenderbuffers(1, &sDepthRb);
    glGenRenderbuffers(1, &sDepthRb);
    glBindRenderbuffer(GL_RENDERBUFFER, sDepthRb);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
    sDepthW = w;
    sDepthH = h;
}

void buildProjection(const XrFovf& fov, float zNear, float zFar, float* m) {
    const float l = std::tan(fov.angleLeft);
    const float r = std::tan(fov.angleRight);
    const float u = std::tan(fov.angleUp);
    const float d = std::tan(fov.angleDown);
    const float w = r - l;
    const float h = u - d;
    const float fr = zFar - zNear;
    m[0] = 2.f / w;          m[1] = 0;                 m[2] = 0;                          m[3] = 0;
    m[4] = 0;                 m[5] = 2.f / h;          m[6] = 0;                          m[7] = 0;
    m[8] = (r + l) / w;      m[9] = (u + d) / h;     m[10] = -(zFar + zNear) / fr;       m[11] = -1.f;
    m[12] = 0;                m[13] = 0;                m[14] = -(2.f * zFar * zNear) / fr; m[15] = 0;
}

void buildView(const XrPosef& pose, float* m) {
    // Inverse of pose (orientation, position) → view matrix.
    const float x = pose.orientation.x;
    const float y = pose.orientation.y;
    const float z = pose.orientation.z;
    const float w = pose.orientation.w;
    const float xx = x * x, yy = y * y, zz = z * z;
    const float xy = x * y, xz = x * z, yz = y * z;
    const float wx = w * x, wy = w * y, wz = w * z;

    float r[16] = {
        1 - 2 * (yy + zz), 2 * (xy + wz),     2 * (xz - wy),     0,
        2 * (xy - wz),     1 - 2 * (xx + zz), 2 * (yz + wx),     0,
        2 * (xz + wy),     2 * (yz - wx),     1 - 2 * (xx + yy), 0,
        0,                  0,                  0,                  1,
    };
    // Transpose (inverse of orthonormal rotation) directly into output column-major.
    m[0] = r[0]; m[1] = r[4]; m[2] = r[8];  m[3] = 0;
    m[4] = r[1]; m[5] = r[5]; m[6] = r[9];  m[7] = 0;
    m[8] = r[2]; m[9] = r[6]; m[10] = r[10]; m[11] = 0;
    // Inverse translation.
    m[12] = -(m[0] * pose.position.x + m[4] * pose.position.y +
              m[8] * pose.position.z);
    m[13] = -(m[1] * pose.position.x + m[5] * pose.position.y +
              m[9] * pose.position.z);
    m[14] = -(m[2] * pose.position.x + m[6] * pose.position.y +
              m[10] * pose.position.z);
    m[15] = 1.f;
}

}  // namespace

bool GlRenderer::init() {
    glGenFramebuffers(1, &sFbo);
    Quad::init();
    return true;
}

void GlRenderer::shutdown() {
    Quad::shutdown();
    if (sDepthRb) glDeleteRenderbuffers(1, &sDepthRb);
    if (sFbo) glDeleteFramebuffers(1, &sFbo);
    sDepthRb = 0;
    sFbo = 0;
}

uint32_t GlRenderer::createVideoTexture() {
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    return tex;
}

void GlRenderer::renderEye(uint32_t glTextureId, int32_t width, int32_t height,
                           const XrView& view) {
    ensureDepth(width, height);
    glBindFramebuffer(GL_FRAMEBUFFER, sFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                           glTextureId, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                              GL_RENDERBUFFER, sDepthRb);

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        VRP_LOGE("Framebuffer incomplete");
        return;
    }

    glViewport(0, 0, width, height);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    float proj[16];
    float viewMat[16];
    buildProjection(view.fov, 0.05f, 200.f, proj);
    buildView(view.pose, viewMat);

    // Pull next decoded video frame into the external OES texture.
    VideoBridge::updateTexImage();
    float texMatrix[16];
    VideoBridge::getTransformMatrix(texMatrix);

    Quad::draw(VideoBridge::textureId(), proj, viewMat, texMatrix);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

}  // namespace vrplayer
