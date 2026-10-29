# Player Tilt API

Player Tilt leans the player's body, model and hitbox with the Sable sub-level they stand on, and
runs the magnetic boots. The API gives another mod the same controls the mod uses itself: read the
tilt, drive it, tune it, split the model from the hitbox, pause it, control it from the server,
change gravity, tilt mobs, and switch the boots on from its own item

Everything is in one class, `com.mlh.aero_player_tilt.api.PlayerTiltApi`:

| Type | What it is |
|---|---|
| `PlayerTiltApi.forMod("yourmod")` | entry point, returns your `Handle` |
| `Handle` | everything you can ask for |
| `TiltState` | snapshot of one entity's tilt |
| `TiltSource` / `TiltContext` / `Tilt` | your own tilt for the local player |
| `TiltSettingsSource` / `TiltSettings` | how Player Tilt's own tilt behaves |
| `EntityTiltSource` | your own tilt for mobs and items |
| `BootsSource` / `BootsSettings` | whether the local player wears your boots, and how they behave |
| `BootsSurfaceFilter` | which blocks the boots may grip |
| `GravitySource` | which way, and how hard, an entity falls |
| `TiltListener` | tilt, boots and deck events |

Import the nested types (`import com.mlh.aero_player_tilt.api.PlayerTiltApi.*;`) and use them by
their short names.

## Adding the dependency

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    compileOnly "maven.modrinth:aeronautics-player-tilt:<version>"
}
```

The API compiles against Minecraft and JOML only, you do not need Sable or Camera Sync on your
compile classpath.

If Player Tilt is optional for you, declare it with an order and keep every call in a class of its
own, so the API types are never loaded when the mod is missing:

```toml
[[dependencies.yourmod]]
    modId="aero_player_tilt"
    type="optional"
    versionRange="[0.3.0,)"
    ordering="AFTER"
    side="BOTH"
```

```java
if (ModList.get().isLoaded("aero_player_tilt")) PlayerTiltBridge.init();
```

## Reading the tilt

```java
private static final PlayerTiltApi.Handle TILT = PlayerTiltApi.forMod("yourmod");

TiltState state = TILT.state(entity, partialTick);
if (state.tilted()) {
    Vector3f up = state.up();                // the body's up in world space
    Quaternionf q = state.orientation();     // world up -> body up, identity when upright
}
```

`forMod` throws `IllegalArgumentException` for a null or blank id, the same id always returns the
same handle, so keep it in a static field.

Works on players, and on mobs and items. On the client it is the drawn tilt of anyone in view, on
the server it is what each client last reported, which is what the server's hitbox uses.

- `orientation` is the body: hitbox, physics, collisions.
- `model` is what is drawn. It equals `orientation` unless a source tilted the model alone.
- `pivot` is the height above the feet the model turns around.
- `booted` says whether magnetic boots or Sticky tilt are holding the body to a face.
- `face` is the normal of the face the boots hold, in world space, local player only, null when
  not booted.
- `deck` is the sub-level the tilt follows, or null.
- `surface` is the floor normal Player Tilt measured under the local player this frame.
- `drivenBy` is the mod id of the source driving the local player, or null.

## The tilted hitbox

A tilted body keeps a tilted hitbox. Vanilla rays, projectiles and entity clicks already hit it,
because Player Tilt patches them. If your mod runs its own raycast or overlap test against an
entity's `getBoundingBox()`, it will miss a leaning player. Ask the API instead:

```java
Optional<Vec3> hit = TILT.clip(target, from, to);   // where the segment enters the box
boolean inside    = TILT.contains(target, point);
AABB broad        = TILT.bounds(target);            // axis-aligned box around the tilted one
```

For an entity that is not tilted these are exactly the vanilla box answers, so call them for every
entity without checking first.

### Hitbox size

The tilted hitbox is the entity's own bounding box, turned around the feet. Player Tilt and Sable
both read its size from `getBoundingBox()`, so change the size the vanilla way and the tilt, the
collisions with the deck and the hit tests all follow:

```java
@SubscribeEvent
static void onSize(EntityEvent.Size event) {
    if (event.getEntity() instanceof Player player && shrunk(player)) {
        event.setNewSize(EntityDimensions.scalable(0.4f, 0.9f).withEyeHeight(0.8f));
    }
}
```

Call `player.refreshDimensions()` when your condition changes. Mods that scale players, such as
Pehkui, already work this way. The point the hitbox turns around stays at the feet: Sable anchors
its deck collisions there.

## Driving the tilt

A source decides the local player's tilt on the frames it claims. Return a `Tilt`, or `null` to
leave the frame alone.

```java
TILT.addTiltSource(0, ctx -> inMyGravityZone(ctx.player())
        ? Tilt.of(myGravity.orientation(ctx.partialTick()))
        : null);
