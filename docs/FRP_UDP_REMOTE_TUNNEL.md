# CraftNode FRP UDP remote tunnel — implementation plan

## Goal

Let a friend join a CraftNode-hosted Bedrock-compatible server from another network using Minecraft Bedrock only. The host uses CraftNode; the friend does not install a VPN or tunnel app.

## Selected architecture

- Run the existing Bedrock server on the host Android phone.
- Run an Android-compatible FRP client inside CraftNode.
- Connect outbound from the phone to a publicly reachable FRP server.
- Configure an FRP UDP proxy from a public UDP port on the relay to the local Bedrock UDP port (normally 19132).
- Give the friend the relay hostname/IP and allocated public UDP port for Minecraft's Add Server screen.
- Keep signaling/control-plane APIs separate from the Bedrock UDP data path.

FRP documents UDP proxy support and exposing services behind NAT/firewalls. This does not mean a public relay has been deployed for CraftNode.

## Implementation status

- [x] Add a validated Kotlin model that emits FRP TOML for a UDP proxy.
- [x] Add unit tests for config generation and input validation.
- [ ] Bundle or securely install a compatible Android ARM64 FRP client; verify its license, checksum and Android runtime compatibility.
- [ ] Implement tunnel process supervision in the existing foreground service, tied to the selected server profile.
- [ ] Provision a public relay with a stable hostname, UDP port allocation, authentication, rate limits and monitoring.
- [ ] Keep relay credentials out of the APK, source control, UI logs and diagnostic exports. Prefer short-lived per-session credentials from a backend.
- [ ] Add UI for relay configuration, assigned public endpoint, tunnel state and actionable errors.
- [ ] Verify an actual Bedrock UDP handshake from a second phone on a different network.
- [ ] Verify disconnect, host network changes, Android background limits, relay failure and cleanup.

## Security and reliability requirements

- Never report READY just because the tunnel process started. Require tunnel connectivity and a real Bedrock reachability/handshake test.
- Do not hardcode a shared production token in the APK or commit secrets.
- Validate relay host and port inputs; do not accept a URL/path where a hostname is expected.
- The relay must support the chosen FRP UDP proxy mode and expose the public UDP port to the internet.
- Explain that relay bandwidth/compute may incur costs; unlimited free service is not guaranteed.
- Preserve the current LAN workflow and never alter world files as part of tunnel startup/shutdown.

## Current limitation

This branch contains the configuration model and tests only. It does not include or launch the FRP client, provision a public relay, or provide a working public address. Complete the remaining tasks before advertising remote multiplayer as available.
