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

**Early scaffold — not yet a working server host.** The initial Android UI is a foundation only. The server runtime must pass a feasibility test before server hosting is advertised as working.

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
