# Pulse (Fabric, Minecraft 1.21.11)

Client-side utility mod: module system, event bus, JSON config, panel-style click GUI, HUD and modules.

## Build
Requires JDK 21.

1. This zip has no Gradle wrapper (it can't be generated offline). Pick one:
   - Open the folder in IntelliJ IDEA and let it import (builds without the wrapper), or
   - With Gradle installed, run once: `gradle wrapper --gradle-version 9.2.0`, then use `./gradlew`.
     (If Loom complains about the Gradle version, use `--gradle-version latest`.)
2. `./gradlew build` -> jar in `build/libs/pulse-0.1.0.jar` (not the `-sources` one).
3. `./gradlew runClient` to test in a dev client.
4. To install: jar + Fabric API 0.141.1+1.21.11 in `.minecraft/mods`, Fabric Loader 0.18.1+.

## Build without installing anything (GitHub Actions)
1. Create a free GitHub account and a new repository, then upload the contents of this folder
   (including the hidden `.github` folder) to it.
2. Open the **Actions** tab, pick **Build Pulse**, and wait for the green check (a few minutes).
3. Open the finished run and download the **pulse-jar** artifact. Inside is `pulse-0.1.0.jar` (use that one,
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
- **Freecam** - fly the camera through walls. WASD move, Jump up, Sneak down, Sprint = boost.
  Sliders: Speed (blocks/sec), Sprint boost. Never auto-starts.
- **Auto Tool** - switches to the fastest hotbar tool when you mine.
- **Block ESP** - highlights blocks through walls (2D screen overlay). At the top of its settings, click
  **Pick blocks** to open a searchable list of every block in the game (with icons) and tick exactly the blocks you want;
  **Pick color** sets their color. Below that are quick toggles for Diamond, Debris, Chests, Spawners, Emerald, Gold,
  Iron, Redstone, Lapis, Copper, Coal, Quartz, Shulkers, Hoppers, Utility, Beds and Signs, plus Range (chunks),
  Fill %, Max boxes, Labels and Tracers.
- **Entity ESP** - boxes + name/health/distance for players, hostile mobs, other mobs and items (toggle each, range, fill).
- **Spawner Finder** - boxes mob spawners through walls and posts a chat alert once per new spawner.
- **Sus Chunk Finder** - lists the chunks with the most storage blocks / spawners (block coordinates on the right side of the screen).
- **Base Finder** - scores loaded chunks by player-made blocks (hoppers, droppers, observers, redstone parts, glass,
  concrete, torches, crafting stations, signs, storage) and lists/boxes the best ones. Heuristic: villages and other
  generated structures can score too. Settings: Range, Min score, List size, Chunk boxes, Tracers, Chat alert.
- **Waypoints** - save found bases and see them through walls. `/pulse wp add <name>` (or `add <name> <x> <y> <z>`),
  `/pulse wp remove <name>`, `/pulse wp list`, `/pulse wp clear`. Stored per server and dimension in `config/pulse-waypoints.json`.
- **Fake Stats** - a stats panel with values you pick (FPS, ping, CPS, kills, deaths, K/D).
  Local overlay only; it doesn't change anything real or send anything to a server.
- Sprint, Auto Walk, Fullbright, Zoom, HUD.

## Base-hunting workflow
1. Turn on **Sus Chunk Finder** + **Base Finder** and fly/elytra around - flagged chunks show up in the right-hand lists and as boxes.
2. **Block ESP**: enable Chests, Shulkers, Hoppers, Utility, Beds, Signs (and Spawners) for close-range confirmation; Tracers help you home in.
3. **Entity ESP** with "Frames/stands" shows item frames and armor stands (often marks storage rooms).
4. **Spawner Finder** alerts on spawners; **Freecam** lets you scout without moving.
5. Save what you find with `/pulse wp add <name>`.

## Known limitations
- Freecam: movement runs from the HUD callback, so it pauses while F1 is on. Mouse look also turns your
  real player. Interacting with blocks while in freecam is unreliable (crosshair ray starts at the camera).
- Block ESP: first-person only; boxes can drift slightly at screen edges while sprinting (FOV effect) -
  set "FOV Effects" to 0 in Video settings to remove that.
- Not included: Fast Place (needs a mixin), true 3D ESP outlines. Combat automation and anti-cheat bypasses are intentionally not part of Pulse.

## If it doesn't compile
Minecraft 1.21.11 is Yarn-mapped but names shift between builds. Paste the compiler errors back and
they'll be fixed. Versions are in `gradle.properties`.
