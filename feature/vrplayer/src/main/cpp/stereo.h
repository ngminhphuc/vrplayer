#pragma once

namespace vrplayer {

/**
 * Stereo layout descriptor — applied as a UV transform when sampling the
 * video texture. Each layout maps the *full* texture to two half-regions
 * (one per eye). The render code calls [uvScaleOffset] to fetch the
 * (scaleU, scaleV, offsetU, offsetV) for the eye it is currently drawing.
 *
 * Conventions:
 *   - SBS (Side-by-Side) splits horizontally: left half = left eye.
 *   - TB  (Top-Bottom) splits vertically: top half = left eye in TB-LT,
 *     bottom half = left eye in TB-RT (a.k.a. "OverUnder reversed").
 *   - Mono uses the full texture for both eyes.
 *   - The "reversed" variants swap the eye-to-half mapping.
 */
class Stereo {
  public:
    enum class Mode {
        Mono,
        SbsLeftRight,   // L | R
        SbsRightLeft,   // R | L
        TbLeftRight,    // L over R
        TbRightLeft,    // R over L
    };

    static void setMode(Mode m);
    static Mode mode();

    /**
     * Output uv crop for [eyeIndex] (0 = left, 1 = right). Caller passes
     * the result to the shader as a uniform `vec4(scaleU, scaleV, offU, offV)`
     * and computes `uv' = uv * scale + offset`.
     */
    static void uvScaleOffset(int eyeIndex, float out[4]);
};

}  // namespace vrplayer
