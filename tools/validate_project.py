#!/usr/bin/env python3
"""Static checks for CraftNode's PocketMine runtime and Android packaging."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")
    print(f"PASS: {message}")


def main() -> int:
    installer = (ROOT / "app/src/main/java/app/bedrockhost/mobile/server/PocketMineInstaller.kt").read_text()
    service = (ROOT / "app/src/main/java/app/bedrockhost/mobile/server/ServerHostService.kt").read_text()
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    native = (ROOT / "app/src/main/cpp/native_diagnostics.cpp").read_text()
    bridge = (ROOT / "app/src/main/java/app/bedrockhost/mobile/NativeDiagnostics.kt").read_text()
    workflow = (ROOT / ".github/workflows/android.yml").read_text()
    main_activity = (ROOT / "app/src/main/java/app/bedrockhost/mobile/MainActivity.kt").read_text()
    dashboard = (ROOT / "app/src/main/java/app/bedrockhost/mobile/LocalHostDashboard.kt").read_text()

    hashes = re.findall(r'"([0-9a-f]{64})"', installer)
    require(len(hashes) >= 1 and "sha256sum --check" in workflow,
            "PHP runtime download has a pinned SHA-256 checksum")
    require("Prepare pinned NovaMine checksum asset" in workflow
            and "sha256sum" in workflow
            and "assets.open(\"novamine.sha256\")" in installer,
            "NovaMine checksum is generated in CI and embedded for runtime verification")
    require("Zinkaio/NovaMine/e37d378d35b7ecb46ee5a6b9838a6d8261da2b24/NovaMine.phar" in installer, "NovaMine server URL is pinned to an immutable commit")
    require("downloadVerified(" in installer and "SHA-256 verification failed" in installer and 'assets.open("novamine.sha256")' in installer,
            "PHP and NovaMine downloads are checksum-verified")
    require("entry.name" in installer and "Unsafe path found" in installer,
            "archive extraction checks canonical paths")
    require("engine-previous" in installer, "engine activation retains rollback support")
    require("libphp.so" in workflow and "sha256sum --check" in workflow and "readelf -h" in workflow,
            "CI checksum-verifies and architecture-checks the bundled PHP executable")
    require("Prepare pinned NovaMine checksum asset" in workflow and "app/src/main/assets/novamine.sha256" in workflow,
            "CI pins and checksums the NovaMine Bedrock-compatible engine")
    packaging_ok = re.search(r'keepDebugSymbols\s*\+=\s*["\']\*\*/libphp\.so["\']', gradle) and re.search(r'useLegacyPackaging\s*=\s*true', gradle)
    require(bool(packaging_ok),
            "Android packaging extracts the bundled PHP executable into nativeLibraryDir")
    require('versionCode = 125' in gradle and 'versionName = "1.0.25"' in gradle,
            "version code and name are bumped")
    require('ndkVersion = "27.2.12479018"' in gradle and 'src/main/cpp/CMakeLists.txt' in gradle,
            "Android build pins NDK and enables CMake")
    require("Java_app_bedrockhost_mobile_NativeDiagnostics_nativeReport" in native,
            "C++ JNI entry point matches the Kotlin bridge")
    require('System.loadLibrary("craftnode_native")' in bridge,
            "Kotlin loads the native diagnostics library")
    require("available system RAM" in native, "native diagnostics reports system memory availability")
    require("FalconInstaller(File(applicationInfo.nativeLibraryDir))" in service and "startFalconEngine(safeId)" in service,
            "Falcon installer and experimental launch path are wired into the service")
    require("Bundle trusted CA certificates for native Falcon TLS" in workflow
            and "app/src/main/assets/cacert.pem" in workflow,
            "CI bundles the system CA trust store for Falcon native TLS")
    require('assets.open("cacert.pem")' in service and 'environment()["SSL_CERT_FILE"]' in service
            and 'environment()["CURL_CA_BUNDLE"]' in service,
            "Falcon launch configures a bundled CA file without disabling TLS verification")
    falcon_installer = (ROOT / "app/src/main/java/app/bedrockhost/mobile/server/FalconInstaller.kt").read_text()
    require("nativeLibraryDir" in falcon_installer and "libfalcon.so" in falcon_installer and "sha256sum --check" in workflow and "libfalcon.so" in workflow,
            "Falcon is checksum-verified in CI and packaged in Android nativeLibraryDir")
    require("File(applicationInfo.nativeLibraryDir, \"libphp.so\")" in service,
            "server prefers PHP packaged in Android nativeLibraryDir")
    require("PHPRC" in service and "LD_LIBRARY_PATH" in service,
            "server process receives PHP configuration and native library paths")
    require("configureLocalXboxAuthentication(profileDir)" in service and '"xbox-auth=off"' in service,
            "LAN server disables the Xbox-auth gate and logs the reduced identity-verification warning")
    require("ACTION_RESTART" in service and "restartEngine(intent.getStringExtra(EXTRA_PROFILE_ID)" in service
            and "ServerHostService.ACTION_RESTART" in dashboard,
            "restart control is wired from the dashboard to the server service")
    require("ACTION_INSTALL_JAVA21" not in service and "PowerNukkitX" not in main_activity,
            "unsupported Java and PowerNukkitX setup is removed")
    require("formatConsoleLine" in main_activity and "HH:mm:ss" in main_activity,
            "console timestamps are rendered in readable clock format")
    require('selectedMinecraftVersion' in main_activity and 'Minecraft Bedrock ${version.first}' in main_activity,
            "Settings provides a Minecraft Bedrock version selector")
    require('getSharedPreferences("craftnode_settings", 0)' in main_activity
            and 'putString("selected_engine", it)' in main_activity
            and 'putString("selected_minecraft_version", it)' in main_activity,
            "selected engine and Minecraft version persist across activity/process recreation")
    require('1.26.52' in main_activity and 'Falcon (experimental)' in main_activity,
            "1.26.52 exposes the experimental Falcon Android engine with a warning")
    require('selectedMinecraftVersion == "1.26.51"' in main_activity and 'selectedEngine == "Falcon (experimental)"' in main_activity,
            "engine installation is gated by the matching engine and Minecraft version")
    require("RESTART" in dashboard and "App heap" in dashboard,
            "hosting dashboard includes restart and memory information")
    require("SERVER READY" in dashboard and "Server started." in dashboard and "PROCESS STARTED · WAITING" in dashboard,
            "dashboard distinguishes a running process from a server that finished booting")
    playit_doc = (ROOT / "docs/PLAYIT_ANDROID_COMPANION.md").read_text()
    require("Create CraftNode agent" in main_activity
            and "PLAYIT · NATIVE BETA" in main_activity
            and "Refresh tunnel" in main_activity
            and "COPY SERVER ADDRESS + PORT" in main_activity
            and "NativePlayitService.ACTION_CLAIM" in main_activity
            and "generated agent key automatically" in main_activity,
            "Playit screen supports automatic agent-key provisioning and copyable tunnel endpoint")
    require("physical-device validation" in playit_doc
            and "successful remote Bedrock join" in playit_doc
            and "UDP" in playit_doc
            and "Termux fallback" in playit_doc,
            "Playit guide records device-test requirements and fallback path")
    require('private fun RemoteAccessPage()' in main_activity and 'START REMOTE TUNNEL' in main_activity
            and 'FrpTunnelService.ACTION_START' in main_activity and '19132' in main_activity
            and 'COPY JOIN ADDRESS' in main_activity,
            "remote access screen can configure the FRP UDP tunnel and copy its join endpoint")
    native_playit = (ROOT / "app/src/main/java/app/bedrockhost/mobile/multiplayer/NativePlayitController.kt").read_text()
    native_playit_service = (ROOT / "app/src/main/java/app/bedrockhost/mobile/multiplayer/NativePlayitService.kt").read_text()
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
    require("libplayitd.so" in native_playit and "libplayit_cli.so" in native_playit and "beginClaim" in native_playit and "set_secret" in native_playit
            and "get_state" in native_playit and "ACTION_STOP" in native_playit_service and "ACTION_CLAIM" in native_playit_service
            and ".multiplayer.NativePlayitService" in manifest,
            "Native Playit daemon lifecycle, IPC authorization/status, and foreground service are wired")
    tunnel_service = (ROOT / "app/src/main/java/app/bedrockhost/mobile/multiplayer/FrpTunnelService.kt").read_text()
    tunnel_controller = (ROOT / "app/src/main/java/app/bedrockhost/mobile/multiplayer/FrpUdpTunnelController.kt").read_text()
    tunnel_config = (ROOT / "app/src/main/java/app/bedrockhost/mobile/multiplayer/FrpUdpTunnelConfig.kt").read_text()
    require("libfrpc.so" in tunnel_controller and "FrpUdpTunnelConfig" in tunnel_controller
            and "forgetLocalConfig" in tunnel_service,
            "FRP client process has a private config lifecycle and cleanup")
    require("frp_0.71.1_android_arm64.tar.gz" in workflow
            and "9dce8c10e66bdf1a0cff19bf2cd1a3f4f7389fccee8ca0cda57a7a7c0d87af3a" in workflow
            and "libfrpc.so" in workflow and "readelf -h" in workflow,
            "CI checksum-verifies and architecture-checks the pinned Android ARM64 FRP client")
    require("auth.token" in tunnel_config and "replace(config.authToken, \"[REDACTED]\")" in tunnel_controller,
            "FRP credentials are redacted from client process logs")
    world_studio = (ROOT / "app/src/main/java/app/bedrockhost/mobile/WorldPlayerManagement.kt").read_text()
    host_dashboard = (ROOT / "app/src/main/java/app/bedrockhost/mobile/LocalHostDashboard.kt").read_text()
    require('"CONFIGURE & HOST"' in host_dashboard and '"Save & Host World"' in host_dashboard
            and '"I have reviewed these world settings"' in host_dashboard,
            "hosting requires completing the pre-host world setup confirmation")
    require('"World seed (optional)"' in host_dashboard and '"Leave blank for a random seed"' in host_dashboard
            and '"Creative"' in host_dashboard and '"Survival"' in host_dashboard
            and '"Adventure"' in host_dashboard and '"Spectator"' in host_dashboard,
            "pre-host setup offers optional seed and Bedrock game-mode choices")
    require('"PvP"' in host_dashboard and '"Force selected game mode"' in host_dashboard
            and '"Enable allow-list / whitelist"' in host_dashboard and '"Maximum players (1–100)"' in host_dashboard
            and '"View distance (4–32 chunks)"' in host_dashboard and '"Tick distance (4–12 chunks)"' in host_dashboard,
            "pre-host setup includes multiplayer, capacity, and performance settings")
    require("World Studio" in world_studio and "craftnode-profile.properties" in world_studio,
            "World Studio persists per-profile settings locally")
    require("DELETE WORLD / SERVER PROFILE" in world_studio and "Confirm deletion" in world_studio
            and "Delete permanently" in world_studio and "host_profile_id" in world_studio,
            "world/profile deletion requires confirmation and protects the hosted profile")
    require("BACKUPS ·" in world_studio and '"backup"' in world_studio
            and '"addon"' in world_studio and "ADVANCED WORLD RULE PRESETS" in world_studio,
            "backup and add-on deletion plus advanced world-rule presets are present")
    require('setProperty("level-seed", chosenSeed)' in world_studio
            and 'setProperty("max-players", chosenMax)' in world_studio
            and 'setProperty("allow-cheats"' in world_studio
            and 'setProperty("gamemode"' in world_studio
            and 'setProperty("difficulty"' in world_studio,
            "World Studio writes compatible server.properties for seed, difficulty, mode, cheats, and player limit")
    require('OpenMultipleDocuments()' in world_studio and '"phar"' in world_studio
            and '"mcpack", "mcaddon"' in world_studio and 'File(worldDir, "plugins")' in world_studio,
            "add-on manager imports PocketMine plugin files and stages Bedrock packs without claiming engine support")
    remote_doc = (ROOT / "docs/FRP_UDP_REMOTE_TUNNEL.md").read_text()
    require("Verify an actual Bedrock join from a second phone on a different network" in remote_doc
            and "Remote multiplayer is not verified yet" in remote_doc,
            "remote multiplayer remains gated on real device validation")
    return 0


if __name__ == "__main__":
    sys.exit(main())
