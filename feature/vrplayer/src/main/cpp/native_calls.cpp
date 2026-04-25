#include "log.h"
#include "sphere.h"
#include "stereo.h"

#include <jni.h>

namespace {

vrplayer::Sphere::Mode toMode(jint v) {
    switch (v) {
        case 1: return vrplayer::Sphere::Mode::Equirect360;
        case 2: return vrplayer::Sphere::Mode::Hemisphere180;
        default: return vrplayer::Sphere::Mode::Off;
    }
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_dev_anilbeesetti_nextplayer_feature_vrplayer_XrActivity_nativeSetProjection(
    JNIEnv*, jobject, jint mode) {
    vrplayer::Sphere::setMode(toMode(mode));
    VRP_LOGI("Projection mode -> %d", mode);
}

JNIEXPORT void JNICALL
Java_dev_anilbeesetti_nextplayer_feature_vrplayer_XrActivity_nativeSnapFront(
    JNIEnv*, jobject) {
    vrplayer::Sphere::setYawOffsetDeg(0.f);
}

JNIEXPORT void JNICALL
Java_dev_anilbeesetti_nextplayer_feature_vrplayer_XrActivity_nativeRotateYaw(
    JNIEnv*, jobject, jfloat deg) {
    vrplayer::Sphere::setYawOffsetDeg(deg);
}

JNIEXPORT void JNICALL
Java_dev_anilbeesetti_nextplayer_feature_vrplayer_XrActivity_nativeSetStereo(
    JNIEnv*, jobject, jint mode) {
    using M = vrplayer::Stereo::Mode;
    M m = M::Mono;
    switch (mode) {
        case 1: m = M::SbsLeftRight; break;
        case 2: m = M::SbsRightLeft; break;
        case 3: m = M::TbLeftRight; break;
        case 4: m = M::TbRightLeft; break;
        default: m = M::Mono; break;
    }
    vrplayer::Stereo::setMode(m);
    VRP_LOGI("Stereo mode -> %d", mode);
}

}  // extern "C"
