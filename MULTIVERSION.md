# 多版本开发

项目使用 Stonecutter 管理一份主源码，并为每个 Minecraft 版本生成独立 Fabric Jar。

## 当前版本矩阵

| Minecraft | Java | Loom 模式 |
| --- | ---: | --- |
| 1.20.1 | 17 | 重映射 |
| 1.20.4 | 17 | 重映射 |
| 1.21.1 | 21 | 重映射 |
| 1.21.4 | 21 | 重映射 |
| 1.21.8 | 21 | 重映射 |
| 1.21.11 | 21 | 重映射 |
| 26.1.2 | 25 | 非混淆 |
| 26.2 | 25 | 非混淆 |

`src/` 保存 VCS 基准版本，目前是 26.1.2；最新稳定目标是 26.2。每个版本自己的依赖和 Java 要求位于
`versions/<minecraft>/gradle.properties`。

## 常用命令

```powershell
# 构建当前 26.1.2 基线
.\gradlew.bat :26.1.2:build

# 构建最新稳定版本
.\gradlew.bat :26.2:build

# 将 src/ 切换并预处理为另一个版本
.\gradlew.bat "Set active project to 1.21.11"

# 提交前恢复到 VCS 基准版本
.\gradlew.bat "Reset active project"

# 所有版本完成适配后一次性构建
.\gradlew.bat buildAllVersions
```

## 版本条件

Stonecutter 可以直接比较 Minecraft 版本：

```kotlin
//? if >=26.1 {
newRenderingApi()
//?} else {
/*oldRenderingApi()*/
//?}
```

也可以使用 `stonecutter.gradle.kts` 中定义的常量，例如：

```kotlin
//? if minecraft_26_1_or_newer
newRenderingApi()
```

优先把差异封装在少量兼容层文件中；布局、配置、动画算法等业务逻辑继续保持单份实现。

## 增加版本

1. 在 `settings.gradle.kts` 的正确 Loom 分组中登记版本。
2. 新建 `versions/<minecraft>/gradle.properties` 并填写依赖与 Java 版本。
3. 切换到该版本，逐项处理编译错误和 Mixin 签名差异。
4. 构建并进入游戏验证世界血条、HUD、粒子、名称隐藏和配置界面。

Gradle Wrapper 与依赖下载均已固定使用 `127.0.0.1:7890` 代理：Wrapper JVM 参数位于
`gradlew` / `gradlew.bat`，仓库下载参数位于根 `gradle.properties`。
