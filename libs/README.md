# Compile-only integration jars

NaughtyList talks to XmasWorld, WorldGuard and GriefPrevention only when those plugins are installed on the server. At compile time it needs their APIs.

* **`XmasWorld-api.jar` (committed)** — the XmasWorld 1.3.x plugin jar, used as a compile-only API (`XmasWorldAPI`).
* **WorldGuard, FastAsyncWorldEdit (WorldEdit `BukkitAdapter`) and GriefPrevention** are not committed (they are third-party plugins). When those jars are absent, the build uses the compile-only stubs in `stubs/`. The stubs are not packaged into the NaughtyList jar; at runtime the real plugins are used.

To compile against the real APIs instead of the stubs, copy these files from your server into this folder:

* `worldguard-bukkit-7.0.17.jar`
* `FastAsyncWorldEdit-Paper-2.15.3.jar`
* `GriefPrevention.jar`

`./gradlew build` works with or without them.
