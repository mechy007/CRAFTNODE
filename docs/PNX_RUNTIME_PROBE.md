# PowerNukkitX runtime probe — implementation step

This branch adds a runtime probe for CraftNode's planned app-private Java runtime location:

`filesDir/pnx-runtime/bin/java`

The probe checks that the phone exposes ARM64, that the Java executable exists and is executable, and that `java -version` completes successfully and reports Java 21 or newer. It configures a private runtime library path for the probe and reports clear failure reasons. It does not download files, invoke a shell, enable root, or launch PowerNukkitX.

## Why this is not the engine integration yet

Upstream PowerNukkitX requires 64-bit Java 21+ and documents Linux, Windows, macOS and Docker, not Android. A Java version string alone does not prove that its native libraries and networking work on Android. The next gate is to select a redistributable Android ARM64 Java 21 runtime with a clear license and immutable checksum, then verify execution on a physical Android device.

After a runtime is installed and this probe passes, the next implementation can add a checksum-verified PowerNukkitX JAR installer and a process adapter. Do not enable the engine or show a server-ready state until startup, world persistence and a real Bedrock LAN join pass.

Upstream references:
- https://powernukkitx.org/install
- https://powernukkitx.org/docs/faq
- https://github.com/PowerNukkitX/PowerNukkitX/releases