```

The rotation goes from world up to the body's up. By default it becomes the tilt everywhere: the
model, the hitbox on both sides, the camera through Camera Sync, and what other players see.

- Sources are asked once per frame, highest priority first, ties in registration order. The first
  non-null answer wins.
- When nobody claims, Player Tilt's own tilt from the deck is used.
- Smoothing on the way in is yours, against `ctx.deltaTicks()`. The way out is handled for you:
  the body eases from your last tilt back to its own.
- The player's own body tilt toggle still wins: with it off, sources are not asked.

To modify the lean instead of replacing it, start from `ctx.ownTilt()`:

```java
TILT.addTiltSource(0, ctx -> Tilt.of(new Quaternionf().slerp(ctx.ownTilt(), 0.5f)));   // half the lean
```

`ctx.surface()` is the floor normal Player Tilt measured this frame, or null in the air.

### What a tilt changes

`Tilt` carries four switches next to the rotation. Each `with...` returns a copy:

| Switch | Default | What it does                                                                |
|---|---|-----------------------------------------------------------------------------|
| `free` | false | no limits: past the max tilt, upside down, with a twist around the vertical |
| `hitbox` | true | false tilts the model only, hitbox and physics keep Player Tilt's own tilt  |
| `camera` | true | false keeps the view level, the eye still moves with the head               |
| `pivot` | 0 | height above the feet, in blocks (0 – 4), the model and camera turn around  |

```java
Tilt.of(upsideDown).withFree(true);                     // walk on the ceiling of a gravity room
Tilt.of(bow).withHitbox(false).withCamera(false);       // an emote: only the model bows
Tilt.of(lean).withPivot(0.9f);                          // turn at the hips, not the feet
```

Without `free`, the tilt goes through the same limits as the player's own lean: the max tilt (the
server's, if it enforces one) and no twist. With it, those limits are skipped here, on the server
and for other players. Physics still treats the body as leaning, not as booted.

The hitbox always turns around the feet, `pivot` moves only the model and the camera.

## Tuning Player Tilt's own tilt

Every setting of the player's own lean can be changed per player, per frame. You get the player's
current settings and return yours, or `null` to keep them:

```java
TILT.addTiltSettingsSource((player, current) -> onMyShip(player)
        ? current.withSmoothIn(4.0).withMaxAngle(60)
        : null);
```

| Setting | Default | Range | What it does |
|---|---|---|---|
| `smoothIn` | 1.7 | 0 – 9999 | half-life in ticks of leaning onto the floor; 0 = snap |
| `smoothOut` | 4.0 | 0 – 9999 | half-life in ticks of straightening up off the deck |
| `holdTicks` | 5 | 0 – 200 | how long the tilt survives with no floor found |
| `airborneHoldTicks` | 60 | 0 – 400 | how long the tilt survives a jump over the deck |
| `predictLanding` | true | | turn towards the floor you are going to land on |
| `maxAngle` | 36.9 | 0 – 180 | steepest floor the body leans onto, in degrees; 90 or more = any face |
| `multiplier` | 1.0 | 0 – 1 | how much of the tilt the body takes |
| `rotateCamera` | true | | the camera turns with the body |
| `turnWithDeck` | true | | on a spinning deck, keep facing the same way relative to it |
| `sticky` | false | | Sticky tilt: don't slide off steep decks (needs `maxAngle` above 36.9) |

The first source that answers wins. A server that enforces its max tilt still wins over `maxAngle`.

## Splitting model and hitbox

`hitbox(false)` on a `Tilt` is how a mod animates the body without moving the hitbox:

```java
TILT.addTiltSource(10, ctx -> emote.playing()
        ? Tilt.of(emote.pose(ctx.partialTick())).withHitbox(false).withCamera(false)
        : null);
```

The model is drawn at your rotation, the hitbox, collisions and physics keep Player Tilt's own tilt,
magnetic boots included. Other players see the same model.

## Tilting mobs and items

Mobs and items are tilted on each side by itself, nothing is synced, so an entity source must give
the same answer on the client and on the server: the client draws it, the server's hitbox uses it.

```java
TILT.addEntityTiltSource(0, (entity, partialTick) -> entity.getType() == MyEntities.DRONE.get()
        ? myDrone(entity).orientation(partialTick)
        : null);
