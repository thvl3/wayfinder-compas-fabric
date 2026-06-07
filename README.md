# Overview

This project is a **Minecraft mod for the Fabric mod loader (Minecraft 1.21.1)**
written in Java. It adds a single item: the **Wayfinder Compass**. Unlike a normal
compass that only spins a needle, the Wayfinder Compass renders an animated "fairy
trail" of glowing motes that hugs the terrain from the player toward a waypoint they
set — draping over hills and dips and shimmering with floating particles — showing the
actual direction and distance to travel.

My purpose in writing this software was to learn the Java language by building
something non-trivial and interactive. Modding Minecraft forces you to use real
object-oriented Java — registering objects, subclassing engine classes, handling
events, working with collections, and reading/writing files — inside a large existing
codebase, which is a realistic way to practice the language.

**How it works in-game:**

- Obtain the **Wayfinder Compass** from the *Tools & Utilities* creative tab, or craft
  it with a vanilla **Compass + Eye of Ender**.
- **Right-click** while holding it: sets a waypoint on the block you are looking at
  (within 128 blocks), or at your own feet if you are not aiming at anything.
- **Sneak + right-click**: clears the active waypoint.
- **Press `N`**: cycles through every waypoint you have saved, even ones from a
  previous play session (they are stored in a file).
- While holding the compass, an **A\* path** is computed across the terrain surface
  (routing around walls and cliffs and stepping up/down like a player walks). The path
  is computed fresh each time a surge ignites — so it always starts from where you are
  standing — and stays frozen for the whole surge: it never re-routes until the surge
  reaches the waypoint or falls out of your view.
- The path itself is **invisible**. Every few seconds a **surge** of light ignites at
  the player, races along the hidden track toward the waypoint as a cluster of glowing
  sparks, leaves a brief fading wake, bursts on arrival, and then goes dark until the
  next surge. A faint marker on the destination keeps the endpoint locatable; it shows
  through walls, while the surge sparks sit on the terrain.

[Software Demo Video]([https://www.youtube.com/](https://youtu.be/tQrq1y84Gz8)) <!-- TODO: replace with your recorded demo link -->

# Development Environment

- **OS:** Linux (Arch)
- **Editor/IDE:** Visual Studio Code (any IDE with Java support works; IntelliJ IDEA
  Community is also a good choice for Fabric mods)
- **Build tool:** Gradle 8.8 (via the included Gradle wrapper) with the
  **Fabric Loom 1.7** plugin
- **JDK:** OpenJDK 21 (required by Minecraft 1.21.1)

**Language and libraries:**

- **Java 21**
- **Fabric Loader** `0.16.10` and **Fabric API** `0.116.12+1.21.1` — the modding
  toolchain and hooks
- **Yarn mappings** `1.21.1+build.3` — human-readable names for Minecraft's code
- The **Java Collections Framework** (`LinkedHashMap`, `ArrayList`) and `java.nio.file`
  for file I/O — both from the standard library

**Building and running:**

```bash
# Compile and package the mod (produces build/libs/wayfinder-compass-1.0.0.jar)
./gradlew build

# Launch a development Minecraft client with the mod loaded
./gradlew runClient
```

To use the built mod in a normal Minecraft install, drop the jar from `build/libs/`
into your `.minecraft/mods` folder alongside the Fabric API jar (Fabric Loader for
1.21.1 must be installed).

# Project Structure

| File | Role |
| --- | --- |
| `src/main/java/.../WayfinderMod.java` | Common entry point; registers the item |
| `src/main/java/.../Waypoint.java` | Plain data class + file serialization helpers |
| `src/main/java/.../item/WayfinderCompassItem.java` | The compass item (tooltip) |
| `src/client/java/.../client/WayfinderClient.java` | Client setup: input, keybind, events |
| `src/client/java/.../client/WaypointManager.java` | `HashMap` of waypoints + file read/write |
| `src/client/java/.../client/SurfacePathfinder.java` | A* search across the terrain surface |
| `src/client/java/.../client/TrailController.java` | Path recompute timer + 2-second sparks |
| `src/client/java/.../client/WayfinderRenderer.java` | Draws the pulsing trail, sparks, marker |

**Where the required concepts live (for grading):**

- **Variables / expressions / conditionals / loops** — throughout, e.g. the dot loop
  and color math in `WayfinderRenderer.render` and the parsing in `WaypointManager.load`.
- **Functions** — every method, e.g. `Waypoint.fromFileLine`, `resolveTarget`.
- **Classes** — `Waypoint`, `WaypointManager`, `WayfinderCompassItem`, etc.
- **Java Collection Framework** — `LinkedHashMap<String, Waypoint>` and `ArrayList`
  in `WaypointManager`.
- **Stretch challenge — read and write to a file** — `WaypointManager.save()` /
  `load()` persist waypoints to `config/wayfinder_waypoints.txt`.

# Useful Websites

- [Fabric Documentation](https://docs.fabricmc.net/)
- [Fabric Develop (version numbers)](https://fabricmc.net/develop)
- [Yarn 1.21.1 API Javadocs](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/)
- [Java Tutorial — W3Schools](https://www.w3schools.com/java/)
- [Java Collections Framework — BeginnersBook](https://beginnersbook.com/2017/06/java-collections-framework/)

# Future Work

- Run the A* search off the main thread (currently it runs on the client tick every
  ~10s, which is fine for short paths but could hitch for very long ones).
- Raise the pathfinder's range/node caps and add a fallback when the waypoint is past
  the search budget, so very distant waypoints still get a full route.
- Render the markers as directional arrows/chevrons that point along the path instead
  of flat diamonds.
- Add an on-screen distance readout and a HUD edge indicator when the waypoint is off
  to the side.
- Let the player name waypoints and pick a color when setting them.
- Sync waypoints across server/client so the mod works in true multiplayer (currently
  the waypoint state is tracked client-side, which is aimed at single-player).
- Add a config screen instead of editing the text file by hand.
# wayfinder-compas-fabric
