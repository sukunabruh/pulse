# Pulse 0.2.0 (Fabric, Minecraft 1.21.11)

Client-side utility mod: module system, event bus, JSON config, panel-style click GUI, HUD, ESP, base finding and player helpers.

## Build
Requires JDK 21.

1. This zip has no Gradle wrapper (it can't be generated offline). Pick one:
   - Open the folder in IntelliJ IDEA and let it import (builds without the wrapper), or
   - With Gradle installed, run once: `gradle wrapper --gradle-version 9.2.0`, then use `./gradlew`.
     (If Loom complains about the Gradle version, use `--gradle-version latest`.)
2. `./gradlew build` -> jar in `build/libs/pulse-0.2.0.jar` (not the `-sources` one).
3. `./gradlew runClient` to test in a dev client.
4. To install: jar + Fabric API 0.141.1+1.21.11 in `.minecraft/mods`, Fabric Loader 0.18.1+.

## Build without installing anything (GitHub Actions)
1. Create a free GitHub account and a new repository, then upload the contents of this folder
   (including the hidden `.github` folder) to it.
2. Open the **Actions** tab, pick **Build Pulse**, and wait for the green check (a few minutes).
3. Open the finished run and download the **pulse-jar** artifact. Inside is `pulse-0.2.0.jar` (use that one,
   not the `-sources` jar). If the run fails, open the failed step and copy the error text.

## Controls
- **Right Shift**: click GUI. Floating draggable panels per category:
  left-click a module = toggle, right-click = expand its settings, drag sliders, click "Bind" then press a key
  (Esc while binding = unbind), mouse wheel scrolls the panel under the cursor. Esc or Right Shift closes.
- **Right Ctrl**: HUD editor (drag the watermark / coords / FPS / module list; saved in the config).
- **Theme** button (bottom-left of the click GUI) cycles accent colours, including Rainbow.
- **H**: Freecam. **J**: Block ESP. **C** (hold): Zoom. Other modules have unbound toggle keys.
- Config: `.minecraft/config/pulse.json` (saved when the GUI closes and on exit).

## Modules
**Movement**
- **Auto Sprint** - sprints while you move forward (setting: all directions).
- **Auto Walk** - holds forward for you.
- **Freecam** (H) - fly the camera through walls. WASD move, Jump up, Sneak down, Sprint = boost. v0.2: mouse look no
  longer turns your real player, clicks (mine/place/attack/pick) are swallowed while active, and it keeps moving with F1 on.
- **Freelook** (hold Left Alt, rebindable) - look around without turning your player or changing your walking direction.
  Setting "Third person" (default on) orbits the camera around you; turn it off to stay in first person.

**Player**
- **Auto Tool** - best hotbar tool while mining.
- **Auto Totem** - keeps a totem in your offhand (refills from inventory/hotbar when it is empty). Setting: delay.
- **Hover Totem** - hover a totem in any inventory screen and it jumps to your offhand (or a chosen hotbar slot).
- **Auto Inv Totem** - while your inventory is open, fills the offhand (and an optional hotbar slot) with totems.
- **Auto Eat** - eats from your hotbar at the chosen hunger level, then switches back. Won't eat rotten flesh, spider eyes,
  pufferfish, poisonous potatoes, chorus fruit or raw chicken; golden apples are off by default.
  It holds the use key, so don't enable it while looking at a chest/door.
- **Auto TPA** - sees "wants to teleport to you"-style messages and sends the accept command (default `tpaccept`).
  "Whitelist only" is ON by default: put names in "Names" (comma-separated, no spaces) or it accepts nobody.
- **Fast Place** - no delay between placing blocks (and XP bottles / end crystals).

**Render**
- Fullbright, Zoom, HUD, **Fake Stats** (now with X/Y position sliders and a custom text line; local overlay only).

**ESP**
- **Block ESP** - any blocks through walls; "Pick blocks" opens a searchable list.
- **Entity ESP** - players, mobs, items, frames/stands.
- **Player ESP** - boxes, name, health + health bar, distance, held item, totem marker [T], tracers, optional chat alert
  when someone enters a range.
- **Storage ESP** - chests, trapped chests, ender chests, barrels, shulkers, hoppers, dispensers/droppers (furnaces optional),
  coloured by type, with a one-line count summary. Reaches your whole render distance.
- **Spawner Flagger** - boxes spawners, chat alert, saves a waypoint, warns about spawner clusters, remembers spawners
  after the chunk unloads.
- **Sus Chunk Finder** - chunks packed with storage blocks / spawners.
- **Base Finder** - see below.
- **Waypoints** - `/pulse wp add <name>` (or `add <name> <x> <y> <z>`), `remove <name>`, `list`, `clear`.

## Base Finder v2
Scores every loaded chunk for player-made blocks (redstone, storage, lights, building blocks, crafting stations, signs,
beds, banners) plus item frames and armor stands. Chunks that mix several categories score higher, and chunks full of
generated-structure blocks (strongholds, villages, monuments, ancient cities, trial chambers...) are discounted.
- Flagged chunks are **remembered** after they unload and saved per server+dimension in `config/pulse-bases.json`.
- Neighbouring flagged chunks merge into **base clusters** (list on the right: x, z, score, distance, compass direction).
- Boxes around clusters, **edge arrows** pointing at off-screen ones, optional tracers.
- Chat alert once per new base, and **auto waypoint** (`Base-<cx>_<cz>`).
- Commands: `/pulse base list`, `/pulse base wp` (waypoints for all), `/pulse base ignore` (hide the nearest one, e.g. a
  village or your own base), `/pulse base clear`.
- Settings: Range (up to 32 chunks, but you only see what the server sends), Chunk min score, Base min score, List size,
  Sort by distance, Boxes, Edge arrows, Tracers, Chat alert, Auto waypoint, Remember bases.
- It is still a heuristic: big generated structures and shared builds can score. Raise "Base min score" for fewer hits.

## Base-hunting workflow
1. Turn on **Base Finder**, **Storage ESP**, **Player ESP** and **Spawner Flagger**, then fly/elytra around.
2. Follow the edge arrows / list toward the highest scores; `/pulse base ignore` things you've already checked.
3. Use **Freecam** to scout without moving, **Freelook** to look around while flying.
4. Everything found is saved as waypoints; `/pulse wp list` shows them.

## Known limitations
- ESP is a 2D screen overlay (first person only, so it hides during Freelook's third-person view).
- Freelook/Freecam use a camera-entity trick instead of mixins; your own model can wobble slightly while looking around.
- Auto Totem / Hover Totem / Auto Inv Totem send inventory clicks the same way the vanilla UI does; some servers' anti-cheat
  dislikes inventory actions done outside the inventory screen.
- Auto TPA matches message text, so it depends on the server's wording (adjust nothing - just whitelist names).
- Not included: true 3D ESP outlines.

## If it doesn't compile
Minecraft 1.21.11 is Yarn-mapped but names shift between builds. Paste the compiler errors back and
they'll be fixed. Versions are in `gradle.properties`.
