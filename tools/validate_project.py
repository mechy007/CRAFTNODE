#!/usr/bin/env python3
"""Fast static safety checks for CraftNode's engine installer and native build."""
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
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    native = (ROOT / "app/src/main/cpp/native_diagnostics.cpp").read_text()
    bridge = (ROOT / "app/src/main/java/app/bedrockhost/mobile/NativeDiagnostics.kt").read_text()

    hashes = re.findall(r'"([0-9a-f]{64})"', installer)
    require(len(hashes) >= 2, "installer contains SHA-256 pins for both engine assets")
    require("PocketMine-MP/releases/download/5.44.3/" in installer, "PocketMine release URL is version-pinned")
    require("downloadVerified(" in installer and "SHA-256 verification failed" in installer,
            "downloads pass through SHA-256 verification")
    require("entry.name" in installer and "Unsafe path found" in installer,
            "archive extraction checks canonical paths")
    require("engine-previous" in installer and "restore" not in installer.lower() or "engine-previous" in installer,
            "engine activation retains a previous-install rollback path")
    require('ndkVersion = "27.2.12479018"' in gradle and 'src/main/cpp/CMakeLists.txt' in gradle,
            "Android build pins NDK and enables CMake")
    require("Java_app_bedrockhost_mobile_NativeDiagnostics_nativeReport" in native,
            "C++ JNI entry point matches the Kotlin bridge")
    require("System.loadLibrary(\"craftnode_native\")" in bridge,
            "Kotlin loads the native diagnostics library safely")
    require("available system RAM" in native, "native diagnostics reports system memory availability")
    return 0


if __name__ == "__main__":
    sys.exit(main())
