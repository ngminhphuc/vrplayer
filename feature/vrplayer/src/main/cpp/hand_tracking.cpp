#include "hand_tracking.h"

#include "log.h"

#include <cmath>
#include <cstring>

namespace vrplayer {

namespace {

constexpr float kPinchThresholdMeters = 0.025f;       // ~25 mm
constexpr float kPinchReleaseHysteresis = 0.035f;     // 35 mm to release

bool createTracker(PFN_xrCreateHandTrackerEXT create, XrSession session,
                   XrHandEXT hand, XrHandTrackerEXT* out) {
    XrHandTrackerCreateInfoEXT info{XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT};
    info.hand = hand;
    info.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;
    XrResult r = create(session, &info, out);
    if (XR_FAILED(r)) {
        VRP_LOGW("xrCreateHandTrackerEXT(%d) failed: %d", hand, r);
        *out = XR_NULL_HANDLE;
        return false;
    }
    return true;
}

float dist3(const XrVector3f& a, const XrVector3f& b) {
    float dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
    return std::sqrt(dx * dx + dy * dy + dz * dz);
}

}  // namespace

bool HandTracking::init(XrInstance instance, XrSession session) {
    mInstance = instance;
    if (XR_FAILED(xrGetInstanceProcAddr(
            instance, "xrCreateHandTrackerEXT",
            reinterpret_cast<PFN_xrVoidFunction*>(&mCreate))) ||
        XR_FAILED(xrGetInstanceProcAddr(
            instance, "xrDestroyHandTrackerEXT",
            reinterpret_cast<PFN_xrVoidFunction*>(&mDestroy))) ||
        XR_FAILED(xrGetInstanceProcAddr(
            instance, "xrLocateHandJointsEXT",
            reinterpret_cast<PFN_xrVoidFunction*>(&mLocate)))) {
        VRP_LOGI("Hand tracking entry points unavailable");
        return false;
    }
    bool okL = createTracker(mCreate, session, XR_HAND_LEFT_EXT, &mLeft);
    bool okR = createTracker(mCreate, session, XR_HAND_RIGHT_EXT, &mRight);
    mEnabled = okL || okR;
    if (mEnabled) VRP_LOGI("Hand tracking ready (L=%d R=%d)", okL, okR);
    return mEnabled;
}

void HandTracking::shutdown() {
    if (mDestroy) {
        if (mLeft) mDestroy(mLeft);
        if (mRight) mDestroy(mRight);
    }
    mLeft = mRight = XR_NULL_HANDLE;
    mEnabled = false;
}

void HandTracking::update(XrSpace baseSpace, XrTime time) {
    if (!mEnabled || !mLocate) return;

    XrHandJointLocationEXT lLocs[XR_HAND_JOINT_COUNT_EXT]{};
    XrHandJointLocationEXT rLocs[XR_HAND_JOINT_COUNT_EXT]{};

    auto locate = [&](XrHandTrackerEXT t,
                      XrHandJointLocationEXT* locs,
                      bool* outPinch, bool* outEdge,
                      XrPosef* outAim, bool* outValid,
                      bool* prevState) {
        if (!t) {
            *outValid = false;
            return;
        }
        XrHandJointLocationsEXT result{XR_TYPE_HAND_JOINT_LOCATIONS_EXT};
        result.jointCount = XR_HAND_JOINT_COUNT_EXT;
        result.jointLocations = locs;

        XrHandJointsLocateInfoEXT info{XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT};
        info.baseSpace = baseSpace;
        info.time = time;
        XrResult r = mLocate(t, &info, &result);
        if (XR_FAILED(r) || !result.isActive) {
            *outValid = false;
            return;
        }
        const auto& tip = locs[XR_HAND_JOINT_INDEX_TIP_EXT];
        const auto& thumb = locs[XR_HAND_JOINT_THUMB_TIP_EXT];
        constexpr XrSpaceLocationFlags kPos =
            XR_SPACE_LOCATION_POSITION_VALID_BIT;
        constexpr XrSpaceLocationFlags kRot =
            XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;
        if ((tip.locationFlags & kPos) && (thumb.locationFlags & kPos)) {
            float d = dist3(tip.pose.position, thumb.pose.position);
            // Hysteresis: enter at 25 mm, release at 35 mm. Avoids
            // chattering while user holds a near-pinch pose.
            bool nowPinch = *prevState
                ? d < kPinchReleaseHysteresis
                : d < kPinchThresholdMeters;
            *outEdge = (!*prevState) && nowPinch;
            *prevState = nowPinch;
            *outPinch = nowPinch;
        }
        if (tip.locationFlags & kRot) {
            *outAim = tip.pose;
            *outValid = true;
        } else {
            *outValid = false;
        }
    };

    bool prevL = mLeftPinch;
    bool prevR = mRightPinch;
    bool edgeL = false, edgeR = false;

    locate(mLeft, lLocs, &mLeftPinch, &edgeL, &mLeftAim, &mLeftValid, &prevL);
    locate(mRight, rLocs, &mRightPinch, &edgeR, &mRightAim, &mRightValid,
           &prevR);

    if (edgeL) mLeftPinchEdge = true;
    if (edgeR) mRightPinchEdge = true;
}

bool HandTracking::consumeLeftPinchEdge() {
    bool e = mLeftPinchEdge;
    mLeftPinchEdge = false;
    return e;
}

bool HandTracking::consumeRightPinchEdge() {
    bool e = mRightPinchEdge;
    mRightPinchEdge = false;
    return e;
}

bool HandTracking::leftAimPose(XrPosef* out) const {
    if (!mLeftValid) return false;
    *out = mLeftAim;
    return true;
}

bool HandTracking::rightAimPose(XrPosef* out) const {
    if (!mRightValid) return false;
    *out = mRightAim;
    return true;
}

}  // namespace vrplayer
