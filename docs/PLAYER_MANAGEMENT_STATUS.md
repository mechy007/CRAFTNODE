# Player management implementation status

## First implementation slice

CraftNode now has a typed command builder for common PocketMine-style moderation operations:
- list online players
- add/remove whitelist entries
- grant/revoke operator privileges
- kick and ban a player

Inputs are validated to prevent command injection and accidental multi-line commands.

## Important limits

This is a command-building foundation, **not yet a complete player-management UI**. Commands must only be sent when a compatible server is running. The app must read the server response before showing success. NovaMine/PocketMine-style commands may vary by engine/version; Falcon compatibility has not been verified. The current server disables Xbox identity verification, so names should not be treated as authenticated identities.

Next implementation steps:
1. Add a Players screen with online-player parsing from real server output.
2. Send commands through the service's existing command action.
3. Show pending/success/failure based on actual console response, never optimistic UI.
4. Device-test on NovaMine and separately verify Falcon before enabling its controls.
