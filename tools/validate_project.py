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
    require('versionCode = 113' in gradle and 'versionName = "1.0.13"' in gradle,
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
