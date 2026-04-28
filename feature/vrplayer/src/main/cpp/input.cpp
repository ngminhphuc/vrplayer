#include "input.h"

#include "log.h"

#include <cmath>
#include <cstdlib>
#include <cstring>

namespace vrplayer {

namespace {

XrPath stringToPath(XrInstance instance, const char* s) {
    XrPath p = XR_NULL_PATH;
    xrStringToPath(instance, s, &p);
    return p;
}

}  // namespace

bool XrInput::init(XrInstance instance, XrSession /*session*/) {
    XrActionSetCreateInfo asCi{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::strncpy(asCi.actionSetName, "vrplayer", XR_MAX_ACTION_SET_NAME_SIZE - 1);
    std::strncpy(asCi.localizedActionSetName, "VR Player",
                 XR_MAX_LOCALIZED_ACTION_SET_NAME_SIZE - 1);
    asCi.priority = 0;
    if (XR_FAILED(xrCreateActionSet(instance, &asCi, &mActionSet))) {
        VRP_LOGE("xrCreateActionSet failed");
        return false;
    }

    mLeftSubaction = stringToPath(instance, "/user/hand/left");
    mRightSubaction = stringToPath(instance, "/user/hand/right");
    XrPath subactions[2] = {mLeftSubaction, mRightSubaction};

    auto makeAction = [&](const char* name, const char* localized,
                          XrActionType type, XrAction* out) {
        XrActionCreateInfo ci{XR_TYPE_ACTION_CREATE_INFO};
        std::strncpy(ci.actionName, name, XR_MAX_ACTION_NAME_SIZE - 1);
        std::strncpy(ci.localizedActionName, localized,
                     XR_MAX_LOCALIZED_ACTION_NAME_SIZE - 1);
        ci.actionType = type;
        ci.countSubactionPaths = 2;
        ci.subactionPaths = subactions;
        return XR_SUCCEEDED(xrCreateAction(mActionSet, &ci, out));
    };

    if (!makeAction("trigger", "Play / Pause", XR_ACTION_TYPE_BOOLEAN_INPUT,
                    &mTrigger)) return false;
    if (!makeAction("grip", "Grab Screen", XR_ACTION_TYPE_BOOLEAN_INPUT,
                    &mGrip)) return false;
    if (!makeAction("thumbstick", "Seek / Volume",
                    XR_ACTION_TYPE_VECTOR2F_INPUT, &mThumbstick)) return false;
    if (!makeAction("menu", "Menu / Recenter",
                    XR_ACTION_TYPE_BOOLEAN_INPUT, &mMenu)) return false;
    if (!makeAction("aim_pose", "Aim Pose", XR_ACTION_TYPE_POSE_INPUT,
                    &mAimPose)) return false;

    // Suggested binding: Oculus Touch.
    XrPath touch = stringToPath(instance,
                                "/interaction_profiles/oculus/touch_controller");
    XrActionSuggestedBinding b[10] = {
        {mTrigger,
         stringToPath(instance, "/user/hand/left/input/trigger/value")},
        {mTrigger,
         stringToPath(instance, "/user/hand/right/input/trigger/value")},
        {mGrip, stringToPath(instance, "/user/hand/left/input/squeeze/value")},
        {mGrip, stringToPath(instance, "/user/hand/right/input/squeeze/value")},
        {mThumbstick,
         stringToPath(instance, "/user/hand/left/input/thumbstick")},
        {mThumbstick,
         stringToPath(instance, "/user/hand/right/input/thumbstick")},
        {mMenu, stringToPath(instance, "/user/hand/left/input/menu/click")},
        {mMenu, stringToPath(instance, "/user/hand/right/input/system/click")},
        {mAimPose, stringToPath(instance, "/user/hand/left/input/aim/pose")},
        {mAimPose, stringToPath(instance, "/user/hand/right/input/aim/pose")},
    };
    XrInteractionProfileSuggestedBinding sug{
        XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    sug.interactionProfile = touch;
    sug.suggestedBindings = b;
    sug.countSuggestedBindings = 10;
    if (XR_FAILED(xrSuggestInteractionProfileBindings(instance, &sug))) {
        VRP_LOGE("xrSuggestInteractionProfileBindings failed");
        // Continue anyway — input is non-fatal in spike scope.
    }
    return true;
}

void XrInput::attachToSession(XrSession session) {
    XrSessionActionSetsAttachInfo att{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
    att.countActionSets = 1;
    att.actionSets = &mActionSet;
    if (XR_FAILED(xrAttachSessionActionSets(session, &att))) {
        VRP_LOGE("xrAttachSessionActionSets failed");
    }

    XrActionSpaceCreateInfo aci{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    aci.action = mAimPose;
    aci.poseInActionSpace.orientation.w = 1.f;

    aci.subactionPath = mLeftSubaction;
    xrCreateActionSpace(session, &aci, &mLeftAimSpace);
    aci.subactionPath = mRightSubaction;
    xrCreateActionSpace(session, &aci, &mRightAimSpace);
}

void XrInput::shutdown() {
    if (mLeftAimSpace) xrDestroySpace(mLeftAimSpace);
    if (mRightAimSpace) xrDestroySpace(mRightAimSpace);
    if (mActionSet) xrDestroyActionSet(mActionSet);
    mLeftAimSpace = mRightAimSpace = XR_NULL_HANDLE;
    mActionSet = XR_NULL_HANDLE;
}

void XrInput::sync(XrSession session, XrSpace baseSpace,
                   XrTime predictedTime) {
    XrActiveActionSet active{};
    active.actionSet = mActionSet;
    active.subactionPath = XR_NULL_PATH;

    XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
    sync.countActiveActionSets = 1;
    sync.activeActionSets = &active;
    if (XR_FAILED(xrSyncActions(session, &sync))) {
        return;
    }

    auto readBool = [&](XrAction action, XrPath sub) {
        XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
        gi.action = action;
        gi.subactionPath = sub;
        XrActionStateBoolean s{XR_TYPE_ACTION_STATE_BOOLEAN};
        xrGetActionStateBoolean(session, &gi, &s);
        return s.isActive == XR_TRUE && s.currentState == XR_TRUE;
    };
    auto readVec2 = [&](XrAction action, XrPath sub) {
        XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO};
        gi.action = action;
        gi.subactionPath = sub;
        XrActionStateVector2f s{XR_TYPE_ACTION_STATE_VECTOR2F};
        xrGetActionStateVector2f(session, &gi, &s);
        return s.isActive ? s.currentState : XrVector2f{0.f, 0.f};
    };

    const bool trigL = readBool(mTrigger, mLeftSubaction);
    const bool trigR = readBool(mTrigger, mRightSubaction);
    triggerLeftEdge = !mPrevTriggerLeft && trigL;
    triggerRightEdge = !mPrevTriggerRight && trigR;
    triggerPressedEdge = triggerLeftEdge || triggerRightEdge;
    mPrevTriggerLeft = trigL;
    mPrevTriggerRight = trigR;

    gripLeftHeld = readBool(mGrip, mLeftSubaction);
    gripRightHeld = readBool(mGrip, mRightSubaction);

    const bool menuNow = readBool(mMenu, mLeftSubaction) ||
                          readBool(mMenu, mRightSubaction);
    menuTapEdge = (!mPrevMenu && menuNow);
    mPrevMenu = menuNow;

    // Take whichever stick is more deflected.
    XrVector2f l = readVec2(mThumbstick, mLeftSubaction);
    XrVector2f r = readVec2(mThumbstick, mRightSubaction);
    if (std::abs(l.x) + std::abs(l.y) >= std::abs(r.x) + std::abs(r.y)) {
        thumbstickX = l.x;
        thumbstickY = l.y;
    } else {
        thumbstickX = r.x;
        thumbstickY = r.y;
    }

    if (mLeftAimSpace) {
        leftAim.type = XR_TYPE_SPACE_LOCATION;
        xrLocateSpace(mLeftAimSpace, baseSpace, predictedTime, &leftAim);
    }
    if (mRightAimSpace) {
        rightAim.type = XR_TYPE_SPACE_LOCATION;
        xrLocateSpace(mRightAimSpace, baseSpace, predictedTime, &rightAim);
    }
}

}  // namespace vrplayer
