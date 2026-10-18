# CraftNode FRP UDP remote tunnel

## Goal

Let a friend join a CraftNode-hosted Bedrock-compatible server from another network using Minecraft Bedrock only. The host uses CraftNode; the friend does not install a VPN or tunnel app.

## Selected architecture

- Run the existing Bedrock server on the host Android phone.
- Run an Android-compatible FRP client inside CraftNode.
- Connect outbound from the phone to a publicly reachable FRP server.
- Configure an FRP UDP proxy from a public UDP port on the relay to the local Bedrock UDP port (normally 19132).
- Give the friend the relay hostname/IP and allocated public UDP port for Minecraft's Add Server screen.

## Implementation status

- [x] Validated FRP TOML config model and unit tests.
- [x] FRP process controller with private config storage, token redaction in process logs, and stop/cleanup.
- [x] Dedicated foreground service for tunnel lifecycle; it is not exported to other apps.
- [x] Android manifest registration and executable native-library packaging rule.
- [x] Bundle official FRP Android ARM64 executable as `libfrpc.so` in CI, pinned to v0.71.1 and verified against the release SHA-256 digest.
- [x] Add Remote Access UI controls to configure and start/stop the tunnel service.
- [ ] Add durable credential management using Android Keystore; current UI asks for the token each session and does not persist it in preferences.
- [ ] Provision a public relay with stable hostname, UDP port allocation, authentication, rate limits and monitoring.
- [ ] Show the assigned public endpoint and meaningful tunnel state.
- [ ] Verify an actual Bedrock UDP handshake from a second phone on a different network.
- [ ] Test reconnect after mobile network changes, relay failures, Android background limits and cleanup.

## Important behavior

The service deliberately refuses to start when the FRP executable is missing. This branch does not yet package that executable or create a public relay, so remote joining is not available yet. Starting a process alone must never be displayed as a successful Bedrock connection.

## Security and reliability

- Never hardcode a shared production token in the APK or commit secrets. Prefer short-lived per-session credentials from a backend.
- The token-bearing FRP config is written to app-private storage with owner-only permissions and removed when the service stops.
- Never report READY just because the tunnel process started. Require tunnel connectivity and a real Bedrock reachability/handshake test.
- The relay must support FRP UDP proxy mode and expose the chosen public UDP port to the internet.
- Relay bandwidth/compute may incur costs; unlimited free service is not guaranteed.
- Preserve the current LAN workflow and never alter world files as part of tunnel startup/shutdown.