```

- Players are never asked: their tilt belongs to their own client, see `addTiltSource`.
- A source tilts any entity it answers for, even with "Tilt mobs" or "Tilt items" off.
- No limits are applied, twist included. Called often, for every entity, so keep it cheap.

### From the server

Entity sources run on each side by themselves. When only the server knows the tilt, set it there and
it is sent to every player who can see the entity:

```java
TILT.setEntityTilt(drone, droneTilt, 2f);   // half-life in ticks the clients ease towards it
TILT.setEntityTilt(drone, null, 0f);        // stop
```

Send it again whenever the tilt changes. A set tilt wins over entity sources and the deck, an
exclusion still wins over it. Called on the client it only changes that client's view.

### Keeping an entity upright

Some entities look wrong tilted: a boss with its own animation, a display entity, a mob that is
meant to hover level. Add the entity type to the `aero_player_tilt:upright` entity type tag and
it stays upright no matter what, deck, source or `setEntityTilt`. The tag works from any mod or
data pack, with no code, and the server sends it to clients, so both sides always agree:

```json
// data/aero_player_tilt/tags/entity_type/upright.json
{
  "values": ["yourmod:boss", "minecraft:armor_stand"]
}
```

In code the tag is `PlayerTiltApi.UPRIGHT`, for data generation or your own checks.

Server owners have `mobList` and `itemList` in the server config as well, with a blacklist or
whitelist mode. Those only gate the tilt from the deck, like the "Tilt mobs" and "Tilt items"
switches: an entity source or `setEntityTilt` still tilts a mob that is left out of them. The tag
wins over everything.

When a type is not enough, for example only bosses in their second phase, add an exclusion test.
It is asked on both sides, so it must give the same answer on each:

```java
TILT.addEntityExclusion(entity -> entity instanceof MyBoss boss && boss.phase() == 2);
```

Players are never excluded: their tilt belongs to their own client.

## Gravity

A gravity source says which way, and how hard, a living entity falls, in multiples of normal
gravity. `null` leaves it alone.

```java
TILT.addGravitySource(entity -> inMyStation(entity) ? new Vec3(0, 0, 0) : null);          // weightless
TILT.addGravitySource(entity -> onMyPlanet(entity) ? planet.down(entity).scale(0.4) : null); // towards a planet
```

- `(0, -1, 0)` is vanilla. The strength scales `getGravity()`, so the vanilla gravity attribute
  and slow falling still apply.
- Jumps push against your gravity instead of straight up.
- It runs where the entity is simulated: the local player on their client, mobs on the server.
  Register it on both sides.
- Magnetic boots win while they hold a face. Otherwise it replaces the deck gravity setting.
- It moves the entity, nothing more. To stand on a wall, pair it with a `TiltSource`, or use the
  boots: vanilla only knows the ground below your feet.

## Pausing the tilt

For cutscenes, emotes and the like: the player is drawn upright and the camera goes level, while
everything that keeps them on the deck carries on.

```java
TILT.suppress(5_000);   // take or extend this mod's lease, in milliseconds, 0 or less is ignored
TILT.release();         // drop it early
```

While a lease is held:

- the model eases upright, facing the way it faced, over `smoothOut`, the camera follows it. On
  release it eases back over `smoothIn`. Other players see the same.
- the hitbox, the gravity along the deck, magnetic boots and Sticky tilt are left alone. A player
  on a steep deck, a wall or a ceiling stays exactly where they are, pausing never drops anyone
  off a ship.
- tilt sources are still asked, and a source that moves the hitbox still moves it, only what is
  drawn is held upright.
- `TiltState.orientation` and `tilted` still describe the body, `model` is the upright one.

To actually take the player off a face, call `releaseBoots()` as well. Each mod holds its own
lease, the player is held upright while any mod holds one, and you cannot drop someone else's.

## Magnetic boots

Player Tilt carries the whole magnetic boots mechanic: walking on walls and ceilings, rolling over
edges and corners, turning with the deck. Your mod decides when the player has them on and how they
behave. Return settings when the player wears your boots, or `null` when not:

```java
TILT.addBootsSource(player -> player.getItemBySlot(EquipmentSlot.FEET).is(MyItems.BOOTS.get())
        ? BootsSettings.DEFAULT
        : null);
