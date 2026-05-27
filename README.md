# Better Health Indicator

A cute client-side Minecraft Fabric mod for displaying better health bars.

## Development

- Minecraft: `26.1`
- Loader: Fabric Loader `0.18.4+`
- Build tool: Gradle `9.4.0`
- Java: `25`
- Language: Kotlin `2.3.21`
- Loom: `net.fabricmc.fabric-loom` `1.15.5`

Minecraft `26.1` is unobfuscated, so this project uses the new non-remapping Fabric Loom flow. Dependencies use standard Gradle configurations such as `implementation` instead of the old `modImplementation`.

## Build

Install JDK 25 and Gradle 9.4.0, then run:

```powershell
gradle build
```

The mod jar will be generated under `build/libs/`.
