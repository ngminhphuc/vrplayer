# Input mapping — Quest Touch (Stage 1 Sprint 1)

Action set: `vrplayer`. Suggested binding: `/interaction_profiles/oculus/touch_controller`.

## Actions

| Action | Type | Bindings | Effect |
|---|---|---|---|
| `trigger` | bool | `…/left/input/trigger/value`, `…/right/input/trigger/value` | Edge-trigger → toggle play/pause (`ExoPlayer.playWhenReady`). |
| `grip` | bool | `…/left/input/squeeze/value`, `…/right/input/squeeze/value` | Hold to grab and drag the cinema screen. Releases on un-press; transform persisted via `SharedPreferences`. |
| `thumbstick` | vec2 | `…/left/input/thumbstick`, `…/right/input/thumbstick` | X past ±0.7 → seek ±10 s (rate-limited 1 / 250 ms). Y past ±0.2 → ExoPlayer volume delta `±0.01` per frame. |
| `menu` | bool | `…/left/input/menu/click`, `…/right/input/system/click` | Edge-trigger → recenter (recreates `XR_REFERENCE_SPACE_TYPE_LOCAL`). Long-press will open the overlay control bar in Stage 1 Sprint 3. |
| `aim_pose` | pose | `…/left/input/aim/pose`, `…/right/input/aim/pose` | Drives the laser-pointer rays and grip-drag delta source. |

Whichever thumbstick is more deflected wins — both controllers feed the same axis state. This avoids a "primary hand" preference dialogue; users can hand the headset to someone else without resetting bindings.

## Frame timing

- 72 Hz on Q2, 90 Hz on Q3 (default). `XR_FB_display_refresh_rate` enabled when present so the next sprint can call `xrRequestDisplayRefreshRateFB` to lock 72 Hz on Q2 explicitly.
- FFR (`XR_FB_foveation` + `XR_FB_swapchain_update_state`) is **requested at instance creation time** when available. The actual `xrUpdateSwapchainFB` call to apply foveation level 2 is wired up but gated on a Quest device runtime check — verify in Stage 1 Sprint 2 build with `OVRMetricsTool`.

## Out of scope (this sprint)

- Hand tracking — Stage 3 Sprint 2.
- Voice control — Stage 3 Sprint 3.
- World-space Compose UI for the menu overlay — Stage 1 Sprint 3.
