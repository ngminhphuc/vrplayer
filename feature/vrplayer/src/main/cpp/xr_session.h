#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android_native_app_glue.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <vector>

#include "input.h"

namespace vrplayer {

struct SwapchainImage {
    XrSwapchain handle = XR_NULL_HANDLE;
    int32_t width = 0;
    int32_t height = 0;
    std::vector<XrSwapchainImageOpenGLESKHR> images;
};

/**
 * Owns the OpenXR instance, system, session, swapchains, reference space, and
 * the render loop driving them. Stage 0 spike: 1 quad layer per frame, no
 * input, no compositor optimisation.
 */
class XrSession {
public:
    XrSession() = default;
    ~XrSession();

    bool init(android_app* androidApp, EGLDisplay display, EGLContext context,
              EGLConfig config);

    /** Pumps the OpenXR event queue and renders one frame if applicable. */
    void renderFrame();

    bool sessionRunning() const { return mSessionRunning; }
    bool exitRequested() const { return mExitRequested; }

    /** Hot-reapply DataStore-persisted screen transform from Kotlin (called on
     *  init when the saved values come back). */
    void applyScreenTransform(float radius, float arc, float height,
                               float yaw, float yOffset, float zOffset);

private:
    bool createInstance(android_app* androidApp);
    bool createSystem();
    bool createSession(EGLDisplay display, EGLContext context, EGLConfig config);
    bool createSwapchains();
    bool createReferenceSpace();
    void recenter();
    void processInput(XrTime predictedTime);

    void handleSessionStateChange(XrSessionState newState);

    XrInstance mInstance = XR_NULL_HANDLE;
    XrSystemId mSystemId = XR_NULL_SYSTEM_ID;
    XrSession_T* mSession = XR_NULL_HANDLE;
    XrSpace mAppSpace = XR_NULL_HANDLE;
    XrSessionState mSessionState = XR_SESSION_STATE_UNKNOWN;

    std::vector<SwapchainImage> mSwapchains;  // one per view (left/right eye)
    std::vector<XrViewConfigurationView> mViewConfigViews;
    std::vector<XrView> mViews;

    XrInput mInput;
    bool mInputAttached = false;

    // Screen transform state (mutated by grip-drag, persisted via JNI).
    struct ScreenState {
        float radius = 3.f;
        float arc = 2.0944f;
        float height = 1.6f;
        float yaw = 0.f;
        float yOffset = 1.5f;
        float zOffset = 0.f;
    } mScreen;
    bool mWasGrip = false;
    XrPosef mGripStartHand{};
    ScreenState mGripStartScreen{};

    // Seek throttle.
    int64_t mLastSeekNanos = 0;

    bool mSessionRunning = false;
    bool mExitRequested = false;
};

}  // namespace vrplayer
