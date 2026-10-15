# CraftNode Product Specification

## Identity

- Name: CraftNode
- Tagline: Your world. Your server. Your rules.
- Theme: Cyber, midnight black, neon violet and cyan
- Logo concept: portal inside a pixel-style block
- Application ID: app.bedrockhost.mobile
- Initial version: 0.1.0
- Platform: Android
- UI: Kotlin + Jetpack Compose
- Runtime installation: Hybrid — bundle essential app components and download larger permitted engine/runtime components after integrity and compatibility checks
- Privacy: Local-first; no mandatory account

## Core product requirement

Host a compatible Minecraft Bedrock community server directly on an Android phone, with the goal of letting a friend in another city join. LAN is the first networking milestone; remote networking must be separately tested. No external Linux app or paid hosting service should be mandatory.

## Main areas

1. Home dashboard: server status, start/stop, player count, uptime and available device metrics.
2. Servers and worlds: profiles, world import/export, backups, restore, and engine/version details.
3. Live console: logs, errors and supported commands.
4. Players: online list, whitelist, supported moderation and player activity history.
5. Configuration: server properties, pack management, schedules and network diagnostics.
6. Settings: runtime downloads, storage, privacy, AI provider configuration and diagnostics.

## CraftMind AI — six required capabilities

1. AI Server Administrator: natural-language requests mapped to explicitly supported actions.
2. AI Error Fixer: analyze actual logs, explain likely causes and propose reversible fixes.
3. AI Performance Coach: use measured device/server data; never fabricate metrics.
4. AI World Creator: help design worlds, quests, rules and validated supported configuration.
5. AI Add-on Assistant: validate manifests/configuration and help create compatible starter files.
6. AI Multiplayer Helper: diagnose server status, version mismatch and network connection problems.

### AI architecture and safeguards

- Hybrid: deterministic local diagnostics for basic tasks; configurable cloud AI for complex language tasks.
- Offline fallback for core controls and basic diagnostics.
- AI actions go through a typed, permission-checked command layer. Require confirmation for destructive or security-sensitive actions.
- Report actual execution results; never claim an action succeeded unless verified.
- Local memory is opt-in/configurable and can be viewed, exported and erased.
- Free-tier API limits must be disclosed; no unlimited-free-use claims.

## Runtime feasibility gate

Before building the full server manager, validate a community engine/runtime on target Android devices. Candidate engines such as PocketMine-MP and PowerNukkitX are unconfirmed; their Bedrock compatibility and Android runtime requirements differ. Do not claim vanilla parity.

Acceptance test:
- Starts in-app without Termux.
- Creates and saves a world.
- Allows a compatible Bedrock client to join over LAN.
- Produces readable logs and shuts down safely.
- Recovers from failed start without corrupting world data.

Only after LAN passes should remote access be tested with a compatible tunnel or networking approach.

## Networking and reliability

- LAN support first.
- Remote access only after testing; NAT, carrier-grade NAT, mobile data and tunnel availability may prevent connections.
- Android foreground-service and battery restrictions may interrupt hosting. Do not promise 24/7 uptime.
- Verify downloaded components and show compatibility/integrity errors.
- Use safe writes, verified backups and recovery paths.

## Roadmap

1. Android scaffold and CI build.
2. Runtime/engine proof of concept.
3. First playable world and LAN connection.
4. Console, properties, player controls, backup/restore and monitoring.
5. Remote connectivity, add-ons, multi-world management and schedules.
6. CraftMind AI integration with real server data and safe actions.
7. Device testing, update/data-preservation checks and preview release.

## v0.1.0 acceptance criteria

- CI produces a debug APK.
- UI navigation works without crashing.
- Runtime install failures are clearly reported.
- Engine starts/stops reliably on a target device.
- World saves and reopens.
- LAN client connection is demonstrated.
- Logs reflect real engine state.
- Normal app updates preserve app data.
- No paid hosting or external Linux app is mandatory.
