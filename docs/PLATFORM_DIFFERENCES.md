# Platform Differences: Java Edition (Fabric 1.20.6) vs. Bedrock Edition (Add-On 1.21+)

| Subsystem | Java Edition (`Fabric 1.20.6`) | Bedrock Edition (`@minecraft/server 1.11.0`) |
|-----------|--------------------------------|----------------------------------------------|
| **Entity Class** | Custom `AiPlayerEntity extends PathAwareEntity` rendered via `PlayerEntityModel` (64x64 biped with visible `MAINHAND` equipped tools). | Data-driven `ai_player:companion` entity (`minecraft:inventory`, `minecraft:navigation.walk`, 64x64 humanoid geometry & walk/idle animations). |
| **Chat Interception** | Native `ServerMessageEvents.CHAT_MESSAGE` on all singleplayer/server worlds without experimental settings. | Stable `/scriptevent aiplayer:cmd <text>` + `.mcfunction` helpers work unconditionally; `world.beforeEvents.chatSend` auto-subscribes dynamically where exposed by runtime. |
| **Pathfinding & Movement** | `MobNavigation` + custom 512-node 3D `LocalAStarPathfinder` + `StuckDetector`. | `minecraft:navigation.walk` + script-driven `applyImpulse` stepping along custom 3D `findLocalPath` A* waypoints. |
| **Block Harvesting** | `ServerWorld.breakBlock(pos, true, entity, 512)` + tool durability wear + real `ItemEntity` drop pickup into `SimpleInventory(36)`. | `dimension.runCommand('setblock x y z air destroy')` (stable API; cached bounded resource scans) spawning real world item drops collected into `minecraft:inventory` (27 slots). |
| **Persistence** | `writeCustomDataToNbt` / `readCustomDataFromNbt` storing owner UUID, home/guard coords, blueprint progress, and all 36 `ItemStack`s. | `entity.setDynamicProperty(...)` + native Bedrock persistence of the 27-slot `minecraft:inventory` container.
