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

    // Player command bridge (ExoPlayer lives in Kotlin land).
    static void togglePlayPause();
    static void seekDelta(int deltaMs);
    static void volumeDelta(float delta);
    static void persistScreenTransform(float radius, float arc, float height,
                                       float yaw, float yOffset, float zOffset);

private:
    static jobject sActivityRef;
    static jmethodID sAcquireSurface;
    static jmethodID sUpdateTexImage;
    static jmethodID sGetTransformMatrix;
    static jmethodID sTogglePlayPause;
    static jmethodID sSeekDelta;
    static jmethodID sVolumeDelta;
    static jmethodID sPersistTransform;
    static uint32_t sTextureId;
};

}  // namespace vrplayer
