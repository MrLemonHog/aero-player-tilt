# player-tilt → ACS API migration

## Goal

`aeronautics-player-tilt` (`aero_player_tilt`) currently is a full fork of `Aeronautics Camera
Sync` (`aero_cam_sync`): it duplicates ACS's own camera/aim-tilt computation instead of using it.
Now that ACS ships a public API (`com.playsi.aero_cam_sync.api`, since 1.3.7), player-tilt becomes
a real addon: a hard dependency on ACS, with all camera/aim tilt computation deleted and replaced
by API calls. player-tilt keeps only what ACS does not do: tilting player **bodies/hitboxes** to
match the deck they stand on, and broadcasting that body tilt to other clients so everyone sees
everyone else's model tilted.

## Dependency

ACS becomes a **required**, not optional, dependency.

- `build.gradle`: `compileOnly "maven.modrinth:aero_cam_sync:1.3.7"` and a matching `runtimeOnly`
  entry (dev-run needs the jar on the classpath; NeoForge's mod-loading dedupes it against the
  same coordinate declared for the runtime, so both are needed — `compileOnly` keeps ACS's classes
  out of player-tilt's own jar, `runtimeOnly` puts them on the dev-run classpath).
- `neoforge.mods.toml`: `type="required"` (not `optional`), `ordering="AFTER"`, `side="BOTH"`.
  Because it's required, NeoForge itself refuses to load player-tilt without ACS present — no
  `ModList.isLoaded` guard, no soft-dependency bridge class is needed anywhere in the code.
- Access: one static handle, `AeroCamSyncApi.forMod("aero_player_tilt")`, held in a small holder
  class (e.g. `AcsBridge`), used everywhere player-tilt needs ACS state.

## What gets deleted

Everything that recomputes or applies camera/aim tilt for the **local player** — this is now
entirely ACS's job:

- `TiltAccess`, `client/utils/ClientTiltAccess`
- `client/utils/CameraController` — the camera-rotation/position/wall-scale parts
  (`applyCameraRotation`, `applyCameraPosition`, `updateWallScale`, `getSmoothedTilt`,
  `getRawTilt`* used for camera/ray purposes). *`getRawTilt`'s deck-following body value is kept,
  see below — just detached from the camera-facing half of the class.
- `SideManager`'s look/pos-tilt fields and the part of `sendTiltToServer` that sends them — the
  server no longer needs a locally-recomputed look/pos tilt because nothing downstream (rays,
  projectiles) is player-tilt's to shift anymore.
- Mixins that shift rays/camera/entity-look for the local player: `CameraMixin`,
  `EntityLookMixin`, `GameRendererPickMixin`, `CreateRaycastTiltMixin`, `ItemPovTiltMixin`,
  `EntityHitTiltMixin`, `EntityReachTiltMixin`, `EntityPushTiltMixin`, `EntityShoveTiltMixin`,
  `ProjectileShootTiltMixin`, `SableCompatClipMixin`, `AirControlTiltMixin`,
  `EntityLightingTiltMixin`, `PlayerFitTiltMixin`, `PlayerDropTiltMixin`, `JumpTiltMixin`,
  `ServerEntityLookMixin`.
- `ServerTiltStore`'s `lookTilt`/`rotActive`/`posShift`/`dropFromCamera` fields and
  `getLookTilt`/`getPosTilt`/`getDropFromCamera` — body fields stay (see below).
- `network/Payload/TiltSyncPayload`'s look/pos-tilt fields — trimmed to carry only body tilt.
  `HandshakePacket`/`HandshakeResponsePacket` are re-evaluated: keep only if still needed to
  negotiate the body-tilt channel; if their only past purpose was legacy-pick negotiation for
  camera tilt, delete them too.

Config options that only ever controlled the deleted camera/aim behaviour
(`MODIFY_CAMERA_ROT`, `MODIFY_CAMERA_POS`, `ALLOW_3RD_PERSON`, `CAMERA_COLLISION*`,
`DROP_FROM_CAMERA`, `IGNORE_SERVER`, `LEGACY_PICK`-equivalents) are removed from `Config` and their
screens/categories (`CameraCategory`, `RaycastCategory`, relevant entries in `ActivationCategory`)
— that's ACS's config now, reached through ACS's own settings screen, not player-tilt's.

## What stays, and how it's wired to the API

**Body/hitbox tilt is untouched in its own logic** — `SurfaceRaycaster` (raycasts the deck under
the player), `PlayerTilt` (deck-normal → quaternion, "is this meaningful" threshold),
`TiltedPlayerBox`, `BoxOrientation`, `TiltedFitCheck`, `JumpTickAccess`, the deck-following part of
`CameraController` (renamed/moved — it no longer has anything to do with the camera, so it moves
out of `client/utils/CameraController` into `client/tilt/` alongside `ClientPlayerTilt`).

Two places do need one API call each, replacing local recomputation:

1. ~~**Gating.** `shouldComputeTilt()` currently reimplements "is the mod-equivalent-of-ACS on".
   Replace with `AcsBridge.ACS.state(player, partialTick).modEnabled()`~~ — **reverted, do not
   redo.** `modEnabled()` is not "is ACS on": it is "is ACS tilting this player's camera right
   now", and that is false in third person (ACS's `allow3rdPerson` defaults to `false`) and while
   holding a blacklisted item. Gating the body on it made F5 and a bow in hand snap the body
   upright — which contradicts the camera-independence this same document requires below.
   `shouldComputeTilt()` uses player-tilt's own `MOD_ENABLED` plus "no vehicle"; that also removes
   the `ACS.state()` reentrancy that needed a `ThreadLocal` guard.
2. **Suppression.** Where player-tilt used to have no concept of "a cutscene wants tilt off" (it
   didn't need one, being self-contained), it now should call
   `AcsBridge.ACS.isSuppressed()` (the handle, not `state()`, which walks back into Sable and
   therefore into us) and hold the body tilt level while ACS
   is suppressed — otherwise a mod that calls `ACS.suppress()` levels the camera while the body
   next to it stays tilted, which looks broken from a third-person viewer's angle.

Everything else about body tilt — the raycast, the smoothing, the OBB fit check, the network
broadcast, the render mixins that apply it to a model/hitbox — is unaffected, because none of it
existed in ACS to begin with and the API has no opinion on it.

**Rendering the tilted body of other players** stays as-is: `BodyTiltBroadcaster`,
`ServerTiltStore` (body fields), `TiltSnapshot`, `client/tilt/ClientPlayerTilt`,
`FirstPersonModelTiltMixin`, `TiltedHitboxDebugMixin`, the sable sub-level render mixins
(`EntitySubLevelRotationHelperMixin`, `SubLevelEntityShadowRendererMixin`), and the collision
mixins under `mixins/sable/`.

## Data flow (after)

```
Every client tick:
  AcsState state = AcsBridge.ACS.state(localPlayer, 1.0f)
  bodyAllowed = state.modEnabled() && Config.PLAYER_TILT.get() && no vehicle
  if suppressed: hold current body tilt level (don't re-raycast toward level)
  else: SurfaceRaycaster + PlayerTilt compute deck-relative body quaternion, as before
  ClientPlayerTilt.pushLocal(...) snapshot, as before
  SideManager.sendTiltToServer() sends ONLY the body quaternion + active flag
Server:
  ServerTiltStore stores body tilt per player (look/pos fields removed)
  BodyTiltBroadcaster rebroadcasts body tilt to nearby viewers, unchanged
Remote client:
  ClientPlayerTilt.accept(...) as before
  Render mixins apply body tilt to the model/hitbox, unchanged
```

## Testing

- Manual: two clients + a Sable sub-level (existing dev-test setup). Confirm own body tilts with
  the deck, other player's body tilts to match theirs, and both stop tilting (camera AND body)
  when a test mod calls `ACS.suppress()`.
- Confirm player-tilt refuses to load with ACS absent (NeoForge's own dependency error screen,
  not a player-tilt crash).
- No unit tests existed for the deleted code; the kept body-tilt math (`PlayerTilt`,
  `TiltedPlayerBox` etc.) has none today either — out of scope to add as part of this migration.

## Out of scope

- Any change to ACS itself.
- Adding `AimPolicy`/`TiltListener` registrations — player-tilt doesn't build its own aiming rays,
  so it has nothing for a policy to catch, and no camera-facing behaviour to react to via a
  listener (suppression is read directly from `state()` each frame, which is simpler than a
  listener for this one boolean).
