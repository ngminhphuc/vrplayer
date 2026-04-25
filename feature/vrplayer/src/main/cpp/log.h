#pragma once

#include <android/log.h>

#define VRP_TAG "VrPlayer/native"

#define VRP_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  VRP_TAG, __VA_ARGS__)
#define VRP_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  VRP_TAG, __VA_ARGS__)
#define VRP_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VRP_TAG, __VA_ARGS__)

#define VRP_CHECK_XR(call)                                                    \
    do {                                                                       \
        XrResult _r = (call);                                                  \
        if (XR_FAILED(_r)) {                                                   \
            VRP_LOGE("OpenXR call failed: %s -> %d", #call, _r);               \
            return false;                                                      \
        }                                                                      \
    } while (0)
