# SafeUUID

SafeUUID 是一个面向 **Minecraft 1.21.1 / NeoForge 21.1.227** 的双端模组。

它的目标是在 `online-mode=false` 的服务器中，为能够通过正版验证的玩家恢复并使用其 **正版 UUID / GameProfile**，同时保留离线玩家进入服务器的兜底能力。

SafeUUID 的目标体验参考了 TrueUUID 的思路：在离线服务器环境中尽量避免正版玩家 UUID 被离线 UUID 分叉，并提供已知正版名字保护、离线兜底策略和数据迁移工具。当前项目针对 **NeoForge 1.21.1** 进行了适配实现。

> [!NOTE]
> 本项目由作者在 AI 辅助下完成开发与整理。  
> 尽管已经进行了构建、测试和功能验证，但仍可能存在潜在问题或未覆盖到的兼容性情况。  

## 功能特性

- 登录阶段认证  
  服务端在登录阶段向客户端发送 SafeUUID 认证请求，客户端使用 Mojang Session Service 执行 `joinServer(...)`。

- 正版 UUID 应用  
  服务端通过 `hasJoinedServer(...)` 验证成功后，将玩家本次登录使用的离线 GameProfile 替换为正版 GameProfile。

- 离线兜底策略  
  当认证失败时，可根据配置决定是否允许未绑定过正版身份的玩家以离线模式进入。

- 已知正版名字保护  
  某个玩家名只要曾经成功通过正版验证，后续同名认证失败时可以拒绝离线兜底，避免正版 UUID 与离线 UUID 身份分叉。

- 近期同 IP 容错  
  同名同 IP 在 TTL 内曾成功通过正版验证时，本次认证失败可临时按已知 premium UUID 处理。该功能偏向可用性容错，不应视为强安全机制。

- 配置文件  
  使用 NeoForge 标准配置系统，首次运行生成 `config/safeuuid-common.toml`。

- 名字注册表  
  成功通过正版验证的玩家名和 premium UUID 会记录到 `config/safeuuid-premium-names.txt`。

- `/safeuuid link` 数据迁移命令  
  支持将离线 UUID 下的玩家数据迁移到正版 UUID 下，包含 dry-run、备份和保守迁移逻辑。

## 运行环境

- Minecraft：`1.21.1`
- NeoForge：`21.1.227`
- Java：`21`
- 安装方式：客户端和服务端均需安装 SafeUUID

## 重要前提

SafeUUID 面向离线服务器环境设计，服务端必须使用：

```properties
online-mode=false
```

如果服务器已经运行在 `online-mode=true`，通常不需要 SafeUUID 来恢复正版 UUID。

## 安装方式

### 服务端

1. 安装 Minecraft `1.21.1` 对应的 NeoForge 服务端。
2. 将 SafeUUID jar 放入服务端 `mods/` 目录。
3. 确认 `server.properties` 中：

```properties
online-mode=false
```

4. 启动服务端。

首次启动后会生成：

```text
config/safeuuid-common.toml
config/safeuuid-premium-names.txt
```

其中 `safeuuid-premium-names.txt` 会在首次有玩家成功通过正版验证后写入或更新。

### 客户端

1. 安装 Minecraft `1.21.1` 对应的 NeoForge 客户端。
2. 将 SafeUUID jar 放入客户端 `mods/` 目录。
3. 使用正版账号进入安装了 SafeUUID 的服务器。

客户端需要安装 SafeUUID，否则无法响应服务端登录阶段的认证请求。

## 配置文件

首次运行后会生成：

```text
config/safeuuid-common.toml
```

主要配置项如下。

### debug

```toml
debug = false
```

是否启用 SafeUUID 调试日志。

- `false`：只输出关键日志，适合长期运行。
- `true`：输出登录握手、payload、交互状态等详细排错信息。

### auth.timeoutMs

登录阶段等待客户端返回认证结果的最长时间，单位为毫秒。

默认：

```toml
timeoutMs = 10000
```

### auth.allowOfflineOnTimeout

认证超时时是否允许离线兜底。

```toml
allowOfflineOnTimeout = false
```

- `false`：超时后踢出。
- `true`：超时后按离线兜底策略继续登录。

### auth.allowOfflineOnFailure

认证失败时是否允许离线兜底的旧式总开关。

```toml
allowOfflineOnFailure = true
```

如果设置为 `false`，认证失败时通常会拒绝离线进入。

### auth.timeoutKickMessage

认证超时且不允许离线兜底时，客户端看到的踢出原因。

```toml
timeoutKickMessage = "登录超时，未完成账号校验"
```

### auth.offlineFallbackMessage

玩家通过离线兜底进入服务器后收到的聊天提示。

```toml
offlineFallbackMessage = "注意：你当前以离线模式进入服务器；如果你是正版账号，可能是网络原因导致无法成功鉴权，请重新登陆重试。继续游玩，若后续鉴权成功可能会丢失玩家数据。"
```

### auth.offlineShortSubtitle

离线兜底进入时显示的短副标题，同时也会作为简短聊天提示发送。

```toml
offlineShortSubtitle = "鉴权失败：离线模式"
```

### auth.onlineShortSubtitle

正版校验成功时显示的短副标题，同时也会作为简短聊天提示发送。

```toml
onlineShortSubtitle = "已通过正版校验"
```

