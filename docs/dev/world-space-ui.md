# World-space UI — Stage 1 Sprint 2

## Architecture

```
+--------------------+        +------------------------+
|  ComposeView       |        |  Native render thread  |
|  VrPickerScreen    |  draw  |  PickerQuad (GLES)     |
|       │            |─────▶  |  samplerExternalOES    |
|  Surface(Canvas)   |        |  drawn into FBO eye    |
+----┬──┬────────────+        +───┬────────────────────+
     │  │                          │
     │  └── SurfaceTexture(extId)──┘
     │
     │ MotionEvent.dispatchTouchEvent
     ▲
     │
+----┴────────────────────────────────────────────────+
|  XrSession::processInput                            |
|    if (PickerQuad::visible() && trigger edge)       |
|        PickerQuad::hitTest(aim) → injectPickerTap   |
+──────────────────────────────────────────────────────+
```

## Surface pipeline

1. `XrActivity` lazily creates `PickerSurfaceHost`. Native `VideoBridge::requestPickerSurface` calls into `acquirePickerSurface(textureId)`; the host wraps that texture id in a `SurfaceTexture` and a `Surface`.
2. The host also adds an invisible `WindowManager` window so the `ComposeView` has a `ViewTreeLifecycleOwner` and `SavedStateRegistry`.
3. Every 33 ms the host runs `Surface.lockCanvas` → `ComposeView.draw(canvas)` → `unlockCanvasAndPost`. The native side polls `updatePickerTexImage()` per frame.

## Hit-testing

`PickerQuad::hitTest` projects the controller aim ray onto the picker plane (`z = -1.4 m`) using only the quaternion-rotated forward vector. `(u, v)` is normalized to `[0, 1]` with origin top-left. We forward `(u * width, v * height)` as a `MotionEvent.ACTION_DOWN`/`ACTION_UP` pair on the main thread.

## Resume

`ResumeStore` (SharedPreferences `vrplayer_resume`):
- `<file_path>` → last-known `currentPosition` (Long).
- `_last_played` → last picked `<file_path>`.

A 5 s ticker writes the current position. `onPause` flushes synchronously to avoid losing the final segment when Quest goes idle. On `onResume`, if a last-played path exists, that file is loaded and seek'd; otherwise the bundled `spike0_sample.mp4` plays.

Migration to proto DataStore (`core:datastore`) is scheduled for Stage 1 Sprint 3 once the broader Hilt graph wires into `feature:vrplayer`.

## Known limitations

- The `Surface.lockCanvas` path is a workaround for the absence of stable Compose-to-Surface APIs. Stage 1 Sprint 3 will move to `Window#takeSurface` via `OffscreenComposition` once we validate it compiles against the Compose BOM in use.
- Picker texture is in-scene (depth-tested), not an `XrCompositionLayerQuad`. Promotion to a real quad layer is part of the FFR / sharpness pass in Sprint 3.
- No SAF folder picker yet — picker surfaces only show files visible via `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`. Folder grant arrives with the SMB / streaming work in Stage 3.
