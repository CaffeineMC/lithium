_ReleaseTag_ is automatically replaced with the release tag, e.g. mc1.21.4-0.14.5
_MCVersion_ is automatically replaced with the minecraft version range (display), e.g. 26.1.x
_LithiumVersion_ is automatically replaced with the lithium version, e.g. 0.14.5
Everything above the line is ignored and not included in the changelog. Everything below will be in the
changelog on GitHub, Modrinth and CurseForge.
----------
Lithium _LithiumVersion_ for Minecraft _MCVersion_ fixes a startup issue.

Make sure to take a backup of your world before using the mod and please report any bugs and mod compatibility issues at the [issue tracker](https://github.com/CaffeineMC/lithium-fabric/issues). You can check the [description of each optimization](https://github.com/CaffeineMC/lithium/blob/_ReleaseTag_/lithium-mixin-config.md) and how to disable it when encountering a problem.

## Additions
- Client side tick optimizations (previously experimental), including optimized biome particle spawning

## Fixes
- Fix comparator updates missing for furnaces and brewing stands (since 24w10a / 1.20.5)
- Fix mistake in projectile entity optimization causing looking-at predicates in commands failing

## Changes
- Make random_block_ticking and serialization optimizations work together
- LithiumBlockCounter as interface instead of class for mod compatibility
