# NetEase legacy Forge build

NetEase Java Edition versions 1.12.2, 1.11.2, and 1.7.10 use an isolated
Gradle 8.7 + Unimined build. Keeping this toolchain outside the Gradle 9.5
Stonecutter build prevents old Forge tooling from changing modern targets.

These targets share the pure-Java full-heart animation core with the 1.16
compatibility backend. The world overlay includes right-to-left layered hearts,
health-change blinking, hit scatter, low-health shake, falling damage hearts,
and sequential death shards; it is not the former rectangular fallback.

On Windows, build every old target with:

```powershell
.\legacy\netease-forge\gradlew.bat -p .\legacy\netease-forge build
```

Build one target by replacing `build` with, for example,
`:netease-1.12.2:build`. The installable production Jar has no `-dev`
classifier; Unimined creates the `-dev` Jar only as its remapping input.

From the repository root, `./build-netease.ps1` builds both the seven modern
Stonecutter targets and these three isolated targets without configuring the
unrelated Fabric, Forge, or NeoForge projects.
