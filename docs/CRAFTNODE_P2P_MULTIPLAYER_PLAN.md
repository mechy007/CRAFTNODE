# CraftNode-to-CraftNode Multiplayer (Option A)

## Goal
Let two Android phones with CraftNode installed host and join a Minecraft Bedrock world over the internet. The host runs the existing Bedrock server; the joining phone uses CraftNode to discover the host and establish the connection. Friends should not need a separate VPN app.

## Important compatibility constraint
A normal Bedrock client cannot join a custom peer-to-peer transport just because CraftNode is installed on the host. The joining CraftNode must provide a supported path from Minecraft Bedrock traffic to the peer connection. Do not claim compatibility until tested against the actual Bedrock client and server engine.

## Proposed phases

### Phase 1 — Local-network proof of concept
- Add Host World and Join Friend entry points.
- Generate a random, short-lived room code and display it to the host.
- Joining device submits the code.
- Use a small signaling API to exchange session metadata only; never send world traffic through signaling.
- Verify the joining device can reach the host's Bedrock UDP listener on a LAN before attempting internet traversal.
- Add explicit states: Creating room, Waiting for friend, Connecting, Connected, Failed.

### Phase 2 — Internet connectivity
- Add STUN-based public endpoint discovery and UDP hole-punching.
- Treat direct connectivity as best-effort; CGNAT/symmetric NAT may prevent it.
- Provide clear diagnostics when direct connection fails.
- Do not expose a fake public address or report success until a real Bedrock handshake succeeds.

### Phase 3 — Relay fallback decision
- A relay is required for networks where direct UDP traversal fails.
- Before implementation, choose a relay provider/protocol that supports UDP, Android, and a sustainable free tier. Do not assume Portwarp API access on free plans or rename a Linux executable to an Android library.
- Keep credentials out of the APK; use short-lived room tokens and server-side authorization where needed.

### Phase 4 — Lifecycle and reliability
- Starting a world starts its session; stopping the world closes the session and invalidates the room code.
- Handle app backgrounding, network changes, Android battery restrictions, and server process failure.
- Add timeouts, reconnect behavior, and privacy controls.

## Acceptance tests
1. Two devices on the same Wi-Fi can create/join a room and complete a real Bedrock connection.
2. Two devices on different networks can connect when direct UDP traversal succeeds.
3. If traversal fails, the UI reports the failure honestly and explains that a relay is needed.
4. Stopping the host closes the room and the joiner sees a clear disconnect.
5. No API secret is embedded in the APK or committed to source control.
6. UI status reflects real connection/server state, not merely a created room record.

## Current status
Planning baseline only. No P2P transport or relay is implemented by this document. Build and physical-device tests are required before advertising the feature as working.
