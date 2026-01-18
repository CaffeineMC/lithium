# Lithium Configuration File Summary
The configuration file makes use of the [Java properties format](https://docs.oracle.com/cd/E23095_01/Platform.93/ATGProgGuide/html/s0204propertiesfileformat01.html). If the configuration file does not exist during game start-up, a blank file with a comment will be created.

The configuration file defines *overrides* for the available options, and as such, a blank file is perfectly normal! It simply means that you'd like to use all the default values.

Each category below includes a list of options which can be changed by the user. Due to the nature of the mod, configuration options require a game restart to take effect.

### Editing the configuration file

Before editing the configuration file, take a backup of your minecraft worlds!
All configuration options are simple key-value pairs. In other words, you first specify the option's name, followed by the desired value, like so:

```properties
mixin.ai.pathing=false
mixin.gen.biome_noise_cache=false
```

# Configuration options
### `mixin.ai`
(default: `true`)  
Mob AI optimizations
  
### `mixin.ai.non_poi_block_search`
(default: `true`)  
Optimizes Non-POI block search using maybeHas to return early or reduce block searches
  
### `mixin.ai.pathing`
(default: `false`)  
A faster code path is used for determining what kind of path-finding node type is associated with a
given block. Additionally, a faster chunk cache will be used for accessing blocks while evaluating
paths.

Requirements:
- `mixin.util.chunk_access=true`  
  
### `mixin.ai.poi`
(default: `true`)  
Implements a faster POI search
  
### `mixin.ai.poi.fast_portals`
(default: `true`)  
Portal search uses the faster POI search and optimized loaded state caching
  
### `mixin.ai.poi.tasks`
(default: `true`)  
Mob Tasks which search for POIs use the optimized POI search
  
### `mixin.ai.raid`
(default: `true`)  
Avoids unnecessary raid bar updates and optimizes expensive leader banner operations
Requirements:
- `mixin.util.data_storage=true`  
  
### `mixin.ai.sensor`
(default: `true`)  
Brain sensor optimizations
  
### `mixin.ai.sensor.secondary_poi`
(default: `true`)  
Avoid unnecessary secondary POI searches of non-farmer villagers
  
### `mixin.ai.task`
(default: `true`)  
Various AI task optimizations
  
### `mixin.ai.task.launch`
(default: `true`)  
Keep track of running and runnable tasks to speed up task launching checks
  
### `mixin.ai.task.memory_change_counting`
(default: `true`)  
Keep track of AI memory changes to skip checking AI task memory prerequisites
  
### `mixin.ai.task.replace_streams`
(default: `true`)  
Replace Stream code of AI tasks with more traditional iteration.
  
### `mixin.ai.task.run`
(default: `true`)  
Various optimizations inside AI tasks
  
### `mixin.ai.task.run.long_jump_weighted_choice`
(default: `true`)  
Speed up the weighted random choice of long jump target positions.
  
### `mixin.ai.useless_sensors`
(default: `true`)  
Disabling useless brain sensors to avoid useless sensing calculations.
  
### `mixin.ai.useless_sensors.goat_item_sensor`
(default: `true`)  
Disable the goat item sensor whose memories are never used.
  
### `mixin.ai.useless_sensors.parent_animal_sensor`
(default: `true`)  
Disable the parent animal sensor when an animal is not a baby. Would differ from vanilla in the case where an adult animal turns back into a baby animal, as the sensor information is refreshed, leading to a less-outdated value in the first second of turning back into a baby animal. However, there is no way to turn an animal back into a baby without reinitializing the brain, creating entirely new sensors.
  
### `mixin.alloc`
(default: `true`)  
Patches that reduce memory allocations
  
### `mixin.alloc.chunk_random`
(default: `true`)  
Random block ticking uses fewer block position allocations, thereby reducing the object allocation rate.
  
### `mixin.alloc.chunk_ticking`
(default: `true`)  
Reuse large chunk lists
  
### `mixin.alloc.composter`
(default: `true`)  
Composters will reuse the available slot arrays that are requested by hoppers
  
### `mixin.alloc.deep_passengers`
(default: `true`)  
Reduce stream code usage when getting the passengers of an entity
  
### `mixin.alloc.entity_iteration`
(default: `true`)  
Avoid unnecessary indirection when iterating entities of an entity section.
  
### `mixin.alloc.entity_tracker`
(default: `true`)  
Entity trackers use a fastutil set for storing players instead of an IdentityHashSet
  
### `mixin.alloc.enum_values`
(default: `true`)  
Avoid `Enum#values()` array copy in frequently called code
  
### `mixin.alloc.enum_values.piston_block`
(default: `true`)  
Avoid `Enum#values()` array copy in frequently called code
  
### `mixin.alloc.enum_values.piston_handler`
(default: `true`)  
Avoid `Enum#values()` array copy in frequently called code
  
### `mixin.alloc.enum_values.redstone_wire`
(default: `true`)  
Avoid `Enum#values()` array copy in frequently called code
  
### `mixin.alloc.explosion_behavior`
(default: `true`)  
Remove lambda allocation in frequently called block blast resistance calculation in explosion code
  
### `mixin.alloc.nbt`
(default: `true`)  
NBT tags use a fastutil hashmap instead of a standard HashMap
  
### `mixin.block`
(default: `true`)  
Optimizations related to blocks
  
### `mixin.block.flatten_states`
(default: `true`)  
FluidStates store directly whether they are empty
  
### `mixin.block.fluid`
(default: `true`)  
Fluid optimizations
  
### `mixin.block.fluid.flow`
(default: `true`)  
Fluid flow optimization
  
### `mixin.block.hopper`
(default: `true`)  
Reduces hopper lag using caching, notification systems and BlockEntity sleeping
Requirements:
- `mixin.util.entity_movement_tracking=true`
- `mixin.util.block_entity_retrieval=true`
- `mixin.util.inventory_change_listening=true`
- `mixin.util.item_component_and_count_tracking=true`  
  
### `mixin.block.moving_block_shapes`
(default: `true`)  
Moving blocks and retracting pistons avoid calculating their VoxelShapes by reusing previously created VoxelShapes.
  
### `mixin.block.redstone_wire`
(default: `true`)  
Redstone wire power calculations avoid duplicate block accesses
  
### `mixin.block_pattern_matching`
(default: `true`)  
Fast-path exit end portal search by counting nearby bedrock blocks. Reduces lag when placing the last end crystal when respawning the ender dragon.
  
### `mixin.cached_hashcode`
(default: `true`)  
BlockNeighborGroups used in fluid code cache their hashcode
  
### `mixin.chunk`
(default: `true`)  
Various world chunk optimizations
  
### `mixin.chunk.entity_class_groups`
(default: `true`)  
Allow grouping entity classes for faster entity access, e.g. boats and shulkers
Requirements:
- `mixin.util.accessors=true`  
  
### `mixin.chunk.no_locking`
(default: `true`)  
Remove debug checks in block access code
  
### `mixin.chunk.no_validation`
(default: `true`)  
Skip bounds validation when accessing blocks
  
### `mixin.chunk.palette`
(default: `true`)  
Replaces the vanilla hash palette with an optimized variant
  
### `mixin.chunk.serialization`
(default: `true`)  
Optimizes chunk palette compaction when serializing chunks
  
### `mixin.collections`
(default: `true`)  
Various collection optimizations
  
### `mixin.collections.attributes`
(default: `true`)  
Uses fastutil hashmaps for entity attributes
  
### `mixin.collections.block_entity_tickers`
(default: `true`)  
Uses fastutil hashmaps for BlockEntity tickers
  
### `mixin.collections.brain`
(default: `true`)  
Uses fastutil hashmaps for AI memories and sensors
  
### `mixin.collections.chunk_tickets`
(default: `true`)  
Improves the chunk ticket sets by speeding up the removal of chunk tickets
  
### `mixin.collections.entity_by_type`
(default: `true`)  
Uses fastutil hashmaps for type specific entity lists
  
### `mixin.collections.entity_filtering`
(default: `true`)  
The expensive check to see if a TypeFilterableList can be filtered by a specific class is only made when a new list for that type needs to be created
  
### `mixin.collections.entity_ticking`
(default: `true`)  
Copy entity hashmap instead of duplicating the list using iteration
  
### `mixin.collections.fluid_submersion`
(default: `true`)  
Use ReferenceArraySet instead of HashSet to store the fluids the entity is currently submerged in.
  
### `mixin.collections.gamerules`
(default: `true`)  
Uses fastutil hashmaps for gamerules
  
### `mixin.collections.goals`
(default: `true`)  
Uses fastutil hashsets for goals in the AI goal selector
  
### `mixin.collections.mob_spawning`
(default: `true`)  
Uses custom hashset/list combination for faster mob spawn checks
  
### `mixin.collections.poi_types`
(default: `true`)  
Uses fastutil hashmap for POI Types
  
### `mixin.compat.worldedit`
(default: `false`)  
Send updates to hoppers when adding inventory block entities to chunks when world edit is loaded. Fixes the issue of hoppers not noticing when inventories are placed using worldedit without any block updates. Enabled automatically when worldedit is present.
Requirements:
- `mixin.util.block_entity_retrieval=true`  
  
### `mixin.debug`
(default: `false`)  
Debug features
  
### `mixin.debug.palette`
(default: `true`)  
Clients check the chunk section data when receiving a chunk data packet.
  
### `mixin.entity`
(default: `true`)  
Various entity optimizations
  
### `mixin.entity.collisions`
(default: `true`)  
Various entity collision optimizations
  
### `mixin.entity.collisions.fluid`
(default: `true`)  
Skips being pushed by fluids when the nearby chunk sections do not contain this fluid
Requirements:
- `mixin.util.block_tracking=true`
- `mixin.experimental.entity.block_caching.fluid_pushing=false`  
  
### `mixin.entity.collisions.intersection`
(default: `true`)  
Uses faster block access for block collisions and delayed entity access with grouped boat/shulker for entity collisions when available
Requirements:
- `mixin.util.chunk_access=true`  
  
### `mixin.entity.collisions.movement`
(default: `true`)  
Entity movement uses optimized block access and optimized and delayed entity access. Additionally, the supporting block of entities that only move downwards is checked first. This can profit from mixin.experimental.entity.block_caching.block_support, but it is not required.
Requirements:
- `mixin.util.chunk_access=true`  
  
### `mixin.entity.collisions.unpushable_cramming`
(default: `true`)  
In chunks with many mobs in ladders a separate list of pushable entities for cramming tests is used
Requirements:
- `mixin.chunk.entity_class_groups=true`  
  
### `mixin.entity.equipment_tracking`
(default: `true`)  
Skips repeated checks whether the equipment of an entity changed. Equipment updates are detected instead.
Requirements:
- `mixin.util.item_component_and_count_tracking=true`  
  
### `mixin.entity.equipment_tracking.enchantment_ticking`
(default: `true`)  
Use equipment tracking to skip ticking enchantments (Soul speed) when no such enchantments are present on the equipment of a living entity.
  
### `mixin.entity.equipment_tracking.equipment_changes`
(default: `true`)  
Skips repeated checks whether the equipment of an entity changed.
  
### `mixin.entity.fast_elytra_check`
(default: `true`)  
Skip repeatedly writing to the data tracker that an entity is not flying
  
### `mixin.entity.fast_hand_swing`
(default: `true`)  
Skip hand swinging speed and animation calculations when the hand of an entity is not swinging
  
### `mixin.entity.fast_powder_snow_check`
(default: `true`)  
Skip checking whether an entity is inside powder snow for movement speed slowdown when it is not freezing
  
### `mixin.entity.fast_retrieval`
(default: `true`)  
Access entities faster when accessing a relatively small number of entity sections
  
### `mixin.entity.inactive_navigations`
(default: `true`)  
Block updates skip notifying mobs that won't react to the block update anyways
Requirements:
- `mixin.util.data_storage=true`  
  
### `mixin.entity.replace_entitytype_predicates`
(default: `true`)  
Accesses entities of the correct type directly instead of accessing all nearby entities and filtering them afterwards
  
### `mixin.entity.sprinting_particles`
(default: `true`)  
Skips trying to create sprinting particles for all entities on the server side.
  
### `mixin.experimental`
(default: `false`)  
Various experimental optimizations
  
### `mixin.experimental.entity`
(default: `true`)  
Experimental entity optimizations
  
### `mixin.experimental.entity.block_caching`
(default: `true`)  
Use block listening system to allow skipping stuff in entity code
Requirements:
- `mixin.util.block_tracking=true`  
  
### `mixin.experimental.entity.block_caching.block_support`
(default: `true`)  
Use the block listening system to skip supporting block search (used for honey block pushing, velocity modifiers like soulsand, etc)
Requirements:
- `mixin.util.block_tracking=true`  
  
### `mixin.experimental.entity.block_caching.block_touching`
(default: `true`)  
Use the block listening system to skip block touching (like cactus touching).
Requirements:
- `mixin.util.block_tracking=true`  
  
### `mixin.experimental.entity.block_caching.fire_lava_touching`
(default: `true`)  
Skip searching for fire or lava in the burn time countdown logic when they are not on fire and the result does not make a difference. Also use the block listening system to cache whether the entity is touching fire or lava.
  
### `mixin.experimental.entity.block_caching.fluid_pushing`
(default: `true`)  
Use the block listening system to cache entity fluid interaction when not touching fluid currents.
Requirements:
- `mixin.util.block_tracking=true`  
  
### `mixin.experimental.entity.block_caching.suffocation`
(default: `true`)  
Use the block listening system to cache the entity suffocation check.
Requirements:
- `mixin.util.block_tracking=true`  
  
### `mixin.experimental.entity.item_entity_merging`
(default: `true`)  
Optimize item entity merging by categorizing item entities by item type and only attempting to merge with the same type. Categorizing by stack size allows skipping merge attempts of full item entities or two more than half full item entities.
Requirements:
- `mixin.util.accessors=true`
- `mixin.util.entity_collection_replacement=true`
- `mixin.util.item_component_and_count_tracking=true`  
  
### `mixin.gen`
(default: `true`)  
Various world generation optimizations
  
### `mixin.gen.cached_generator_settings`
(default: `false`)  
World generator settings cache the sea level. Disabled by default due to startup crash.
  
### `mixin.math`
(default: `true`)  
Various math optimizations
  
### `mixin.math.fast_blockpos`
(default: `true`)  
Avoids indirection and inlines several functions
  
### `mixin.math.fast_util`
(default: `true`)  
Avoid indirection and inline several functions in Direction, Axis and Box code
  
### `mixin.math.sine_lut`
(default: `true`)  
Reduces the sine table size to reduce memory usage and increase access speed
  
### `mixin.minimal_nonvanilla`
(default: `true`)  
Optimizations that technically deviate from vanilla behavior, but must not affect gameplay or contraptions. Each optimization includes a description of the differences to vanilla behavior. In case any of these optimizations breaks any of your contraptions or affects your gameplay, please report it to our issue tracker as we consider this to be a bug.
  
### `mixin.minimal_nonvanilla.ai`
(default: `true`)  
Mob AI optimizations
  
### `mixin.minimal_nonvanilla.ai.sensor`
(default: `true`)  
Brain sensor optimizations
  
### `mixin.minimal_nonvanilla.ai.sensor.frog_attackables`
(default: `true`)  
Speed up frog attackable sensor by checking entity type before visibility test. This is slightly non-vanilla because the visibility information is cached for up to a second. If this sensor does not compute the visibility test, a later access might compute the visibility instead. That can cause a different result, since the later computation leads to a more updated result.
  
### `mixin.minimal_nonvanilla.collisions.empty_space`
(default: `true`)  
Speed up finding empty spaces mobs fit into. This speeds up entity pose checks and nether portal positioning for colliding mobs (This code is vanilla's nether portal horse suffocation fix). If certain block collision surfaces have coordinates that are different but within 1e-7 of each other, this optimization may cause entities coming from nether portals or changing pose to be placed in a different position or pose than vanilla. This effect only occurs when the decision whether the entity fits into a space depends on a difference in the magnitude of 1e-7 blocks.
  
### `mixin.minimal_nonvanilla.spawning`
(default: `true`)  
Optimizations to spawning conditions. Reorders the iteration over entities to match the chunks and chunk sections, reducing the number of cache misses. Might differ slightly from vanilla due to floating point associativity differences when summing the spawning potential of density controlled spawns, e.g. skeleton, ghast, enderman and strider spawns in certain nether biomes.
  
### `mixin.minimal_nonvanilla.world.block_entity_ticking.support_cache`
(default: `true`)  
BlockEntity ticking caches whether the BlockEntity can exist in the BlockState at the same location. This deviates from vanilla in the case of placing a hopper in a powered location, immediately updating the cached BlockState (which is incorrect in vanilla). This most likely does not affect your gameplay, as this deviation only affects hoppers, and in vanilla, hoppers never use the cached state information anyway.
Requirements:
- `mixin.world.block_entity_ticking=true`  
  
### `mixin.minimal_nonvanilla.world.expiring_chunk_tickets`
(default: `false`)  
Only check positions with expiring tickets during ticket expiration. Can cause reordering of chunks unloading. The chunk unloading order in vanilla is predictable, but depends on the hash of the chunk position of the tickets and the hashes of the other chunk tickets, and the order of creation of the chunk tickets when hash collisions occur. No known contraptions depend on the unload order.
  
### `mixin.profiler`
(default: `true`)  
Avoid indirection when accessing the profiler
  
### `mixin.shapes`
(default: `true`)  
Various VoxelShape optimizations
  
### `mixin.shapes.blockstate_cache`
(default: `true`)  
Use a faster collection for the full cube test cache
  
### `mixin.shapes.lazy_shape_context`
(default: `true`)  
Entity shape contexts initialize rarely used fields only on first use
  
### `mixin.shapes.optimized_matching`
(default: `true`)  
VoxelShape collisions use a faster intersection test for cuboid shapes
  
### `mixin.shapes.precompute_shape_arrays`
(default: `true`)  
VoxelShapes store position arrays for their shape instead of recalculating the positions
  
### `mixin.shapes.shape_merging`
(default: `true`)  
Merging and intersecting VoxelShapes is optimized using faster position list merging
  
### `mixin.shapes.specialized_shapes`
(default: `true`)  
Specialized VoxelShape implementations are used for cuboid and empty shapes. Collisions with those shapes are optimized using a cuboid specific implementation
  
### `mixin.util`
(default: `true`)  
Various utilities for other mixins
  
### `mixin.util.accessors`
(default: `true`)  
Allow accessing certain fields and functions that are normally inaccessible
  
### `mixin.util.block_entity_retrieval`
(default: `true`)  
Allows access to existing BlockEntities without creating new ones
  
### `mixin.util.block_tracking`
(default: `false`)  
Chunk sections count certain blocks inside them and provide a method to quickly check whether a chunk contains any of these blocks. Furthermore, chunk sections can notify registered listeners about certain blocks being placed or broken.
Requirements:
- `mixin.util.data_storage=true`
- `mixin.util.chunk_status_tracking=true`  
  
### `mixin.util.chunk_access`
(default: `true`)  
Access chunks of worlds, chunk caches and chunk regions directly.
  
### `mixin.util.chunk_status_tracking`
(default: `true`)  
Allows reacting to changes of the load status of chunks.
Requirements:
- `mixin.util.accessors=true`  
  
### `mixin.util.data_storage`
(default: `true`)  
Stores lithium's extra data used for various optimizations on a per-world basis. The data is not saved, but allows optimizations to quickly store and access data.
  
### `mixin.util.entity_collection_replacement`
(default: `true`)  
Allow replacing entity collections with custom collection types.
  
### `mixin.util.entity_movement_tracking`
(default: `true`)  
System to notify subscribers of certain entity sections about position changes of certain entity types.
Requirements:
- `mixin.util.entity_section_position=true`
- `mixin.util.data_storage=true`  
  
### `mixin.util.entity_section_position`
(default: `true`)  
Entity sections store their position
  
### `mixin.util.inventory_change_listening`
(default: `true`)  
Certain BlockEntity Inventories emit updates to their listeners when their stack list is changed or the inventory becomes invalid
  
### `mixin.util.inventory_comparator_tracking`
(default: `true`)  
BlockEntity Inventories update their listeners when a comparator is placed near them
Requirements:
- `mixin.util.block_entity_retrieval=true`  
  
### `mixin.util.item_component_and_count_tracking`
(default: `true`)  
Implements a subscription / publishing system for changes of item stack components and item entity item type.
  
### `mixin.util.world_border_listener`
(default: `true`)  
World border changes are sent to listeners such as BlockEntities
  
### `mixin.world`
(default: `true`)  
Various world related optimizations
  
### `mixin.world.block_entity_ticking`
(default: `true`)  
Various BlockEntity ticking optimizations
  
### `mixin.world.block_entity_ticking.chunk_tickable`
(default: `true`)  
Speed up checking whether a chunk ticks block entities during block entity ticking.
  
### `mixin.world.block_entity_ticking.sleeping`
(default: `true`)  
Allows BlockEntities to sleep, meaning they are no longer ticked until woken up, e.g. by updates to their inventory or block state
  
### `mixin.world.block_entity_ticking.sleeping.brewing_stand`
(default: `true`)  
BlockEntity sleeping for inactive brewing stands
  
### `mixin.world.block_entity_ticking.sleeping.campfire`
(default: `true`)  
BlockEntity sleeping for inactive campfires
  
### `mixin.world.block_entity_ticking.sleeping.campfire.lit`
(default: `true`)  
BlockEntity sleeping for inactive lit campfires
  
### `mixin.world.block_entity_ticking.sleeping.campfire.unlit`
(default: `true`)  
BlockEntity sleeping for inactive unlit campfires
  
### `mixin.world.block_entity_ticking.sleeping.crafter`
(default: `true`)  
BlockEntity sleeping for inactive crafters
  
### `mixin.world.block_entity_ticking.sleeping.furnace`
(default: `true`)  
BlockEntity sleeping for inactive furnaces
  
### `mixin.world.block_entity_ticking.sleeping.hopper`
(default: `true`)  
BlockEntity sleeping for locked hoppers
  
### `mixin.world.block_entity_ticking.sleeping.shulker_box`
(default: `true`)  
BlockEntity sleeping for closed shulker boxes
  
### `mixin.world.block_entity_ticking.world_border`
(default: `true`)  
Avoids repeatedly testing whether the BlockEntity is inside the world border by caching the test result and listening for world border changes
Requirements:
- `mixin.util.world_border_listener=true`  
  
### `mixin.world.chunk_access`
(default: `true`)  
Several changes to the chunk manager to speed up chunk access
  
### `mixin.world.chunk_ticking`
(default: `true`)  
Various optimizations to chunk ticking
  
### `mixin.world.chunk_ticking.spread_ice`
(default: `true`)  
Access FluidState through already known BlockState instead of accessing the world again.
  
### `mixin.world.combined_heightmap_update`
(default: `true`)  
The four vanilla heightmaps are updated using a combined block search instead of searching blocks separately.
  
### `mixin.world.explosions`
(default: `true`)  
Various improvements to explosions.
  
### `mixin.world.explosions.block_raycast`
(default: `true`)  
Various improvements to explosion block damage, e.g. not accessing blocks along an explosion ray multiple times
  
### `mixin.world.explosions.cache_exposure`
(default: `true`)  
Caches entity explosion exposure to avoid duplicate calculations.
  
### `mixin.world.game_events`
(default: `true`)  
Various improvements to game events (vibrations) that are detected by allays, wardens and several sculk blocks.
  
### `mixin.world.game_events.dispatch`
(default: `true`)  
Create game event dispatchers for chunk sections only when needed, i.e. when a listener is added to a section. This reduces memory usage for chunks that do not have any listeners. The dispatchers are accessed more directly instead of indirectly through chunks. In total this speeds up attempting to dispatch events especially when there are no nearby listeners.
Requirements:
- `mixin.util.data_storage=true`
- `mixin.util.chunk_status_tracking=true`  
  
### `mixin.world.inline_block_access`
(default: `true`)  
Faster block and fluid access due to inlining and reduced method size
  
### `mixin.world.inline_height`
(default: `false`)  
Reduces indirection by inlining world height access methods
  
### `mixin.world.raycast`
(default: `true`)  
Speeds up raycasts with faster block accesses and more efficient fluid handling.
  
### `mixin.world.temperature_cache`
(default: `true`)  
Removes the 1024 entry biome temperature cache hash map because the cache seems to be slow and rarely hit.
  
### `mixin.world.tick_scheduler`
(default: `true`)  
Use faster tick collections and pack scheduled ticks into integers for easier tick comparisons
  
