#pragma once

#include <openxr/openxr.h>

namespace vrplayer {

/**
 * Best-effort wrapper over `XR_EXT_hand_tracking`. When the extension
 * isn't supported (older Quest or hand tracking disabled in settings),
 * all queries silently report "not tracked" and the rest of the app
 * continues to use controllers normally.
 *
 * Pinch detection uses the index-tip ↔ thumb-tip distance threshold
 * recommended by Meta (~25 mm). The latched edge state lives here and
 * is composited into [Input] each frame.
 */
class HandTracking {
public:
    bool init(XrInstance instance, XrSession session);
    void shutdown();

    /** Update tracker; latch new pinch edges. */
    void update(XrSpace baseSpace, XrTime predictedTime);

    /** Released-this-frame edges (consume style — read once per frame). */
    bool consumeLeftPinchEdge();
    bool consumeRightPinchEdge();

    /** Aim pose (index tip) for hit-testing if a hand is tracked. */
    bool leftAimPose(XrPosef* outPose) const;
    bool rightAimPose(XrPosef* outPose) const;

    bool available() const { return mEnabled; }

private:
    bool mEnabled = false;
    XrInstance mInstance = XR_NULL_HANDLE;
    XrHandTrackerEXT mLeft = XR_NULL_HANDLE;
    XrHandTrackerEXT mRight = XR_NULL_HANDLE;

    bool mLeftPinch = false;
    bool mRightPinch = false;
    bool mLeftPinchEdge = false;
    bool mRightPinchEdge = false;

    XrPosef mLeftAim{};
    XrPosef mRightAim{};
    bool mLeftValid = false;
    bool mRightValid = false;

    PFN_xrCreateHandTrackerEXT mCreate = nullptr;
    PFN_xrDestroyHandTrackerEXT mDestroy = nullptr;
    PFN_xrLocateHandJointsEXT mLocate = nullptr;
};

}  // namespace vrplayer
