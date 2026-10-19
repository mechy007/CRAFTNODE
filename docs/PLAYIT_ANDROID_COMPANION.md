# Playit companion setup for CraftNode (experimental)

CraftNode v1.0.18 adds onboarding for a **Termux-based Playit companion workflow**. This is not native Playit process integration: Android apps are sandboxed, and the official Linux ARM64 release is not assumed to be executable on Android. The companion route uses a Termux package built for that environment.

## Important limitations

- Keep the CraftNode Bedrock-compatible server running on local UDP port **19132**.
- Install Termux from its official distribution source, then use the Termux User Repository (TUR) package for Playit. Package availability and commands can change; consult the TUR/Playit community guidance if a command fails.
- Start the Playit daemon and use its CLI to claim/authorize the agent. Do not paste a Playit secret into CraftNode or share it with friends.
- In the Playit dashboard, create a **UDP** tunnel targeting the phone's local server port **19132**. Use the public hostname/port that Playit assigns.
- Keep Android battery/background restrictions from stopping either CraftNode or Termux. A tunnel process launching is not proof the relay works.
- Test a real Bedrock join from a different network. Modern Bedrock clients may use NetherNet; agent release notes mention support in 1.0.12, but CraftNode's engine/client combination still requires a real-device test.
- Do not remove or replace the existing FRP option; it remains a separate provider and requires an FRP server.

## Suggested Termux setup

Run these in Termux, not in CraftNode's console:

```sh
pkg update -y
pkg install tur-repo -y
pkg install playit -y
playitd &
playit-cli
```

The CLI may require agent setup/authorization. If `playit-cli` cannot connect to the daemon, keep `playitd` running and consult the current package instructions rather than repeatedly reinstalling.

## Current implementation status

- [x] Remote screen explains the companion route and copies initial Termux commands.
- [x] Existing FRP controls remain unchanged.
- [ ] Native Playit runtime packaged and launched by CraftNode.
- [ ] Secure in-app agent authorization/configuration.
- [ ] Confirm public UDP tunnel registration and assigned endpoint in the app.
- [ ] Successful remote Bedrock join verified on a second device.

This feature must not be described as fully integrated or working until the unchecked runtime and real-device validation steps pass.
