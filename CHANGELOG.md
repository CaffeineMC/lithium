_ReleaseTag_ is automatically replaced with the release tag, e.g. mc1.21.4-0.14.5
_MCVersion_ is automatically replaced with the minecraft version, e.g. 1.21.4
_LithiumVersion_ is automatically replaced with the lithium version, e.g. 0.14.5
Everything above the line is ignored and not included in the changelog. Everything below will be in the
changelog on GitHub, Modrinth and CurseForge.
----------
Lithium _LithiumVersion_ for Minecraft _MCVersion_ includes two minor optimizations and a mod compatibility improvement.

Make sure to take a backup of your world before using the mod and please report any bugs and mod compatibility issues at the [issue tracker](https://github.com/CaffeineMC/lithium-fabric/issues). You can check the [description of each optimization](https://github.com/CaffeineMC/lithium/blob/_ReleaseTag_/lithium-mixin-config.md) and how to disable it when encountering a problem.

## Additions
- Reintroduce expiring chunk ticket optimization
- Chest animation sleeping optimization

## Changes
- Remove ponder mod compatibility workaround due to fix on their side

## Fixes
- Fix wrong position used in fluid flow calculations causing mod compatibility issues
