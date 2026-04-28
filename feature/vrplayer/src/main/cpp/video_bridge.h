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

    /** Picker surface (Compose-rendered) bridge. */
    static void requestPickerSurface();
    static void updatePickerTexImage();
    static void getPickerTransformMatrix(float out[16]);
    static void injectPickerTap(float u, float v);
    static int pickerWidth();
    static int pickerHeight();
    static uint32_t pickerTextureId() { return sPickerTexId; }
    static void setPickerTextureId(uint32_t id) { sPickerTexId = id; }

    /** Subtitle surface (Compose-rendered) bridge — same shape as
     *  picker, but with no hit testing because subtitles are
     *  output-only. The Kotlin side drives visibility based on
     *  whether the current cue group is non-empty. */
    static void requestSubtitleSurface();
    static void updateSubtitleTexImage();
    static void getSubtitleTransformMatrix(float out[16]);
    static int subtitleWidth();
    static int subtitleHeight();
    static uint32_t subtitleTextureId() { return sSubtitleTexId; }
    static void setSubtitleTextureId(uint32_t id) { sSubtitleTexId = id; }

private:
    static jobject sActivityRef;
    static jmethodID sAcquireSurface;
    static jmethodID sUpdateTexImage;
    static jmethodID sGetTransformMatrix;
    static jmethodID sTogglePlayPause;
    static jmethodID sSeekDelta;
    static jmethodID sVolumeDelta;
    static jmethodID sPersistTransform;
    static jmethodID sAcquirePicker;
    static jmethodID sUpdatePicker;
    static jmethodID sGetPickerTexMat;
    static jmethodID sInjectPickerTap;
    static jmethodID sPickerWidth;
    static jmethodID sPickerHeight;
    static jmethodID sAcquireSubtitle;
    static jmethodID sUpdateSubtitle;
    static jmethodID sGetSubtitleTexMat;
    static jmethodID sSubtitleWidth;
    static jmethodID sSubtitleHeight;
    static uint32_t sTextureId;
    static uint32_t sPickerTexId;
    static uint32_t sSubtitleTexId;
};

}  // namespace vrplayer
