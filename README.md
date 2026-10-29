![Description](https://github.com/MrLemonHog/archive/blob/main/img/apt/description-aptv2.png?raw=true)
<img src="https://cdn.modrinth.com/data/R8r1wiW4/images/bace61aa0d1dab03a797f447a166a47b0a15d07e.webp" width="100%" alt="preview">

### Your body and hitbox tilt with the [Create Aeronautics](https://modrinth.com/mod/create-aeronautics) ship you're standing on

Aeronautics Player Tilt is an add-on for [Aeronautics Camera Sync](https://modrinth.com/mod/aero_cam_sync). ACS tilts your camera with the deck, but you still stand upright on it. People kept asking for the body to tilt too - so here it is.

<br>

![What this mod do?](https://github.com/MrLemonHog/archive/blob/main/img/apt/wtmdv2.png?raw=true)

<br>

![Gravity](https://github.com/MrLemonHog/archive/blob/main/img/apt/gravity.png?raw=true)

## Which way you fall above a tilted ship:

#### **World** - you fall toward the world's down, so over a tilted deck you drift sideways across it.
#### **Sub-level** - you fall toward the deck's own floor, so it works like flat ground.

<img src="https://github.com/MrLemonHog/archive/blob/main/img/apt/output1.webp?raw=true" width="100%" alt="gravity">
<br>
<br>

![mobs&items](https://github.com/MrLemonHog/archive/blob/main/img/apt/mobs-items.png?raw=true)
### Items and mobs standing on a deck tilt with it, just like you.

<img src="https://github.com/MrLemonHog/archive/blob/main/img/apt/output2.webp?raw=true" width="100%" alt="mobs and items">

#### **Keybind:** <kbd>O</kbd> toggles body tilt on/off (rebindable in the config screen or in controls menu).

<br>

![Requires](https://github.com/MrLemonHog/archive/blob/main/img/apt/requiresv3.png?raw=true)

- [Sable](https://modrinth.com/mod/sable)
- [Aeronautics Camera Sync](https://modrinth.com/mod/aero_cam_sync) 1.4.0+

<br>

## For mod developers

Player Tilt leans the player's body and keeps a tilted hitbox. Most mods need no changes: vanilla rays, projectiles and entity clicks already hit the tilted player

If your mod runs its own hit tests against players, or you want it to drive the tilt itself, start at the [documentation](https://github.com/MrLemonHog/aero-player-tilt/blob/main/docs/API.md)

Free to use in any modpack, public or private. No permission needed, and a link back is welcome but not required.
