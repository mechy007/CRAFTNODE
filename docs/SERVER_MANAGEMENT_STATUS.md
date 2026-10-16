# Server management implementation status

This branch starts the server-management work in small verifiable steps.

## Implemented in this increment
- App-private ZIP snapshots for a profile directory under `files/worlds/<profileId>`.
- Backup listing sorted newest first.
- Snapshot writes through a temporary file followed by rename, so incomplete archives are not presented as finished backups.
- Restore stages files before final rename, refuses to overwrite an existing profile, validates profile IDs, and rejects ZIP path traversal.

## Not yet wired into the UI or automatic update flow
The backup manager is a foundation, not a finished in-app backup feature. The next change must add UI actions and call snapshots before engine updates/risky changes. Restores must only be allowed when the server is stopped.

## Remaining roadmap
1. Wire backup/restore UI; implement `.mcworld` import/export with valid Bedrock world packaging.
2. Add engine-supported player listing, whitelist, operator, kick and ban commands; show unsupported operations honestly.
3. Add a validated server.properties editor for settings supported by the selected engine.
4. Add crash classification and bounded auto-restart with backoff (avoid restart loops).
5. Verify foreground-service lifecycle and add battery-optimization guidance.
6. Detect network changes and refresh the displayed local address.
7. Add measured RAM/thermal warnings without promising that Android can prevent throttling.
8. Implement real reachability checks and a QR/share link; document Tailscale setup and its limits.

## Verification requirement
Do not mark a feature complete until CI passes and its behavior has been tested against the actual engine/device. PocketMine-MP and the experimental Falcon engine may support different commands and configuration.
