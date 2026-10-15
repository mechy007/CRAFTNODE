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
    require(len(hashes) >= 2, "PocketMine and PHP downloads have pinned SHA-256 checksums")
    require("PocketMine-MP/releases/download/5.44.3/" in installer, "PocketMine release URL is version-pinned")
    require("downloadVerified(" in installer and "SHA-256 verification failed" in installer,
            "runtime downloads are checksum-verified")
    require("entry.name" in installer and "Unsafe path found" in installer,
            "archive extraction checks canonical paths")
    require("engine-previous" in installer, "engine activation retains rollback support")
    require("libphp.so" in workflow and "sha256sum --check" in workflow and "readelf -h" in workflow,
            "CI checksum-verifies and architecture-checks the bundled PHP executable")
    packaging_ok = re.search(r'keepDebugSymbols\s*\+=\s*["\']\*\*/libphp\.so["\']', gradle) and re.search(r'useLegacyPackaging\s*=\s*true', gradle)
    require(bool(packaging_ok),
            "Android packaging extracts the bundled PHP executable into nativeLibraryDir")
    require('versionCode = 104' in gradle and 'versionName = "1.0.4"' in gradle,
            "version code and name are bumped")
    require('ndkVersion = "27.2.12479018"' in gradle and 'src/main/cpp/CMakeLists.txt' in gradle,
            "Android build pins NDK and enables CMake")
    require("Java_app_bedrockhost_mobile_NativeDiagnostics_nativeReport" in native,
            "C++ JNI entry point matches the Kotlin bridge")
    require('System.loadLibrary("craftnode_native")' in bridge,
            "Kotlin loads the native diagnostics library")
    require("available system RAM" in native, "native diagnostics reports system memory availability")
    require("File(applicationInfo.nativeLibraryDir, \"libphp.so\")" in service,
            "server prefers PHP packaged in Android nativeLibraryDir")
    require("PHPRC" in service and "LD_LIBRARY_PATH" in service,
            "server process receives PHP configuration and native library paths")
    require("ACTION_RESTART" in service and "restartEngine(intent.getStringExtra(EXTRA_PROFILE_ID)" in service
            and "ServerHostService.ACTION_RESTART" in dashboard,
            "restart control is wired from the dashboard to the server service")
    require("ACTION_INSTALL_JAVA21" not in service and "PowerNukkitX" not in main_activity,
            "unsupported Java and PowerNukkitX setup is removed")
    require("formatConsoleLine" in main_activity and "HH:mm:ss" in main_activity,
            "console timestamps are rendered in readable clock format")
    require("RESTART" in dashboard and "App heap" in dashboard,
            "hosting dashboard includes restart and memory information")
    return 0


if __name__ == "__main__":
    sys.exit(main())
