# New feature modules (server core, Android reliability, remote access)

These modules are self-contained and unit-tested. They are **not yet wired into the UI or `ServerHostService`**; wiring should be done after CI confirms they compile.

## Server core (`server/`)
- `ServerSettings` / `ServerPropertiesEditor`: validated read/write of motd, game mode, difficulty, max players, port, whitelist. Preserves comments and unknown keys such as `xbox-auth`.
- `PlayerCommands` / `PlayerListParser`: whitelist, op, kick, ban, pardon command builders (names validated against command injection) and a parser for `list` output.
- `CrashRestartPolicy`: exponential-backoff auto-restart that gives up after 3 crashes in 10 minutes.

## Android reliability (`reliability/`)
- `DeviceHealthMonitor` + `HealthAdvisor`: thermal level, free memory, battery-optimization status, with user-facing warnings.
- `NetworkAddressMonitor`: live LAN/Tailscale IPv4 addresses with change callbacks.

## Remote access (`remote/`)
- `BedrockReachabilityChecker` / `RakNetPing`: real RakNet ping to test whether a server answers on UDP. Run it from a different device than the host for a meaningful remote test.
- `ServerAddress` / `ServerAddressShare`: address parsing and a share-sheet message.

## Wiring still to do
1. `ServerHostService`: track a user-requested-stop flag and consult `CrashRestartPolicy` when the process exits; also run a backup (`WorldBackupManager`) before auto-restart.
2. Dashboard UI: settings form, player list with whitelist/kick/ban buttons, health warnings banner, reachability check button, share button.
3. QR codes for the address need a library such as ZXing (not added here).
