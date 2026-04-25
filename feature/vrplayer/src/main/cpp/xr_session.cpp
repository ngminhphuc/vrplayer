#include "xr_session.h"

#include "gl_renderer.h"
#include "log.h"

#include <EGL/egl.h>
#include <cstring>

namespace vrplayer {

namespace {

constexpr XrViewConfigurationType kViewType =
    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

}  // namespace

XrSession::~XrSession() {
    for (auto& sc : mSwapchains) {
        if (sc.handle != XR_NULL_HANDLE) {
            xrDestroySwapchain(sc.handle);
        }
    }
    if (mAppSpace != XR_NULL_HANDLE) xrDestroySpace(mAppSpace);
    if (mSession != XR_NULL_HANDLE) xrDestroySession(mSession);
    if (mInstance != XR_NULL_HANDLE) xrDestroyInstance(mInstance);
}

bool XrSession::init(android_app* androidApp, EGLDisplay display,
                     EGLContext context, EGLConfig config) {
    if (!createInstance(androidApp)) return false;
    if (!createSystem()) return false;
    if (!createSession(display, context, config)) return false;
    if (!createReferenceSpace()) return false;
    if (!createSwapchains()) return false;
    VRP_LOGI("XrSession initialised; %zu views", mViewConfigViews.size());
    return true;
}

bool XrSession::createInstance(android_app* androidApp) {
    PFN_xrInitializeLoaderKHR xrInitializeLoaderKHR = nullptr;
    if (xrGetInstanceProcAddr(XR_NULL_HANDLE, "xrInitializeLoaderKHR",
                              reinterpret_cast<PFN_xrVoidFunction*>(
                                  &xrInitializeLoaderKHR)) == XR_SUCCESS &&
        xrInitializeLoaderKHR != nullptr) {
        XrLoaderInitInfoAndroidKHR info{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
        info.applicationVM = androidApp->activity->vm;
        info.applicationContext = androidApp->activity->clazz;
        XrResult r = xrInitializeLoaderKHR(
            reinterpret_cast<const XrLoaderInitInfoBaseHeaderKHR*>(&info));
        if (XR_FAILED(r)) {
            VRP_LOGE("xrInitializeLoaderKHR failed: %d", r);
            return false;
        }
    }

    const char* extensions[] = {
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
    };

    XrInstanceCreateInfoAndroidKHR androidCreate{
        XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    androidCreate.applicationVM = androidApp->activity->vm;
    androidCreate.applicationActivity = androidApp->activity->clazz;

    XrInstanceCreateInfo create{XR_TYPE_INSTANCE_CREATE_INFO};
    create.next = &androidCreate;
    create.enabledExtensionCount =
        sizeof(extensions) / sizeof(extensions[0]);
    create.enabledExtensionNames = extensions;
    std::strncpy(create.applicationInfo.applicationName, "VrPlayer",
                 XR_MAX_APPLICATION_NAME_SIZE - 1);
    create.applicationInfo.applicationVersion = 1;
    std::strncpy(create.applicationInfo.engineName, "VrPlayerNative",
                 XR_MAX_ENGINE_NAME_SIZE - 1);
    create.applicationInfo.engineVersion = 1;
    create.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;

    VRP_CHECK_XR(xrCreateInstance(&create, &mInstance));
    return true;
}

bool XrSession::createSystem() {
    XrSystemGetInfo info{XR_TYPE_SYSTEM_GET_INFO};
    info.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    VRP_CHECK_XR(xrGetSystem(mInstance, &info, &mSystemId));

    uint32_t viewCount = 0;
    VRP_CHECK_XR(xrEnumerateViewConfigurationViews(
        mInstance, mSystemId, kViewType, 0, &viewCount, nullptr));
    mViewConfigViews.resize(viewCount,
                            {XR_TYPE_VIEW_CONFIGURATION_VIEW});
    VRP_CHECK_XR(xrEnumerateViewConfigurationViews(
        mInstance, mSystemId, kViewType, viewCount, &viewCount,
        mViewConfigViews.data()));
    mViews.resize(viewCount, {XR_TYPE_VIEW});
    return true;
}

bool XrSession::createSession(EGLDisplay display, EGLContext context,
                              EGLConfig config) {
    XrGraphicsRequirementsOpenGLESKHR req{
        XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
    PFN_xrGetOpenGLESGraphicsRequirementsKHR getReq = nullptr;
    VRP_CHECK_XR(xrGetInstanceProcAddr(
        mInstance, "xrGetOpenGLESGraphicsRequirementsKHR",
        reinterpret_cast<PFN_xrVoidFunction*>(&getReq)));
    VRP_CHECK_XR(getReq(mInstance, mSystemId, &req));

    XrGraphicsBindingOpenGLESAndroidKHR binding{
        XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
    binding.display = display;
    binding.config = config;
    binding.context = context;

    XrSessionCreateInfo info{XR_TYPE_SESSION_CREATE_INFO};
    info.next = &binding;
    info.systemId = mSystemId;
    VRP_CHECK_XR(xrCreateSession(mInstance, &info, &mSession));
    return true;
}

bool XrSession::createReferenceSpace() {
    XrReferenceSpaceCreateInfo info{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
    info.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    info.poseInReferenceSpace.orientation = {0.f, 0.f, 0.f, 1.f};
    info.poseInReferenceSpace.position = {0.f, 0.f, 0.f};
    VRP_CHECK_XR(xrCreateReferenceSpace(mSession, &info, &mAppSpace));
    return true;
}

bool XrSession::createSwapchains() {
    mSwapchains.reserve(mViewConfigViews.size());
    for (const auto& vcv : mViewConfigViews) {
        XrSwapchainCreateInfo ci{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        ci.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT |
                        XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
        ci.format = 0x8058;  // GL_RGBA8
        ci.sampleCount = 1;
        ci.width = vcv.recommendedImageRectWidth;
        ci.height = vcv.recommendedImageRectHeight;
        ci.faceCount = 1;
        ci.arraySize = 1;
        ci.mipCount = 1;

        SwapchainImage sc{};
        sc.width = static_cast<int32_t>(ci.width);
        sc.height = static_cast<int32_t>(ci.height);
        VRP_CHECK_XR(xrCreateSwapchain(mSession, &ci, &sc.handle));

        uint32_t imgCount = 0;
        VRP_CHECK_XR(xrEnumerateSwapchainImages(sc.handle, 0, &imgCount,
                                                nullptr));
        sc.images.resize(imgCount,
                         {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
        VRP_CHECK_XR(xrEnumerateSwapchainImages(
            sc.handle, imgCount, &imgCount,
            reinterpret_cast<XrSwapchainImageBaseHeader*>(sc.images.data())));
        mSwapchains.push_back(std::move(sc));
    }
    return true;
}

void XrSession::handleSessionStateChange(XrSessionState newState) {
    mSessionState = newState;
    switch (newState) {
        case XR_SESSION_STATE_READY: {
            XrSessionBeginInfo begin{XR_TYPE_SESSION_BEGIN_INFO};
            begin.primaryViewConfigurationType = kViewType;
            xrBeginSession(mSession, &begin);
            mSessionRunning = true;
            break;
        }
        case XR_SESSION_STATE_STOPPING:
            mSessionRunning = false;
            xrEndSession(mSession);
            break;
        case XR_SESSION_STATE_EXITING:
        case XR_SESSION_STATE_LOSS_PENDING:
            mExitRequested = true;
            break;
        default:
            break;
    }
}

void XrSession::renderFrame() {
    // Pump events.
    XrEventDataBuffer evt{XR_TYPE_EVENT_DATA_BUFFER};
    while (xrPollEvent(mInstance, &evt) == XR_SUCCESS) {
        if (evt.type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
            const auto& s =
                *reinterpret_cast<XrEventDataSessionStateChanged*>(&evt);
            handleSessionStateChange(s.state);
        }
        evt.type = XR_TYPE_EVENT_DATA_BUFFER;  // reset for next call
    }

    if (!mSessionRunning) return;

    XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
    XrFrameState frameState{XR_TYPE_FRAME_STATE};
    if (XR_FAILED(xrWaitFrame(mSession, &waitInfo, &frameState))) return;

    XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
    xrBeginFrame(mSession, &beginInfo);

    std::vector<XrCompositionLayerProjectionView> projViews;
    XrCompositionLayerProjection projLayer{
        XR_TYPE_COMPOSITION_LAYER_PROJECTION};

    if (frameState.shouldRender == XR_TRUE) {
        // Locate views.
        XrViewLocateInfo locate{XR_TYPE_VIEW_LOCATE_INFO};
        locate.viewConfigurationType = kViewType;
        locate.displayTime = frameState.predictedDisplayTime;
        locate.space = mAppSpace;
        XrViewState viewState{XR_TYPE_VIEW_STATE};
        uint32_t viewCount = 0;
        xrLocateViews(mSession, &locate, &viewState,
                      static_cast<uint32_t>(mViews.size()), &viewCount,
                      mViews.data());

        projViews.resize(viewCount);
        for (uint32_t i = 0; i < viewCount; ++i) {
            auto& sc = mSwapchains[i];
            XrSwapchainImageAcquireInfo aInfo{
                XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            uint32_t imgIdx = 0;
            xrAcquireSwapchainImage(sc.handle, &aInfo, &imgIdx);

            XrSwapchainImageWaitInfo wInfo{
                XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            wInfo.timeout = XR_INFINITE_DURATION;
            xrWaitSwapchainImage(sc.handle, &wInfo);

            // Render the cinema quad into this eye's image.
            GlRenderer::renderEye(sc.images[imgIdx].image, sc.width, sc.height,
                                  mViews[i]);

            XrSwapchainImageReleaseInfo rInfo{
                XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            xrReleaseSwapchainImage(sc.handle, &rInfo);

            projViews[i] = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
            projViews[i].pose = mViews[i].pose;
            projViews[i].fov = mViews[i].fov;
            projViews[i].subImage.swapchain = sc.handle;
            projViews[i].subImage.imageRect.offset = {0, 0};
            projViews[i].subImage.imageRect.extent = {sc.width, sc.height};
        }

        projLayer.space = mAppSpace;
        projLayer.viewCount = static_cast<uint32_t>(projViews.size());
        projLayer.views = projViews.data();
    }

    XrCompositionLayerBaseHeader* layers[1];
    uint32_t layerCount = 0;
    if (frameState.shouldRender == XR_TRUE && !projViews.empty()) {
        layers[0] = reinterpret_cast<XrCompositionLayerBaseHeader*>(&projLayer);
        layerCount = 1;
    }

    XrFrameEndInfo endInfo{XR_TYPE_FRAME_END_INFO};
    endInfo.displayTime = frameState.predictedDisplayTime;
    endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    endInfo.layerCount = layerCount;
    endInfo.layers = layers;
    xrEndFrame(mSession, &endInfo);
}

}  // namespace vrplayer
