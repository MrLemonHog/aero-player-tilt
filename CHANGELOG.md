# Changelog
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
