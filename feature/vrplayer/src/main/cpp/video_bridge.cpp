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
uint32_t VideoBridge::sTextureId = 0;

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
    env->DeleteLocalRef(cls);

    if (!sAcquireSurface || !sUpdateTexImage || !sGetTransformMatrix) {
        VRP_LOGE("VideoBridge::attach failed to resolve core methods");
    }
}

void VideoBridge::detach(JNIEnv* env) {
    if (sActivityRef) env->DeleteGlobalRef(sActivityRef);
    sActivityRef = nullptr;
    sAcquireSurface = sUpdateTexImage = sGetTransformMatrix = nullptr;
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

}  // namespace vrplayer
