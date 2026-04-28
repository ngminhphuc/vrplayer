#include "gl_renderer.h"

#include "log.h"
#include "math_util.h"
#include "picker_quad.h"
#include "pointer.h"
#include "subtitle_quad.h"
#include "quad.h"
#include "screen.h"
#include "skybox.h"
#include "sphere.h"
#include "stereo.h"
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

// Per-frame cached state populated by beginFrame() and consumed by
// renderEye(). Hoisting the SurfaceTexture updateTexImage / transform
// queries out of the per-eye loop guarantees both eyes sample the same
// decoded frame.
float sVideoTexMatrix[16] = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
float sPickerTexMatrix[16] = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
float sSubtitleTexMatrix[16] = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
bool sPickerHasFrame = false;
bool sSubtitleHasFrame = false;

// Build a column-major 4x4 that scales the input UV (treated as the .xy of
// a vec4) and adds an offset. Used to crop the video texture into per-eye
// halves for SBS / TB stereo content.
void buildStereoCrop(const float scaleOffset[4], float out[16]) {
    for (int i = 0; i < 16; ++i) out[i] = 0.f;
    out[0] = scaleOffset[0];
    out[5] = scaleOffset[1];
    out[10] = 1.f;
    out[12] = scaleOffset[2];
    out[13] = scaleOffset[3];
    out[15] = 1.f;
}

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
    PickerQuad::init();
    SubtitleQuad::init();
    Sphere::init();
    return true;
}

void GlRenderer::shutdown() {
    Sphere::shutdown();
    SubtitleQuad::shutdown();
    PickerQuad::shutdown();
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

void GlRenderer::beginFrame() {
    // Pull video frame once per OpenXR frame so both eyes sample identical
    // pixels even if the decoder posts a new frame between left and right
    // eye renders.
    VideoBridge::updateTexImage();
    VideoBridge::getTransformMatrix(sVideoTexMatrix);

    // Picker surface — only relevant when the picker is on screen.
    if (PickerQuad::visible() && VideoBridge::pickerTextureId() != 0) {
        VideoBridge::updatePickerTexImage();
        VideoBridge::getPickerTransformMatrix(sPickerTexMatrix);
        sPickerHasFrame = true;
    } else {
        sPickerHasFrame = false;
    }

    // Subtitle surface — lazily allocated on the render thread (this thread)
    // because requestSubtitleSurface() calls glGenTextures and needs the GL
    // context current.
    sSubtitleHasFrame = false;
    if (SubtitleQuad::visible()) {
        if (VideoBridge::subtitleTextureId() == 0) {
            VideoBridge::requestSubtitleSurface();
        }
        if (VideoBridge::subtitleTextureId() != 0) {
            VideoBridge::updateSubtitleTexImage();
            VideoBridge::getSubtitleTransformMatrix(sSubtitleTexMatrix);
            sSubtitleHasFrame = true;
        }
    }
}

void GlRenderer::renderEye(uint32_t glTextureId, int32_t width, int32_t height,
                           const XrView& view, int eyeIndex) {
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

    // Compose stereo UV crop (right-multiply) so each eye samples its half
    // of the texture. For mono this is identity → behaves as before.
    float stereoCrop[16];
    float stereoUv[4];
    Stereo::uvScaleOffset(eyeIndex, stereoUv);
    buildStereoCrop(stereoUv, stereoCrop);
    float texMatrixEye[16];
    mu::multiply(sVideoTexMatrix, stereoCrop, texMatrixEye);

    // 2. Either the cinema cylinder OR the immersive 360/180 sphere.
    if (Sphere::mode() == Sphere::Mode::Off) {
        Screen::draw(VideoBridge::textureId(), proj, viewMat, texMatrixEye);
    } else {
        Sphere::draw(VideoBridge::textureId(), proj, viewMat, texMatrixEye);
    }

    // 3. Picker (if visible). Texture was updated in beginFrame.
    if (PickerQuad::visible() && sPickerHasFrame) {
        PickerQuad::draw(VideoBridge::pickerTextureId(), proj, viewMat,
                         sPickerTexMatrix);
    }

    // 3b. Subtitle quad (alpha-blended) below the cinema screen.
    //     Visible only while the current cue is non-empty; visibility
    //     is toggled from Kotlin via nativeSetSubtitleVisible(). Texture
    //     was updated in beginFrame.
    if (SubtitleQuad::visible() && sSubtitleHasFrame) {
        SubtitleQuad::draw(VideoBridge::subtitleTextureId(), proj, viewMat,
                           sSubtitleTexMatrix);
    }

    // 4. Laser pointers from active controllers.
    if (sShowLeftPointer) Pointer::draw(sLeftPose, proj, viewMat);
    if (sShowRightPointer) Pointer::draw(sRightPose, proj, viewMat);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

}  // namespace vrplayer
