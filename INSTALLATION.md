# AI Player v0.2.0 — Downloads & Installation

This branch is the working dual-edition build. The default `main` branch is not modified by these experiments.

## Direct downloads

Once the GitHub Actions build completes, these files are published to this branch:

- Java/Fabric JAR: [`latest/ai-player.jar`](https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/arena/ai-player-dual-edition-brain/latest/ai-player.jar)
- Bedrock Add-On (`.mcaddon`): [`dist/AIPlayer-Bedrock-0.2.0.mcaddon`](https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/arena/ai-player-dual-edition-brain/dist/AIPlayer-Bedrock-0.2.0.mcaddon)
- Behavior Pack: [`dist/AIPlayer_BP.mcpack`](https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/arena/ai-player-dual-edition-brain/dist/AIPlayer_BP.mcpack)
- Resource Pack: [`dist/AIPlayer_RP.mcpack`](https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/arena/ai-player-dual-edition-brain/dist/AIPlayer_RP.mcpack)
- Full source ZIP: [`dist/minecraft-ai-player-dual-edition-source.zip`](https://raw.githubusercontent.com/hosseinasgari898989-dev/minecraft-ai-player/arena/ai-player-dual-edition-brain/dist/minecraft-ai-player-dual-edition-source.zip)

Direct links return 404 until the first successful artifact-publishing workflow has completed.

## Java Edition — Fabric

1. Use Minecraft Java Edition 1.20.6, Java 21, Fabric Loader 0.19.5+, and Fabric API 0.100.8+1.20.6.
2. Download `ai-player.jar` and place it in the game's `.minecraft/mods` folder.
3. Start a world. To inspect the companion use `/aiplayer status`; to spawn it use `/aiplayer spawn`.
4. Supported commands can be sent in chat (for example `دنبالم بیا`, `۱۶ تا چوب جمع کن`, or `برام یک خونه شیک بساز`).

## Bedrock Edition

1. Download `AIPlayer-Bedrock-0.2.0.mcaddon` and open it with Minecraft.
2. In world settings, activate both the AI Player Behavior Pack and Resource Pack.
3. Spawn with `/function aiplayer/spawn` or issue a command with `/scriptevent aiplayer:cmd Follow me.`.
4. The chat listener is used where the installed Bedrock runtime exposes it. Script-event commands are the explicit fallback.

## Verification status

A successful CI build verifies Java compilation, automated tests, JSON/manifest validity, ZIP integrity, and packaging. It does not replace an interactive test in a live Minecraft client. Bedrock execution and all in-game behavior should be tested in a copied test world before use in a valued survival world.
