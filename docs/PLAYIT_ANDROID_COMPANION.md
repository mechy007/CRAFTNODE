# Native Playit integration for CraftNode (beta)

CraftNode now includes an initial native Android ARM64 Playit daemon integration using the official Playit agent pinned at v1.0.12. The Remote Access screen can request daemon start/stop, provision a secret key through the daemon's local IPC endpoint, and request tunnel state/endpoints. The existing FRP provider remains available as a separate option.

## Use

1. Install a CraftNode APK built by the Android workflow. The workflow cross-compiles and packages `libplayitd.so` and `libplayit_cli.so` for Android ARM64.
2. Open **Remote access → Native Playit Agent** and tap **Start agent**.
3. To authorize, obtain a Playit agent secret through your Playit account setup/dashboard and paste it into CraftNode's private key field. Tap **Authorize**. The secret is passed to the daemon over its local IPC socket; CraftNode does not put it in ordinary logs.
4. Tap **Refresh status** to request the daemon's current state and reported tunnel addresses. Configure the assigned UDP tunnel to target the local Bedrock server port **19132** in the Playit dashboard if required by your account.
5. Start the Bedrock server, then test a real join from another device on a different network using the exact public hostname and port shown by the agent/dashboard.
6. Use **Stop agent** when finished. Keep Android battery/background restrictions from killing CraftNode during hosting.

## Security and limitations

- The secret is sent to the daemon over a local filesystem socket and is intended to be persisted by Playit's daemon in CraftNode's private app storage. Do not share it with friends or screenshots.
- The UI reports daemon state and any endpoints returned by the pinned Playit IPC protocol. A running daemon is **not** proof that the public tunnel is reachable.
- The packaged binaries are Android ARM64 builds. Other CPU architectures are not supported by this initial integration.
- Device-specific execution permissions, IPC behavior, secret provisioning, lifecycle behavior, and real Bedrock remote joining still require physical-device validation. If native launch or authorization fails, use the Termux companion instructions below or the existing FRP option.
- Keep the FRP provider; it requires a separately hosted FRP relay and is not replaced by Playit.

## Termux fallback

If native execution is unavailable on the device, run these commands in Termux:

```sh
pkg update -y
pkg install tur-repo -y
pkg install playit -y
playitd &
playit-cli
```

Complete the CLI's authorization flow and create a UDP tunnel targeting local port 19132. A tunnel process launching is not proof the relay works.

## Implementation checklist

- [x] Pin and cross-compile official Playit daemon and CLI for Android ARM64.
- [x] Add a foreground service to supervise the daemon.
- [x] Add in-app start/stop, secret provisioning, status and endpoint requests through Playit's local IPC protocol.
- [x] Keep the existing FRP option and Termux fallback.
- [ ] Verify daemon execution and IPC on physical Android hardware.
- [ ] Verify secret provisioning and endpoint display on a real Playit account.
- [ ] Verify a successful remote Bedrock join from a second network.
