// Stage 0 — Sprint 1 spike entry point.
//
// NativeActivity glue invokes android_main() on a dedicated thread. The flow:
//   1. Wait for the ANativeActivity to deliver an ANativeWindow.
//   2. Bring up EGL + GLES 3.2.
//   3. Init OpenXR session bound to the EGL context.
//   4. Bridge the C++ side to Kotlin XrActivity (acquire video Surface).
//   5. Loop: pump the Android event queue + xrPoll/render frames.

#include "gl_renderer.h"
#include "log.h"
#include "video_bridge.h"
#include "xr_session.h"

#include <android_native_app_glue.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <cassert>
#include <cstdlib>

namespace {

struct EglContext {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLConfig config = nullptr;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;  // 1×1 PBuffer; OpenXR owns the real target.
};

bool initEgl(EglContext& egl) {
    egl.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (egl.display == EGL_NO_DISPLAY) return false;
    if (!eglInitialize(egl.display, nullptr, nullptr)) return false;

    const EGLint cfgAttribs[] = {
        EGL_RED_SIZE, 8,    EGL_GREEN_SIZE,    8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,  EGL_DEPTH_SIZE,    0, EGL_STENCIL_SIZE, 0,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_NONE,
    };
    EGLint numCfg = 0;
    if (!eglChooseConfig(egl.display, cfgAttribs, &egl.config, 1, &numCfg) ||
        numCfg == 0) {
        VRP_LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint ctxAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    egl.context = eglCreateContext(egl.display, egl.config, EGL_NO_CONTEXT,
                                   ctxAttribs);
    if (egl.context == EGL_NO_CONTEXT) return false;

    const EGLint pbAttribs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    egl.surface =
        eglCreatePbufferSurface(egl.display, egl.config, pbAttribs);
    if (egl.surface == EGL_NO_SURFACE) return false;

    if (!eglMakeCurrent(egl.display, egl.surface, egl.surface, egl.context)) {
        VRP_LOGE("eglMakeCurrent failed");
        return false;
    }
    return true;
}

void teardownEgl(EglContext& egl) {
    if (egl.display != EGL_NO_DISPLAY) {
        eglMakeCurrent(egl.display, EGL_NO_SURFACE, EGL_NO_SURFACE,
                       EGL_NO_CONTEXT);
        if (egl.surface != EGL_NO_SURFACE)
            eglDestroySurface(egl.display, egl.surface);
        if (egl.context != EGL_NO_CONTEXT)
            eglDestroyContext(egl.display, egl.context);
        eglTerminate(egl.display);
    }
    egl.display = EGL_NO_DISPLAY;
}

void onAppCmd(android_app* app, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_DESTROY:
            VRP_LOGI("APP_CMD_DESTROY");
            break;
        case APP_CMD_PAUSE:
        case APP_CMD_STOP:
            // OpenXR handles its own lifecycle via session events.
            break;
        default:
            break;
    }
}

}  // namespace

extern "C" void android_main(android_app* app) {
    app->onAppCmd = onAppCmd;

    EglContext egl{};
    if (!initEgl(egl)) {
        VRP_LOGE("EGL init failed");
        std::abort();
    }

    if (!vrplayer::GlRenderer::init()) {
        VRP_LOGE("GlRenderer init failed");
        std::abort();
    }

    // Hook up the JNI bridge to the Kotlin XrActivity (NativeActivity exposes
    // the Java instance via app->activity->clazz).
    JNIEnv* env = nullptr;
    app->activity->vm->AttachCurrentThread(&env, nullptr);
    vrplayer::VideoBridge::attach(env, app->activity->clazz);

    vrplayer::VideoBridge::setTextureId(
        vrplayer::GlRenderer::createVideoTexture());
    vrplayer::VideoBridge::requestSurface();

    vrplayer::XrSession xr;
    if (!xr.init(app, egl.display, egl.context, egl.config)) {
        VRP_LOGE("XrSession init failed");
        std::abort();
    }

    while (!app->destroyRequested && !xr.exitRequested()) {
        // Drain the Android event queue without blocking.
        int events = 0;
        android_poll_source* source = nullptr;
        while (ALooper_pollOnce(0, nullptr, &events,
                                reinterpret_cast<void**>(&source)) >=
               ALOOPER_POLL_CALLBACK) {
            if (source) source->process(app, source);
            if (app->destroyRequested) break;
        }

        xr.renderFrame();
    }

    vrplayer::VideoBridge::detach(env);
    app->activity->vm->DetachCurrentThread();
    vrplayer::GlRenderer::shutdown();
    teardownEgl(egl);
}
