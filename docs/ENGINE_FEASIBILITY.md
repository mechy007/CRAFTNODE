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


## Focus: PowerNukkitX and Endstone (2026-10-24)

CraftNode's next engine work is limited to these two requested engines. Do not add Cloudburst Nukkit or other candidates to the catalog.

### PowerNukkitX — first implementation target

- Official upstream: https://github.com/PowerNukkitX/PowerNukkitX
- The reviewed upstream release page lists PowerNukkitX 3.0.5 for Bedrock 1.26.50 / protocol 2193. Verify the exact release asset before pinning it; do not use a moving "latest" URL in production.
- Upstream requires 64-bit Java 21+ and at least 2 GB RAM (4 GB+ recommended). Its documented OSes do not include Android.
- This makes it the first target to investigate, not yet a verified Android engine. Before making it selectable, CraftNode must have a legally redistributable ARM64 Android Java 21 runtime, verify pinned artifacts with SHA-256, launch the server from app-private storage, preserve worlds across restart, and pass a real Bedrock LAN join.
- PowerNukkitX supports resource packs but its FAQ says behavior packs are not supported.

Sources:
- https://powernukkitx.org/install
- https://powernukkitx.org/docs/faq
- https://github.com/PowerNukkitX/PowerNukkitX/releases

### Endstone — separate feasibility gate

- Official upstream: https://github.com/EndstoneMC/endstone
- Endstone depends on Mojang's Bedrock Dedicated Server and exposes Python/C++ plugin APIs. Upstream host targets are x86-64 Windows/Linux, not native ARM64 Android.
- Do not bundle or run the x86-64 BDS binary on the user's ARM64 phone as if it were native. Do not enable Endstone until a supported, legal, and technically viable ARM64 Android path is demonstrated.
- Its Python/C++ APIs are a key differentiator; preserve that goal in the design, but do not imply that Python/C++ plugins can run until the server itself boots successfully.

Sources:
- https://endstone.dev/latest/getting-started/installation/
- https://next.endstone.dev/docs
- https://www.minecraft.net/en-us/download/server/bedrock

### Implementation gates

1. Pin and checksum the upstream engine artifact and record its license.
2. Prove a Java 21+ ARM64 runtime can execute inside CraftNode without Termux or another app.
3. Prove process start/stop, real logs, app-private world persistence, and graceful restart.
4. Verify a Bedrock client joins over LAN on a physical Android device.
5. Test remote play separately only after LAN works.

Until those gates pass, PowerNukkitX and Endstone stay non-selectable and must not show a fake successful install. CI success on Linux is useful evidence but does not prove Android compatibility.