### auth.knownPremiumDenyOffline

已知正版名字保护。

```toml
knownPremiumDenyOffline = true
```

当某个玩家名曾经成功通过正版验证后，后续同名认证失败时拒绝按离线身份进入。

### auth.allowOfflineForUnknownOnly

仅允许未知名字使用离线兜底。

```toml
allowOfflineForUnknownOnly = true
```

通常建议与 `knownPremiumDenyOffline = true` 一起使用。

### auth.recentIpGrace.enabled

是否启用近期同 IP 成功容错。

```toml
enabled = true
```

当同名玩家在同一 IP 最近成功通过正版验证后，短时间内的认证失败可以临时按已知 premium UUID 处理。

注意：`recentIpGrace` 更偏向可用性容错，不是强安全机制。公共网络、网吧、代理或共享出口环境中请谨慎启用。

### auth.recentIpGrace.ttlSeconds

近期同 IP 成功容错记录的有效时间，单位为秒。

```toml
ttlSeconds = 300
```

默认 `300` 秒，即 5 分钟。

## 工作流程

SafeUUID 的登录流程大致如下：

1. 玩家连接服务器。
2. 服务端在登录阶段发送 SafeUUID 认证请求。
3. 客户端收到请求后，使用当前账号调用 Mojang Session Service 的 `joinServer(...)`。
4. 客户端将认证结果返回服务端。
5. 服务端使用 `hasJoinedServer(...)` 验证玩家名和 serverId。
6. 如果验证成功：
   - 服务端取得正版 GameProfile。
   - 本次登录应用正版 UUID。
   - 记录玩家名与 premium UUID 到名字注册表。
7. 如果验证失败：
   - 若命中 recent IP grace，则临时应用注册表中的 premium UUID。
   - 否则按配置决定离线兜底或拒绝登录。
8. 玩家进入服务器后，SafeUUID 会发送简短的认证结果提示。

## 名字注册表

SafeUUID 会记录成功通过正版验证的玩家名及其 premium UUID。

默认文件：

```text
config/safeuuid-premium-names.txt
```

该注册表用于：

- 判断某个名字是否曾经通过正版验证。
- 配合 `knownPremiumDenyOffline` 防止已知正版名字被离线同名占用。
- 给 `/safeuuid link` 提供 premium UUID 查询依据。

文件内容是一个简单的名字到 UUID 映射。通常不建议手动编辑，除非你明确知道自己在做什么。

## 管理命令

SafeUUID 提供 `/safeuuid link` 命令，用于将某个玩家名对应的离线 UUID 数据迁移到正版 UUID 数据。

该命令需要管理员权限。

### 预览迁移

```text
/safeuuid link dryrun <name>
```

dry-run 只预览，不写盘。它会显示：

- 玩家名
- offline UUID
- premium UUID
- 离线数据文件是否存在
- 正版目标文件是否存在
- run 时会移动还是跳过

### 执行迁移

```text
/safeuuid link run <name>
```

run 会实际执行迁移。

处理范围包括：

```text
world/playerdata/<uuid>.dat
world/advancements/<uuid>.json
world/stats/<uuid>.json
```

执行前会备份相关源文件和目标文件到：

```text
world/backups/safeuuid/<timestamp>/
```

迁移规则：

- 如果离线文件存在，正版目标文件不存在：移动离线文件到正版 UUID 文件名。
- 如果离线文件和正版目标文件都存在：保留正版文件，不覆盖，并标记为跳过。
- 如果离线文件不存在：提示无可迁移数据。

当前版本不做复杂合并。playerdata、advancements、stats 的合并逻辑会作为后续增强方向。

## 日志说明

默认情况下，SafeUUID 只输出关键日志，例如：

- 模组和配置加载
- 认证成功
- 认证失败
- 离线 fallback
- 已知正版名字拒绝离线
- recentIpGrace 命中或过期
- `/safeuuid link` 的关键结果
- 重要异常

如果需要排查登录阶段、协议解码、客户端认证、玩家交互状态等细节，可以在配置中开启：

```toml
debug = true
```

开启后会输出更详细的调试日志。排查完成后建议关闭。

## 已知限制与兼容性说明

- SafeUUID 当前主要目标环境是 Minecraft `1.21.1` + NeoForge `21.1.227`。
- 大型整合包中建议实测，尤其是包含登录流程、网络协议、账号系统或代理桥接相关模组时。
- 某些网络层、协议层、跨端桥接或登录阶段修改类模组可能影响 SafeUUID 的认证请求和响应流程。
- `recentIpGrace` 是可用性容错功能，不是强安全机制。共享 IP 或代理环境中请谨慎使用。
- `/safeuuid link` 当前采用保守迁移策略，不覆盖已有正版数据，也不进行复杂合并。

## 构建方式

### Linux / macOS

```bash
./gradlew build
```

### Windows

```powershell
.\gradlew.bat build
```

构建输出目录：

```text
build/libs
```

## 许可证

本项目采用 GNU LGPL v3.0 许可证，详见仓库根目录下的 `LICENSE` 文件。

## 致谢

- 感谢 TrueUUID 提供的思路和目标体验参考。SafeUUID 在此基础上针对 NeoForge 1.21.1 的登录阶段、配置系统和数据迁移需求进行了适配实现。
- Mojang authlib and session API.
- Sponge Mixin.
- ForgeGradle.
