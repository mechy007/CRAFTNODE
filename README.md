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

**Early scaffold — local hosting is still under validation. Remote panel management is an optional feature and requires a compatible Client API key.** The initial Android UI is a foundation only. The server runtime must pass a feasibility test before server hosting is advertised as working.

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


## Remote CraftNode panel

The CraftNode tab can connect to a compatible Pterodactyl Client API panel over HTTPS to list servers, view resource status, send power requests, and submit console commands. A Client API key is required; the app does not persist that key. Provider-side API access may be disabled. Remote panel management does not provide free cloud RAM or create a hosted server.


## Remote Bedrock access (v1.0.15)

CraftNode's Remote screen prepares a Bedrock server address for a private Tailscale network. Install Tailscale separately on both Android phones and sign into the same private network. Enter the host phone's Tailscale IPv4 address, then use the prepared address in Minecraft Bedrock's Servers > Add Server screen (default port 19132). CraftNode does not install/manage Tailscale or verify reachability; confirm the server's actual port and readiness in Console. The current server compatibility configuration disables Xbox identity verification, so only share access with trusted players.
