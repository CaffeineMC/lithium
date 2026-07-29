_ReleaseTag_ is automatically replaced with the release tag, e.g. mc1.21.4-0.14.5
_MCVersion_ is automatically replaced with the minecraft version range (display), e.g. 26.1.x
_LithiumVersion_ is automatically replaced with the lithium version, e.g. 0.14.5
Everything above the line is ignored and not included in the changelog. Everything below will be in the
changelog on GitHub, Modrinth and CurseForge.
----------
Lithium _LithiumVersion_ for Minecraft _MCVersion_ backports all changes made since the release of Minecraft 26.2.

Make sure to take a backup of your world before using the mod and please report any bugs and mod compatibility issues at the [issue tracker](https://github.com/CaffeineMC/lithium-fabric/issues). You can check the [description of each optimization](https://github.com/CaffeineMC/lithium/blob/_ReleaseTag_/lithium-mixin-config.md) and how to disable it when encountering a problem.

## Additions
- Reduce allocations in entity explosion exposure raycasts
- Use caching in entity explosion exposure block access
- Add isDescending to lazy shape context optimization

## Changes
- Include tag-playing villager baby sensor in adult sensor optimization

## Fixes
- Fix explosion no air counting optimization breaking visual explosion size since Lithium 0.24.5
- Fix supporting block movement shortcut not offsetting VoxelShape
- Avoid crashing when modded levels with broken registries are created
- Fix adult sensor optimization only applying to animals that grew up since last reload

- Fix various minor non-vanilla behaviors of VoxelShape optimizations
- Fix goat jump target choice optimization possibly breaking and crashing (Thanks to gurrveerrrr)
- Fix collision with hard EnderDragon, EnderDragonPart and NeoForge PartEntity boxes not being possible
- Fix collision with modded spectator EnderDragonPart being possible
- Fix EntityClassGroup entries missing when multiple theoretical entity types per entity class are decided using entity type parameter
- Fix some startup crashes with other mods by using conformVisibility for mixins
