#pragma once

// Tiny column-major 4x4 matrix helpers. Pulled out into a separate header so
// renderers, input, and pointer code can share without duplicating.

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES
#include <openxr/openxr.h>

#include <cmath>
#include <cstring>

namespace vrplayer::mu {

inline void identity(float* m) {
    static constexpr float kI[16] = {
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
    };
    std::memcpy(m, kI, sizeof(kI));
}

inline void multiply(const float* a, const float* b, float* out) {
    float r[16];
    for (int c = 0; c < 4; ++c) {
        for (int rr = 0; rr < 4; ++rr) {
            r[c * 4 + rr] = a[0 * 4 + rr] * b[c * 4 + 0] +
                             a[1 * 4 + rr] * b[c * 4 + 1] +
                             a[2 * 4 + rr] * b[c * 4 + 2] +
                             a[3 * 4 + rr] * b[c * 4 + 3];
        }
    }
    std::memcpy(out, r, sizeof(r));
}

inline void translation(float x, float y, float z, float* m) {
    identity(m);
    m[12] = x;
    m[13] = y;
    m[14] = z;
}

inline void scale(float sx, float sy, float sz, float* m) {
    identity(m);
    m[0] = sx;
    m[5] = sy;
    m[10] = sz;
}

/** Convert XrPosef (rotation + translation) into a column-major 4x4. */
inline void poseToMat(const XrPosef& pose, float* m) {
    const float x = pose.orientation.x;
    const float y = pose.orientation.y;
    const float z = pose.orientation.z;
    const float w = pose.orientation.w;
    const float xx = x * x, yy = y * y, zz = z * z;
    const float xy = x * y, xz = x * z, yz = y * z;
    const float wx = w * x, wy = w * y, wz = w * z;

    m[0] = 1 - 2 * (yy + zz);
    m[1] = 2 * (xy + wz);
    m[2] = 2 * (xz - wy);
    m[3] = 0;
    m[4] = 2 * (xy - wz);
    m[5] = 1 - 2 * (xx + zz);
    m[6] = 2 * (yz + wx);
    m[7] = 0;
    m[8] = 2 * (xz + wy);
    m[9] = 2 * (yz - wx);
    m[10] = 1 - 2 * (xx + yy);
    m[11] = 0;
    m[12] = pose.position.x;
    m[13] = pose.position.y;
    m[14] = pose.position.z;
    m[15] = 1.f;
}

/** View matrix = inverse of pose-as-mat. Pose is rigid so inverse is cheap. */
inline void poseToView(const XrPosef& pose, float* m) {
    float poseMat[16];
    poseToMat(pose, poseMat);
    // Transpose 3x3.
    m[0] = poseMat[0];
    m[1] = poseMat[4];
    m[2] = poseMat[8];
    m[3] = 0;
    m[4] = poseMat[1];
    m[5] = poseMat[5];
    m[6] = poseMat[9];
    m[7] = 0;
    m[8] = poseMat[2];
    m[9] = poseMat[6];
    m[10] = poseMat[10];
    m[11] = 0;
    m[12] = -(m[0] * poseMat[12] + m[4] * poseMat[13] + m[8] * poseMat[14]);
    m[13] = -(m[1] * poseMat[12] + m[5] * poseMat[13] + m[9] * poseMat[14]);
    m[14] = -(m[2] * poseMat[12] + m[6] * poseMat[13] + m[10] * poseMat[14]);
    m[15] = 1.f;
}

inline void projection(const XrFovf& fov, float zNear, float zFar, float* m) {
    const float l = std::tan(fov.angleLeft);
    const float r = std::tan(fov.angleRight);
    const float u = std::tan(fov.angleUp);
    const float d = std::tan(fov.angleDown);
    const float w = r - l;
    const float h = u - d;
    const float fr = zFar - zNear;
    m[0] = 2.f / w;
    m[1] = 0;
    m[2] = 0;
    m[3] = 0;
    m[4] = 0;
    m[5] = 2.f / h;
    m[6] = 0;
    m[7] = 0;
    m[8] = (r + l) / w;
    m[9] = (u + d) / h;
    m[10] = -(zFar + zNear) / fr;
    m[11] = -1.f;
    m[12] = 0;
    m[13] = 0;
    m[14] = -(2.f * zFar * zNear) / fr;
    m[15] = 0;
}

}  // namespace vrplayer::mu
