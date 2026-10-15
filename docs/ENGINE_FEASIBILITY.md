# CraftNode Engine Feasibility Spike

Status: investigation only — no engine is integrated or verified on Android yet.

## Goal

Prove that CraftNode can launch a Bedrock-compatible community server inside the Android app process, without Termux, and persist its world files in app-controlled storage.

## Candidate review

### PowerNukkitX

- Official documentation requires Java 21+ and lists Linux, Windows, macOS and Docker; Android is not a listed supported host OS.
- It implements the Bedrock protocol, but is not Mojang's official Bedrock Dedicated Server.
- Its docs state behavior packs are not supported. Resource packs are supported.
- The current requirements make it a poor first Android integration candidate until an Android-compatible Java runtime and process launch are demonstrated.

Sources:
- https://powernukkitx.org/docs/requirements
- https://powernukkitx.org/docs/faq
- https://powernukkitx.org/install

### PocketMine-MP

- Official requirements describe 64-bit CPU/OS and PHP with required extensions; its officially supported platforms are Windows, Linux and macOS.
- Android is not explicitly listed as officially supported. An Android build must not be assumed to work merely because the device is 64-bit.
- It is a community server implementation, so compatibility and gameplay behavior differ from Mojang's official server.

Sources:
- https://doc.pmmp.io/en/rtfd/installation/requirements.html
- https://doc.pmmp.io/en/rtfd/installation.html

## Required proof before UI controls are connected

1. Identify a legally redistributable Android ARM64 runtime/build for the chosen engine and record its license and source.
2. Verify downloaded files using a pinned version and SHA-256 digest.
3. Launch the engine from the app's own process/service, without Termux or an external Linux app.
4. Confirm the engine creates its config and world in app-controlled storage.
5. Capture real stdout/stderr logs and detect ready, stopped and failed states.
6. Stop gracefully and confirm world data survives restart.
7. Test a compatible Bedrock client joining over LAN.
8. Only after LAN succeeds, investigate remote connectivity separately.

## Decision

Do not wire the Start Server button to a simulated success. Until the proof passes, the UI must report that no compatible runtime is installed or verified. Do not advertise remote multiplayer or 24/7 uptime as guaranteed.
