#include "video_bridge.h"

#include "gl_renderer.h"
#include "log.h"

#include <android_native_app_glue.h>
#include <cstring>

namespace vrplayer {

jobject VideoBridge::sActivityRef = nullptr;
jmethodID VideoBridge::sAcquireSurface = nullptr;
jmethodID VideoBridge::sUpdateTexImage = nullptr;
jmethodID VideoBridge::sGetTransformMatrix = nullptr;
jmethodID VideoBridge::sTogglePlayPause = nullptr;
jmethodID VideoBridge::sSeekDelta = nullptr;
jmethodID VideoBridge::sVolumeDelta = nullptr;
jmethodID VideoBridge::sPersistTransform = nullptr;
jmethodID VideoBridge::sAcquirePicker = nullptr;
jmethodID VideoBridge::sUpdatePicker = nullptr;
jmethodID VideoBridge::sGetPickerTexMat = nullptr;
jmethodID VideoBridge::sInjectPickerTap = nullptr;
jmethodID VideoBridge::sPickerWidth = nullptr;
jmethodID VideoBridge::sPickerHeight = nullptr;
uint32_t VideoBridge::sTextureId = 0;
uint32_t VideoBridge::sPickerTexId = 0;

namespace {
JavaVM* sVm = nullptr;

JNIEnv* attachEnv(bool* needDetach) {
    JNIEnv* env = nullptr;
    if (sVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) ==
        JNI_OK) {
        *needDetach = false;
        return env;
    }
    if (sVm->AttachCurrentThread(&env, nullptr) == 0) {
        *needDetach = true;
        return env;
    }
    return nullptr;
}

void detachEnv(bool needDetach) {
    if (needDetach && sVm) sVm->DetachCurrentThread();
}
}  // namespace

void VideoBridge::attach(JNIEnv* env, jobject xrActivity) {
    env->GetJavaVM(&sVm);
    sActivityRef = env->NewGlobalRef(xrActivity);

    jclass cls = env->GetObjectClass(xrActivity);
    sAcquireSurface =
        env->GetMethodID(cls, "acquireVideoSurface", "(I)Landroid/view/Surface;");
    sUpdateTexImage = env->GetMethodID(cls, "updateTexImage", "()V");
    sGetTransformMatrix = env->GetMethodID(cls, "getTransformMatrix", "([F)V");
    sTogglePlayPause = env->GetMethodID(cls, "togglePlayPause", "()V");
    sSeekDelta = env->GetMethodID(cls, "seekDelta", "(I)V");
    sVolumeDelta = env->GetMethodID(cls, "volumeDelta", "(F)V");
    sPersistTransform =
        env->GetMethodID(cls, "persistScreenTransform", "(FFFFFF)V");
    sAcquirePicker =
        env->GetMethodID(cls, "acquirePickerSurface",
                         "(I)Landroid/view/Surface;");
    sUpdatePicker = env->GetMethodID(cls, "updatePickerTexImage", "()Z");
    sGetPickerTexMat =
        env->GetMethodID(cls, "getPickerTransformMatrix", "([F)V");
    sInjectPickerTap = env->GetMethodID(cls, "injectPickerTap", "(FF)V");
    sPickerWidth = env->GetMethodID(cls, "pickerWidth", "()I");
    sPickerHeight = env->GetMethodID(cls, "pickerHeight", "()I");
    env->DeleteLocalRef(cls);

    if (!sAcquireSurface || !sUpdateTexImage || !sGetTransformMatrix) {
        VRP_LOGE("VideoBridge::attach failed to resolve core methods");
    }
}

void VideoBridge::detach(JNIEnv* env) {
    if (sActivityRef) env->DeleteGlobalRef(sActivityRef);
    sActivityRef = nullptr;
    sAcquireSurface = sUpdateTexImage = sGetTransformMatrix = nullptr;
    sTogglePlayPause = sSeekDelta = sVolumeDelta = sPersistTransform = nullptr;
    sAcquirePicker = sUpdatePicker = sGetPickerTexMat = nullptr;
    sInjectPickerTap = sPickerWidth = sPickerHeight = nullptr;
    // GL textures live on the render thread's context which is torn down
    // alongside the activity, so reset the cached id to force a re-allocate
    // on the next attach (avoids handing Kotlin a stale handle on relaunch).
    sTextureId = 0;
    sPickerTexId = 0;
}

void VideoBridge::requestSurface() {
    if (!sActivityRef || !sAcquireSurface) return;
    if (sTextureId == 0) sTextureId = GlRenderer::createVideoTexture();

    bool needDetach = false;
    JNIEnv* env = attachEnv(&needDetach);
    if (!env) return;
    jobject surface = env->CallObjectMethod(sActivityRef, sAcquireSurface,
                                            static_cast<jint>(sTextureId));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (surface) env->DeleteLocalRef(surface);
    detachEnv(needDetach);
}

