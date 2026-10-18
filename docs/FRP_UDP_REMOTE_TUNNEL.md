# CraftNode FRP UDP remote tunnel

## Goal

Let a friend join a CraftNode-hosted Bedrock-compatible server from another network using Minecraft Bedrock only. The host uses CraftNode; the friend does not install a VPN or tunnel app.

## Selected architecture

- Run the existing Bedrock server on the host Android phone.
- Run the Android ARM64 FRP client inside CraftNode.
- Connect outbound from the phone to a publicly reachable FRP server.
- Configure an FRP UDP proxy from a public UDP port on the relay to the local Bedrock UDP port (normally 19132).
- Give the friend the relay hostname/IP and allocated public UDP port for Minecraft's Add Server screen.

## Implementation status

- [x] Validated FRP TOML config model and unit tests.
- [x] FRP process controller with app-private config storage and token redaction in process output.
- [x] Dedicated foreground service for tunnel lifecycle; it is not exported to other apps.
- [x] Android manifest registration and executable native-library packaging rule.
- [x] CI downloads the official FRP Android ARM64 v0.71.1 archive, checks its pinned SHA-256 digest, verifies AArch64, and packages the client as `libfrpc.so`.
- [x] Remote Access UI fields for relay host, control port, token, public UDP port, and local UDP port.
- [ ] Persist credentials safely using Android Keystore; the UI currently asks for the token each session.
- [ ] Provision or identify a genuinely free public relay with stable hostname, UDP forwarding, and suitable terms. CraftNode does not provision a relay.
- [ ] Show the assigned public endpoint and a reliable, user-visible tunnel state.
- [ ] Verify an actual Bedrock join from a second phone on a different network.
- [ ] Test reconnect after mobile network changes, relay failures, Android background limits, and cleanup.
- [ ] Verify Falcon's installed build and selected transport configuration against the exact binary shipped in CraftNode.

## Important behavior

The CI workflow packages the FRP client, but a client binary is not a relay. The current feature requires a separately operated, publicly reachable FRP server with UDP forwarding and matching authentication settings. A successful service start or FRP process launch does not prove that the relay authenticated, the UDP proxy registered, or Minecraft can connect. Remote multiplayer is not verified yet.

Falcon supports RakNet and NetherNet at the project level, but do not assume that adding `transport=raknet` to `server.properties` works in every build. Verify the exact installed build's configuration support before changing a working server.

## Security and reliability

- Never hardcode a shared production token in the APK or commit secrets. Prefer short-lived per-session credentials from a backend.
- The token-bearing FRP config is stored in app-private storage and is intended to be removed when the service stops. Treat it as sensitive until deletion is confirmed.
- Never report READY just because the tunnel process started. Require tunnel connectivity and a real Bedrock reachability/handshake test.
- The relay must support FRP UDP proxy mode and expose the chosen public UDP port to the internet.
- Relay bandwidth/compute may incur costs; unlimited free service is not guaranteed.
- Preserve the current LAN workflow and never alter world files as part of tunnel startup/shutdown.
