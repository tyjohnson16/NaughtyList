# Changelog

All notable changes to NaughtyList are documented here. The plugin version is set in `gradle.properties`.

## 1.0.3

- Coal presents generated before XmasWorld 1.3.0 are recognised even when a Christmas tree's leaves, ornaments or lights replace a couple of the gift box's wool or concrete blocks (some presents under trees in older worlds were missed).
- Surface presents under trees are no longer mistaken for cave presents.
- In creative or spectator mode, a trapped present now opens normally (with an action-bar note that it is armed) instead of silently refusing to open; it stays armed for survival players.
- New `/naughty present` (look at a present) explains whether it is a trap and why, and `/naughty present scan [radius]` counts presents and traps nearby.

## 1.0.2

- `/naughty spawn snowman` and `/naughty spawn abominable` no longer melt straight away in daytime. Command-spawned snowmen stay day and night (even in creative) and melt only in a safe zone or when no player at all is within 96 blocks. Natural snowmen still melt at sunrise.
- The Abominable's giant size is re-applied after it spawns and every half second.
- A refused spawn now says why (world not enabled, safe zone, or WorldGuard mob-spawning deny).

## 1.0.1

- `/naughty blizzard start` no longer changes the world time (1.0.0 set it to night). A blizzard started in the daytime runs until the next sunrise (at least one minute).
- `/naughty status` shows the real plugin version.

## 1.0.0

- First release: evil snowmen, the Abominable Snowman, blizzards, Grinch goblins, coal presents, nutcracker soldiers, and safe zones.