```

Asked every client tick with the local player, so keep it cheap: an armor slot or a few blocks
around the feet, not a scan. The first source that answers wins. The server's
`allowMagneticBoots` still has the last word.

| Setting | Default | Range | What it does                                                                         |
|---|---|---|--------------------------------------------------------------------------------------|
| `reach` | 0.75 | 0.1 – 4 | how far below the feet to look for a face, in blocks (scaled with the player's size) |
| `leanStart` | 0.30 | 0 – 0.5 | how close to an edge the body starts leaning over it, 0 = no lean                    |
| `ledgeHeight` | 3.0 | 1 – 6 | how high a ledge you can still jump down from, in blocks                             |
| `grip` | 1.0 | 0.25 – 6 | how hard the boots press into the face, jumps are unaffected                         |
| `turnTime` | 1.0 | 0 – 20 | ticks the body takes to turn onto a new face, 0 = instant                            |

Values outside the range are clamped, NaN falls back to the default. Change one and keep the rest:

```java
BootsSettings space = BootsSettings.DEFAULT.withGrip(0.4).withLedgeHeight(6);
```

The answer is read every tick, so settings may change while the boots are on. A centrifuge that
presses harder the faster it spins:

```java
TILT.addBootsSource(player -> {
    Centrifuge c = Centrifuge.around(player);
    return c == null ? null : BootsSettings.DEFAULT.withGrip(1.0 + c.rpm() / 20.0);
});
```

When no source answers and the player turned the boots on in the menu themselves, the menu's
values are used.

To keep the boots off some blocks, add a filter. It is asked for every block the boots try to grip
on a deck; any filter that says no wins:

```java
TILT.addBootsSurfaceFilter((player, state, pos, face) -> state.is(MyTags.MAGNETIC));   // only on metal
```

`pos` is the block's position inside the deck, `face` the side being gripped. It applies to Sticky
tilt as well, since both use the same grip. Called several times a tick, so keep it to a block
check.

To pull the player off a face, for a knockback or a kick-off jump:

```java
TILT.releaseBoots();
player.push(away.x, away.y, away.z);
```

The boots let go and stay off until the player lands. Without the push they are still standing on
the face, so they land on it at once and grip again.

## From the server

The tilt is decided on the player's client, so a server mod sends it there. These calls go out as a
packet to that one player and act like a client-side source, lease or boots source of your mod:

```java
TILT.setTilt(player, 0, Tilt.of(lean), 2f);   // priority, tilt, half-life in ticks to ease towards it
TILT.setTilt(player, 0, null, 0f);            // stop
TILT.setBoots(player, BootsSettings.DEFAULT); // boots on, with these settings
TILT.setBoots(player, null);                  // boots off
TILT.setSettings(player, TiltSettings.DEFAULT.withMaxAngle(60));   // their tilt settings
TILT.setSettings(player, null);               // back to their own
TILT.suppress(player, 10_000);                // keep them upright
TILT.release(player);
```

- A server tilt outranks every client source, between server tilts the higher priority wins.
- Server settings replace the player's own and outrank every `TiltSettingsSource`, a server that
  enforces its max tilt still wins over `maxAngle`.
- Send `setTilt` again whenever the tilt changes, the client eases towards the latest one.
- Everything is dropped when the player disconnects.
- A modified client can ignore these. They are for gameplay, not anti-cheat.

## Events

```java
TILT.addListener(new TiltListener() {
    @Override
    public void tiltChanged(Player player, boolean tilted) { ... }

    @Override
    public void bootsChanged(Player player, boolean booted) { ... }

    @Override
    public void deckChanged(Player player, @Nullable UUID deck) { ... }
});
```

All methods are optional, override only the ones you need. Each fires once when its value
changes, never every frame:

| Event | Fires with `true` / an id | Fires with `false` / null |
|---|---|---|
| `tiltChanged` | the body starts leaning, from a deck, the boots or a source that moves the hitbox | the body is back upright, after easing off |
| `bootsChanged` | magnetic boots or Sticky tilt take hold of a face | they let go: a jump off, `releaseBoots`, losing the face |
| `deckChanged` | the tilt starts following a sub-level, or moves to another one | it follows none any more |

- `tilted` is the body, not the model: a model-only tilt and `suppress` do not fire it. It turns
  true past about 0.1 degrees, so a tilt easing out fires `false` only at the end.
- `deckChanged` is the same as `TiltState.deck`. It can stay set for a moment after the player
  steps off, while the tilt holds over the deck in the air.
- Where they run: on the client for the local player, checked every frame on the render thread,
  on the server for every player, checked at the end of every server tick on the server thread.
  The server sees what the client last reported, so it can be a tick or two behind. In
  singleplayer you get both, tell them apart with `player.level().isClientSide`.
- Order within one check is tilt, boots, deck. Listeners are called in registration order.
- A player who joins already tilted, or on a deck, gets the `true` events on their first check.
  A player who leaves gets no closing event, clean up on logout yourself.
- Listeners run inside the frame or tick, so keep them short and don't throw: a listener that
  throws is logged once and skipped.

```java
TILT.addListener(new TiltListener() {
    @Override
    public void bootsChanged(Player player, boolean booted) {
        if (!player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(),
                    booted ? MySounds.CLANK.get() : MySounds.UNCLANK.get(), SoundSource.PLAYERS);
        }
    }
});
```

## Sides

The local player's tilt is decided on their own client and synced from there. `suppress`,
`addTiltSource`, `addTiltSettingsSource`, `addBootsSource` and `releaseBoots` therefore act on the
client only, on a dedicated server they register and do nothing, so it is safe to call them from
common setup. The `ServerPlayer` overloads are the way to reach a client from the server. `state`,
the hitbox calls, entity sources, exclusions, gravity and listeners work on both sides.

Registering is safe from a mod constructor, which runs in parallel with other mods. A source, test
or listener that throws is logged once with your mod id and skipped.
