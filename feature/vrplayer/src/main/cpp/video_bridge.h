#pragma once

#include <jni.h>
#include <cstdint>

namespace vrplayer {

/**
 * JNI bridge to the Kotlin XrActivity. The native render thread creates an
 * external OES texture via [GlRenderer::createVideoTexture] and asks the
 * Kotlin side to wrap it in a SurfaceTexture+Surface (which is then handed
 * to ExoPlayer). On every frame we call [updateTexImage] to pull the latest
 * decoded video frame onto the GPU.
 */
class VideoBridge {
public:
    static void attach(JNIEnv* env, jobject xrActivity);
    static void detach(JNIEnv* env);

    static uint32_t textureId() { return sTextureId; }
    static void setTextureId(uint32_t id) { sTextureId = id; }

    static void requestSurface();
    static void updateTexImage();
    static void getTransformMatrix(float out[16]);

private:
    static jobject sActivityRef;          // global ref to XrActivity
    static jmethodID sAcquireSurface;     // (I)Landroid/view/Surface;
    static jmethodID sUpdateTexImage;     // ()V
    static jmethodID sGetTransformMatrix; // ([F)V
    static uint32_t sTextureId;
};

}  // namespace vrplayer
