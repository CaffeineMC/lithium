_ReleaseTag_ is automatically replaced with the release tag, e.g. mc1.21.4-0.14.5
_MCVersion_ is automatically replaced with the minecraft version range (display), e.g. 26.1.x
_LithiumVersion_ is automatically replaced with the lithium version, e.g. 0.14.5
Everything above the line is ignored and not included in the changelog. Everything below will be in the
changelog on GitHub, Modrinth and CurseForge.
----------
Lithium _LithiumVersion_ for Minecraft _MCVersion_ adds new optimizations and improves mod compatibility.

Make sure to take a backup of your world before using the mod and please report any bugs and mod compatibility issues at the [issue tracker](https://github.com/CaffeineMC/lithium-fabric/issues). You can check the [description of each optimization](https://github.com/CaffeineMC/lithium/blob/_ReleaseTag_/lithium-mixin-config.md) and how to disable it when encountering a problem.

## Additions
- Optimize profiler access
- Optimize explosions with direct-mapped block cache
- Document non-vanilla behavior when explosions skip air
- Avoid counting skipped air when no player is within 64 blocks (= no particles spawned)
- 
## Changes
- Move explosion skipping air to separate package. 
- 
## Fixes
- Fix invalidation issue causing incompatibility with carpet TIS double barrel
- Fix block entity ticking in lazy loaded chunk with coordinates x,z = 0,0 when first ticking block entity is in that chunk
