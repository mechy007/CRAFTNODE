# CraftNode

**Your world. Your server. Your rules.**

CraftNode is an Android-first Minecraft Bedrock community-server manager, designed for phone-first development and local-first storage.

## Product goals

- Host a compatible community server directly on Android without requiring Termux.
- Support LAN first, then verified remote multiplayer through a compatible networking method.
- Protect worlds with backups, import/export, and safe updates.
- Provide a cyber-themed dashboard with server controls, logs, and monitoring.
- Integrate CraftMind AI: server administrator, error fixer, performance coach, world creator, add-on assistant, and multiplayer helper.
- Prefer free tools and services; never promise unlimited free AI or universal remote connectivity.

## Current status

Remote tunneling is in development and must be validated against real Android devices and Bedrock clients. Do not advertise remote joining as working until a different-network Bedrock connection has been verified.

## Build

The project uses Kotlin, Jetpack Compose, Gradle, and Android Gradle Plugin. GitHub Actions builds the debug APK. Open the repository in Android Studio or use the Actions artifact from a successful run.

## Product specification

See [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md).

## Safety and reliability

- Local-first data by default.
- Validate engine/runtime downloads before use.
- Back up worlds before risky changes.
- Do not expose server addresses or credentials unnecessarily.
- Android background execution and mobile-network restrictions mean 24/7 uptime and remote access cannot be guaranteed.

## Remote Bedrock access

The Remote screen now includes setup controls for an FRP UDP tunnel, and CI bundles a checksum-pinned Android ARM64 FRP client. You must supply a reachable public FRP relay with UDP forwarding and valid credentials. The relay is not provisioned by CraftNode, and cross-network Bedrock joining has not yet been verified. Do not put shared production credentials in source control or claim remote multiplayer is ready until physical testing succeeds. See [docs/FRP_UDP_REMOTE_TUNNEL.md](docs/FRP_UDP_REMOTE_TUNNEL.md) for status and requirements.

The CraftNode tab can also connect to a compatible Pterodactyl Client API panel over HTTPS. Remote panel management does not provide free cloud RAM or create a hosted server.
