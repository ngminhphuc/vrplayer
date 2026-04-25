#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>

#include <cstdint>

namespace vrplayer {

/**
 * Tiny GLES 3 renderer for the Stage 0 spike. Owns:
 *   - the FBO used to render into the OpenXR swapchain colour image.
 *   - the quad VAO/VBO + shader program that draws the video texture.
 *
 * One instance is shared across both eyes; per-eye state (view/proj matrices)
 * is passed in each call to [renderEye].
 */
class GlRenderer {
public:
    static bool init();
    static void shutdown();

    /** Renders the cinema quad into the given GL texture (one of an OpenXR
     *  swapchain image set). The texture is bound as a colour attachment of an
     *  internal FBO; depth is stored separately. */
    static void renderEye(uint32_t glTextureId, int32_t width, int32_t height,
                          const XrView& view);

    /** Allocates the external OES texture used by ExoPlayer's SurfaceTexture.
     *  Returns the GL texture id; caller passes it to Kotlin. */
    static uint32_t createVideoTexture();
};

}  // namespace vrplayer
