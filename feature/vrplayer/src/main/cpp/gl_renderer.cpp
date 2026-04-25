#include "gl_renderer.h"

#include "log.h"
#include "math_util.h"
#include "pointer.h"
#include "quad.h"
#include "screen.h"
#include "skybox.h"
#include "video_bridge.h"

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>

namespace vrplayer {

namespace {

GLuint sFbo = 0;
GLuint sDepthRb = 0;
int32_t sDepthW = 0;
int32_t sDepthH = 0;

bool sShowLeftPointer = false;
bool sShowRightPointer = false;
XrPosef sLeftPose{};
XrPosef sRightPose{};

void ensureDepth(int32_t w, int32_t h) {
    if (sDepthRb && w == sDepthW && h == sDepthH) return;
    if (sDepthRb) glDeleteRenderbuffers(1, &sDepthRb);
    glGenRenderbuffers(1, &sDepthRb);
    glBindRenderbuffer(GL_RENDERBUFFER, sDepthRb);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
    sDepthW = w;
    sDepthH = h;
}

}  // namespace

bool GlRenderer::init() {
    glGenFramebuffers(1, &sFbo);
    Quad::init();
    Screen::init();
    Skybox::init();
    Pointer::init();
    return true;
}

void GlRenderer::shutdown() {
    Pointer::shutdown();
    Skybox::shutdown();
    Screen::shutdown();
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

void GlRenderer::setPointer(bool leftActive, const XrPosef& leftPose,
                            bool rightActive, const XrPosef& rightPose) {
    sShowLeftPointer = leftActive;
    sShowRightPointer = rightActive;
    sLeftPose = leftPose;
    sRightPose = rightPose;
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
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glClear(GL_DEPTH_BUFFER_BIT);

    float proj[16];
    float viewMat[16];
    mu::projection(view.fov, 0.05f, 200.f, proj);
    mu::poseToView(view.pose, viewMat);

    // 1. Skybox (full-screen, depth write off).
    Skybox::draw();

    // 2. Curved screen with the latest video frame.
    VideoBridge::updateTexImage();
    float texMatrix[16];
    VideoBridge::getTransformMatrix(texMatrix);
    Screen::draw(VideoBridge::textureId(), proj, viewMat, texMatrix);

    // 3. Laser pointers from active controllers.
    if (sShowLeftPointer) Pointer::draw(sLeftPose, proj, viewMat);
    if (sShowRightPointer) Pointer::draw(sRightPose, proj, viewMat);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

}  // namespace vrplayer
