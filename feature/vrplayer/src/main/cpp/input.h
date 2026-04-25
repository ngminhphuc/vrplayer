#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <openxr/openxr.h>

namespace vrplayer {

/**
 * OpenXR action set + Quest Touch suggested bindings. One-shot init at session
 * creation, then per-frame [sync] reads action state into the public fields.
 *
 * Bindings (from docs/dev/input-mapping.md):
 *   trigger / select        — play/pause
 *   thumbstick X (any hand) — seek ±10s when |x|>0.7 (rate-limited)
 *   thumbstick Y (any hand) — volume delta
 *   grip + drag             — move screen anchor
 *   menu (left)             — recenter (tap) / open overlay (hold)
 *   A / X                   — confirm
 */
class XrInput {
public:
    bool init(XrInstance instance, XrSession session);
    void shutdown();

    void attachToSession(XrSession session);

    /** Pumps action state for the frame. Must be called between
     *  xrWaitFrame and xrEndFrame. */
    void sync(XrSession session, XrSpace baseSpace, XrTime predictedTime);

    // Aim space pose (where the controller "points" at the screen).
    XrSpaceLocation leftAim{XR_TYPE_SPACE_LOCATION};
    XrSpaceLocation rightAim{XR_TYPE_SPACE_LOCATION};

    // Edge-triggered events; consumed by reading and clearing externally.
    bool triggerPressedEdge = false;  // play/pause — true if either fired
    bool triggerLeftEdge = false;     // which hand fired triggerPressedEdge
    bool triggerRightEdge = false;
    bool menuTapEdge = false;         // recenter
    bool gripLeftHeld = false;
    bool gripRightHeld = false;

    // Continuous values (-1..1).
    float thumbstickX = 0.f;
    float thumbstickY = 0.f;

private:
    XrActionSet mActionSet = XR_NULL_HANDLE;
    XrAction mTrigger = XR_NULL_HANDLE;
    XrAction mGrip = XR_NULL_HANDLE;
    XrAction mThumbstick = XR_NULL_HANDLE;
    XrAction mMenu = XR_NULL_HANDLE;
    XrAction mAimPose = XR_NULL_HANDLE;
    XrPath mLeftSubaction = XR_NULL_PATH;
    XrPath mRightSubaction = XR_NULL_PATH;
    XrSpace mLeftAimSpace = XR_NULL_HANDLE;
    XrSpace mRightAimSpace = XR_NULL_HANDLE;

    bool mPrevTriggerLeft = false;
    bool mPrevTriggerRight = false;
    bool mPrevMenu = false;
};

}  // namespace vrplayer
