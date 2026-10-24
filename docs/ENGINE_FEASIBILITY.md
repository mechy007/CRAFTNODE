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


## Updated implementation direction (2026-10-15)

A public Android server-management project, Minexa/MineServe Mobile, documents a working pattern for no-root Android hosting: bootstrap a Termux-compatible user-space runtime into app-private storage, launch the server as a child process, stream stdin/stdout/stderr, and keep a foreground service while running. Its architecture is a reference for feasibility, not code copied into CraftNode.

Reference:
- https://github.com/lovehutaocute/Minexa
- https://github.com/lovehutaocute/Minexa/blob/master/ARCHITECTURE.md

### CraftNode implementation sequence

1. Add a first-run runtime installer with pinned downloads, SHA-256 verification, progress, cancellation, and cleanup on failure.
2. Implement an app-private process runner and foreground service with real process state and log streaming.
3. Add one Bedrock engine adapter first; validate Android ARM64 compatibility and license before bundling or downloading its files.
4. Verify create-world, stop/restart persistence, and a real LAN client connection on a physical Android device.
5. Add remote access only after LAN works; use a supported tunnel or network path and explain NAT/CGNAT limitations.
6. Connect the frozen Compose UI to runtime state; never show RUNNING until the engine readiness signal is observed.

### Release gate

The current v1.0.0 APK is a UI/build preview and is not a working server host. Runtime installation, engine launch, device-level LAN tests, and remote tests remain incomplete. A CI build alone does not prove hosting works.

## Additional requested engines (2026-10-15)

### PowerNukkitX
- Added to CraftNode's engine catalog as **planned / Android runtime not verified**.
- Upstream requires Java 21+ and lists Windows, Linux and macOS; Android is not an officially listed host OS. It also requires at least 2 GB RAM and does not support behavior packs.
- Before enabling installation, CraftNode needs an Android-compatible ARM64 Java 21 runtime, verified upstream release asset and SHA-256, successful app-private launch, world persistence and LAN client tests.
- Sources: https://powernukkitx.org/docs/requirements ; https://powernukkitx.org/docs/faq

### Endstone
- Added to CraftNode's engine catalog as **planned / Android ARM64 unsupported by upstream**.
- Endstone wraps Mojang's Bedrock Dedicated Server and provides Python/C++ plugin APIs. Upstream native server support is Windows/Linux x86-64; Mojang's official BDS downloads are for Windows and Ubuntu/Linux on Intel/AMD x86-64. That does not directly run on an ARM64 Android phone.
- Do not install the x86-64 server binary on Android or claim it works through emulation. An Android-compatible upstream build/runtime would need to exist and pass licensing, launch, world and client-connection checks before this option can be enabled.
- Sources: https://endstone.dev/latest/getting-started/installation/ ; https://next.endstone.dev/docs ; https://www.minecraft.net/en-us/download/server/bedrock

### Current UI behavior
The Settings engine catalog now lists all three engines. Only PocketMine-MP is install-enabled; PowerNukkitX and Endstone remain visibly marked as planned/unverified. This is intentional, not a claim that either server is already runnable on Android.


## Additional engine candidates (2026-10-24)

The Settings catalog now exposes three additional candidates as **planned and non-selectable**. They must not enable installation or server start until an Android ARM64 runtime, a pinned upstream artifact, checksum verification, world persistence, and a real Bedrock LAN connection are demonstrated.

### Cloudburst Nukkit
- Upstream source: https://github.com/CloudburstMC/Nukkit
- Java-based Bedrock server. This is a useful alternative to test because it is an active public source repository and avoids depending on the currently unavailable Falcon source checkout.
- Not yet integrated. Android compatibility, Java runtime footprint, supported Bedrock protocol, and plugin/world behavior require a feasibility spike.

### PowerNukkitX
- Upstream source: https://github.com/PowerNukkitX/PowerNukkitX
- Java 21+ server; upstream host requirements do not list Android as a supported OS. Keep planned until a bundled ARM64 Java runtime and device tests pass.

### Endstone
- Upstream source: https://github.com/EndstoneMC/endstone
- Depends on Mojang Bedrock Dedicated Server and targets x86-64 host platforms upstream. It is not a direct ARM64 Android option; do not attempt to run its x86-64 binary as if it were native.

### Implementation guardrail
Only PocketMine/NovaMine and Falcon retain their existing selectable catalog status in this change; that status is not proof of successful device hosting. New candidates are display-only, and the installer remains disabled for them. The next practical engine spike should evaluate Cloudburst Nukkit first, then compare its runtime footprint and protocol compatibility with PocketMine/NovaMine.
