# AI Player Capability & Verification Matrix

Status labels follow strict engineering verification criteria:
- `IMPLEMENTED AND TESTED` — Real executable code verified by automated unit/integration tests in this build environment.
- `IMPLEMENTED BUT NOT FULLY TESTED` — Real platform source compiled/packaged cleanly against target SDK, awaiting interactive live game-client verification because headless sandbox does not launch graphical Minecraft clients.
- `PARTIALLY IMPLEMENTED` — Working core capability with documented scope boundaries.
- `NOT IMPLEMENTED` — Explicitly out of current scope.

| Capability | Java / Fabric 1.20.6 | Bedrock Add-On 1.21+ | Verification Evidence |
|------------|---------------------|----------------------|-----------------------|
| Offline Zero-LLM Brain Engine | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `AiPlayerBrainTest#scenarioJ`, `bedrock-and-parity.test.mjs` |
| Bilingual EN + FA Parser (56 canonical intents, numerals, ZWNJ, Arabic forms) | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `AiPlayerBrainTest#scenarioI`, `bedrock-and-parity.test.mjs` |
| Ambiguity Clarification & Honest Unsupported Rejection | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | Verified on ambiguous (`Collect 20.`, `بساز`) & unsupported inputs |
| Priority Preemption, Task Interruption & Resumption | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `AiPlayerBrainTest#scenarioF` + Node parity tests |
| Bounded 3D A* Pathfinder & Hazard Avoidance | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `AiPlayerBrainTest#scenarioB_and_G` + Node A* wall/lava tests |
| Stuck Detection & Bounded Retry Failure Reporting | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `StuckDetector` JUnit tests |
| Custom Humanoid Entity, Owner Lifecycle & Deduplication | `IMPLEMENTED BUT NOT FULLY TESTED` | `IMPLEMENTED BUT NOT FULLY TESTED` | Compiled into `ai-player-0.2.0.jar` & validated in `AIPlayer_BP` |
| Real Inventory Accounting & Deficit Calculation | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | `SimpleInventory(36)` + `minecraft:inventory(27)` deficit tests |
| Tree Harvesting, Mining & Real Item Drop Pickup | `IMPLEMENTED BUT NOT FULLY TESTED` | `IMPLEMENTED BUT NOT FULLY TESTED` | Real `world.breakBlock` + `ItemEntity` pickup; requires live world runtime |
| Tool Progression & Recipe Ingredient Consumption | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | Dependency planner tested; live equip via `EquipmentSlot.MAINHAND` |
| 6 Blueprint Structures (Shelter, Houses, Farm, Tower) | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | Blueprint integrity + material consumption verified |
| Wheat Crop Planting & Harvesting | `IMPLEMENTED BUT NOT FULLY TESTED` | `PARTIALLY IMPLEMENTED` | Full soil tilling/replanting on Java; blueprint & seed planting on Bedrock |
| Chest Storage Organization | `IMPLEMENTED BUT NOT FULLY TESTED` | `PARTIALLY IMPLEMENTED` | Java `ChestBlockEntity` transfer + sorting; Bedrock container sorting |
| Furnace Smelting Automation | `PARTIALLY IMPLEMENTED` | `NOT IMPLEMENTED` | Raw iron/coal mining implemented; timed block-entity furnace fuel loop deferred |
| PvP Safety Gate & Explicit Owner Authorization | `IMPLEMENTED AND TESTED` | `IMPLEMENTED AND TESTED` | Strictly blocked unless PvP enabled + explicit consent |
| Persistent Memory & Post-Reload Validation | `IMPLEMENTED AND TESTED` | `IMPLEMENTED BUT NOT FULLY TESTED` | NBT `writeCustomDataToNbt` + `CompanionMemory#validateAfterReload` |