void VideoBridge::updateTexImage() {
    if (!sActivityRef || !sUpdateTexImage) return;
    bool needDetach = false;
    JNIEnv* env = attachEnv(&needDetach);
    if (!env) return;
    env->CallVoidMethod(sActivityRef, sUpdateTexImage);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    detachEnv(needDetach);
}

void VideoBridge::getTransformMatrix(float out[16]) {
    // Identity fallback if we can't reach the JVM (e.g. during shutdown).
    static const float kIdentity[16] = {
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
    };
    std::memcpy(out, kIdentity, sizeof(kIdentity));
    if (!sActivityRef || !sGetTransformMatrix) return;

    bool needDetach = false;
    JNIEnv* env = attachEnv(&needDetach);
    if (!env) return;
    jfloatArray arr = env->NewFloatArray(16);
    env->CallVoidMethod(sActivityRef, sGetTransformMatrix, arr);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    } else {
        env->GetFloatArrayRegion(arr, 0, 16, out);
    }
    env->DeleteLocalRef(arr);
    detachEnv(needDetach);
}

namespace {

template <typename Call>
void callVoid(jmethodID method, Call invoke) {
    if (!VideoBridge::textureId() && false) return;  // suppress unused warn
    if (!method) return;
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return;
    invoke(env, method);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    detachEnv(need);
}

}  // namespace

void VideoBridge::togglePlayPause() {
    if (!sActivityRef || !sTogglePlayPause) return;
    callVoid(sTogglePlayPause, [](JNIEnv* env, jmethodID m) {
        env->CallVoidMethod(sActivityRef, m);
    });
}

void VideoBridge::seekDelta(int deltaMs) {
    if (!sActivityRef || !sSeekDelta) return;
    callVoid(sSeekDelta, [deltaMs](JNIEnv* env, jmethodID m) {
        env->CallVoidMethod(sActivityRef, m, static_cast<jint>(deltaMs));
    });
}

void VideoBridge::volumeDelta(float delta) {
    if (!sActivityRef || !sVolumeDelta) return;
    callVoid(sVolumeDelta, [delta](JNIEnv* env, jmethodID m) {
        env->CallVoidMethod(sActivityRef, m, static_cast<jfloat>(delta));
    });
}

void VideoBridge::persistScreenTransform(float radius, float arc, float height,
                                          float yaw, float yOffset,
                                          float zOffset) {
    if (!sActivityRef || !sPersistTransform) return;
    callVoid(sPersistTransform,
             [=](JNIEnv* env, jmethodID m) {
                 env->CallVoidMethod(sActivityRef, m, radius, arc, height,
                                     yaw, yOffset, zOffset);
             });
}

void VideoBridge::requestPickerSurface() {
    if (!sActivityRef || !sAcquirePicker) return;
    if (sPickerTexId == 0) sPickerTexId = GlRenderer::createVideoTexture();
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return;
    jobject s = env->CallObjectMethod(sActivityRef, sAcquirePicker,
                                       static_cast<jint>(sPickerTexId));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (s) env->DeleteLocalRef(s);
    detachEnv(need);
}

void VideoBridge::updatePickerTexImage() {
    if (!sActivityRef || !sUpdatePicker) return;
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return;
    env->CallBooleanMethod(sActivityRef, sUpdatePicker);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    detachEnv(need);
}

void VideoBridge::getPickerTransformMatrix(float out[16]) {
    // Identity fallback — fill before any early-return path.
    for (int i = 0; i < 16; ++i) out[i] = (i % 5 == 0) ? 1.f : 0.f;
    if (!sActivityRef || !sGetPickerTexMat) return;
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return;
    jfloatArray arr = env->NewFloatArray(16);
    env->CallVoidMethod(sActivityRef, sGetPickerTexMat, arr);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        for (int i = 0; i < 16; ++i) out[i] = (i % 5 == 0) ? 1.f : 0.f;
    } else {
        env->GetFloatArrayRegion(arr, 0, 16, out);
    }
    env->DeleteLocalRef(arr);
    detachEnv(need);
}

void VideoBridge::injectPickerTap(float u, float v) {
    if (!sActivityRef || !sInjectPickerTap) return;
    callVoid(sInjectPickerTap, [u, v](JNIEnv* env, jmethodID m) {
        env->CallVoidMethod(sActivityRef, m, u, v);
    });
}

int VideoBridge::pickerWidth() {
    if (!sActivityRef || !sPickerWidth) return 1024;
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return 1024;
    jint w = env->CallIntMethod(sActivityRef, sPickerWidth);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        w = 1024;
    }
    detachEnv(need);
    return static_cast<int>(w);
}

int VideoBridge::pickerHeight() {
    if (!sActivityRef || !sPickerHeight) return 720;
    bool need = false;
    JNIEnv* env = attachEnv(&need);
    if (!env) return 720;
    jint h = env->CallIntMethod(sActivityRef, sPickerHeight);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        h = 720;
    }
    detachEnv(need);
    return static_cast<int>(h);
}

}  // namespace vrplayer
