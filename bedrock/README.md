# AI Player — Minecraft Bedrock Edition Add-On (`AIPlayer_BP` + `AIPlayer_RP`)

## Architecture & API Stability

- **Target Release**: Minecraft Bedrock Edition `1.21.0+`
- **Script API Module**: `@minecraft/server` version `1.11.0` (**Stable channel — zero Beta/Experimental world flags required**)
- **Entity Identifier**: `ai_player:companion`
- **Persistence**: Native `Entity.setDynamicProperty` / `getDynamicProperty` stored directly inside the Bedrock level database across world reloads, plus `minecraft:inventory` (27-slot persistent container).

## Command Channels in Bedrock

Because stable `@minecraft/server` `1.11.0` exposes `system.afterEvents.scriptEventReceive` universally without experimental toggles (while `world.beforeEvents.chatSend` availability varies by Bedrock build channel), **both** channels are wired safely:

1. **Guaranteed Stable Command Channel (works in every world)**:
   - `/function aiplayer/spawn` — Spawn or locate your single owned companion
   - `/function aiplayer/status` — Report real HP, mode, active goal, and container items
   - `/function aiplayer/dismiss` — Dismiss companion
   - `/scriptevent aiplayer:cmd Collect 16 pieces of wood.`
   - `/scriptevent aiplayer:cmd ۱۶ تا چوب جمع کن.`
   - `/scriptevent aiplayer:cmd Build me a nice house.`
2. **Automatic Chat Listener (when runtime exposes `world.beforeEvents.chatSend`)**:
   - Plain English or Persian chat messages automatically route into the same `interpretCommand()` + `GoalManager` brain.
3. **Entity Interaction**:
   - Right-click / tap the companion to inspect live health, mode, task progress, and container counts, or open its 27-slot inventory directly.
