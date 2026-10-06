# Changelog
## 0.2.2

### Compatibility
- added Pehkui and Create: Pocket Sized compat: rays, sticky tilt, magnetic boots and the player model now follow the player size

### Fixed
- fixed crash with Create: Pocket Sized 0.20.0
- fixed fps dropping near shrunk sub-levels
- fixed sneaking getting stuck between blocks on scaled sub-levels
- fixed sliding off a sub-level while sneaking with magnetic boots or sticky tilt

## 0.2.1

### Compatibility
- added Physics Mod Pro compat

### Fixed
- fixed camera spinning on jump after walking corners with magnetic boots
- fixed sticky tilt staying active after lowering max tilt back to 37° or below

## 0.2.0

### Added
- Magnetic boots [Beta]: any face becomes your floor, walls and ceilings included. Jump again in the air to let go.
- Sticky tilt: stops you sliding off a tilted deck.
- Rotate Camera: turn it off to keep the camera level while your body still tilts.
- Server config: `allowMagneticBoots` and `allowStickyTilt` turn these features off for every player.

### Changed
- Max Tilt Threshold gains an infinity stop.
- Settings under a switch that is off are now hidden.
- Settings turned off by the server are locked in the menu.
- The "World rules" settings tab is now called "Experimental".
- Config file comments have been rewritten.

## 0.1.3

### Fixed
- Fixed the mod refusing to launch alongside Create: Pipes'n Physics.
- Fixed a tilted body being drawn unlit or in the wrong colour next to Contraption Lights and LambDynamicLights.
- Fixed dropped items and other entities on a tilted deck being drawn pitch black.

## 0.1.2

### Added
- A tilt limit setting has been added to the server configuration. `enforceMaxTilt` holds every player to the server's `maxTilt`.

### Changed
- The Max Tilt Threshold slider now shows what the value means in degrees.

## 0.1.1

### Added settings
"Turn with the sub-level" keeps your heading relative to the deck instead of the world.
"Carry momentum with the sub-level" makes walking on a fast-spinning one work normally again

### Changed
- A gravity setting has been added to the server configuration
- Some descriptions of settings in the menu have been updated
- Minor mod metadata updates.

### Fixed
- Fixed the lean picking up a spinning sub-level's rotation, which skewed and juddered the view
- Fixed a sub-level letting go of you between strides, so a turning deck slid out from under you
- Fixed a player on a seat being drawn floating off it in third person and for other players
