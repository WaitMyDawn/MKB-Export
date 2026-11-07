# MKB Export

这是一个纯 AI 维护的模组。

从**真实运行的 Minecraft 客户端**导出知识快照：物品与实体的实机渲染图，以及注册表、语言、配方、标签、掉落、交易、生成规则、结构/多方块等 JSON 数据，供 MAA Knowledge Builder 一类知识库工具导入。

这是一个**客户端模组**，在单人世界中执行命令即可导出，不需要账号、API Key 或外部服务。

- 当前版本：**0.16.4**
- 支持：**Minecraft 1.21.1 / NeoForge** 与 **Minecraft 1.20.1 / Forge**
- 许可：MIT

---

## 为什么需要它

整合包的配方、掉落、实体外观和生成规则，只有在**实际加载了模组、资源包和数据包的环境里**才是准确的。直接解析 JAR 会漏掉运行时注册、KubeJS/数据包覆盖、模型与渲染器行为。

MKB Export 在游戏内完成两件事：

1. 用游戏自己的 GUI 渲染流程生成透明 PNG（而不是猜纹理文件）；
2. 从加载后的注册表、RecipeManager、战利品表、交易工厂、结构模板等读取结构化数据。

产物是一个自包含目录，可以直接被桌面知识库工具读取。

## 功能

- **物品图标**：默认、染色、手持组件/NBT 状态；透明 PNG。
- **实体图标**：成年/幼年、装备、颜色、变种、多状态；实体构图与取景报告。
- **知识数据**：注册信息、语言与 Tooltip、配方、标签、掉落表与掉落修饰器、交易、酿造、工作站规则、药水/效果/附魔、生成规则、结构/模板/多方块、属性。
- **环境指纹**：游戏/加载器/模组版本、资源包顺序、客户端与服务端内容哈希。
- **长任务支持**：进度、取消、检查点与恢复、对已有导出目录的增量更新。
- **隐私**：不读取账号信息，不包含 API Key；导出器本身不依赖外部服务。

## 支持版本

| 模块 | 发行 JAR | Minecraft | 加载器 | 游戏 Java |
| --- | --- | --- | --- | --- |
| `neoforge-1.21.1` | `mkb_export-neoforge-1.21.1-0.16.4.jar` | 1.21.1 | NeoForge 21.1.265 | 21 |
| `forge-1.20.1` | `mkb_export-forge-1.20.1-0.16.4.jar` | 1.20.1 | Forge 47.3.22 | 17 |

两个加载器**没有通用 JAR**，必须下载与实例匹配的那一个。Fabric 暂未支持。

## 安装

1. 准备对应版本的 Minecraft 实例（NeoForge 21.1.265 或 Forge 47.3.22）。
2. 从发布页（如有）下载对应 JAR，或按[从源码构建](#从源码构建)自行打包。
3. 把 JAR 放入实例的 `mods/` 目录。
4. 启动游戏，**进入一个单人世界**并等待世界加载完成。
5. 在聊天栏执行：

   ```text
   /mkb_export all
   ```

6. 导出完成后，产物位于：

   ```text
   <游戏实例>/MKB-Export/
   ```

   如果该目录已存在，会生成带 UUID 后缀的新目录，不覆盖旧包。

7. 打开 MAA Knowledge Builder 的 Build 入口，选择**包含 `manifest.json` 的完整导出目录**。不要选择未完成的 `.mkb-export-*` 检查点。

> 普通导出只支持单人世界，不支持主菜单或多人服务器。主菜单模式仅用于开发烟测。

## 快速开始

```text
/mkb_export all                 # 全部已注册物品（默认 64px）
/mkb_export all 128             # 指定本次物品分辨率
/mkb_export mod minecraft 64    # 只导出某个命名空间
/mkb_export item minecraft:stone 64
/mkb_export status              # 查看进度
/mkb_export cancel              # 取消（保留检查点）
```

导出期间保持世界打开；切换世界、资源重载或退出游戏会使任务失效。大型整合包可能需要几分钟，期间游戏会持续渲染图标。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/mkb_export` | 显示帮助 |
| `/mkb_export all [size]` | 导出全部已注册物品；`size` 16–512，省略时用配置默认值 |
| `/mkb_export mod <namespace> [size]` | 导出某个命名空间下的物品 |
| `/mkb_export item <id> [size]` | 导出单个物品，`id` 必须带命名空间 |
| `/mkb_export held` | 导出主手物品的当前组件/NBT 状态 |
| `/mkb_export dyed <id> <rgb>` | 导出指定十进制 RGB 的染色物品（如 `16711680`） |
| `/mkb_export update <目录> [size]` | 在已有导出目录上做增量更新（目录为游戏实例根下的文件夹名） |
| `/mkb_export resume <目录>` | 恢复被取消/中断的检查点 |
| `/mkb_export checkpoints` | 列出可恢复检查点 |
| `/mkb_export status` | 查看当前任务进度 |
| `/mkb_export cancel` | 取消当前任务 |
| `/mkb_export item_size <16-512>` | 保存默认物品分辨率 |
| `/mkb_export entity_size <256\|512>` | 保存实体分辨率 |
| `/mkb_export entity_nonliving <true\|false>` | 是否包含弹射物、展示实体等非生物实体 |
| `/mkb_export capture_entity [<true\|false> [标签]]` | 捕获准星指向的实体外观到当前会话队列 |
| `/mkb_export capture_clear` | 清空捕获队列 |

几点说明：

- `all` / `mod` / `item` 只遍历默认物品栈；无限组件/NBT 变体不会自动枚举。
- `capture_entity` 需要在单人世界中用准星指向实体；`true` 表示“代表图”。每个会话最多 64 个状态，不支持玩家实体。捕获后仍需执行 `/mkb_export all` 才会写入完整快照。
- 检查点只能在同一世界会话、同一加载器/导出器环境下恢复；环境变化会拒绝恢复。
- `update <目录>` 接受 `MKB-Export` / `MKB-Export-<uuid>` 形式的目录名，`resume <目录>` 接受 `.mkb-export-<id>` 检查点名；两者都相对**游戏实例根目录**，不是绝对路径。`update` 还要求与基线同分辨率、同渲染设置。

## 输出结构

```text
MKB-Export/
  manifest.json                 # 图标清单（items / states）
  snapshot.json                 # 知识快照描述：会话、指纹、分类状态
  data/
    mod_metadata.json
    registries.json
    language.json
    recipes.json
    tags.json
    loot_tables.json
    loot_modifiers.json
    trades.json
    brewing.json
    workstation_rules.json
    potions.json
    effects.json
    enchantments.json
    spawn_rules.json
    structures.json
    structure_sets.json
    template_pools.json
    structure_processors.json
    structure_templates.json
    multiblocks.json
    item_attributes.json
    entity_attributes.json
    entity_state_attributes.json
    entity_icons.json
    loot_entrypoints.json
  icons/
    default/<namespace>/<path>.png
    state/<state-hash>/<namespace>/<path>.png
  entities/                     # 实体图（实体导出时生成）
  entity-report.txt             # 实体取景/透明/失败统计（实体导出时生成）
  entity-compatibility.json     # 实体构图兼容性（实体导出时生成）
```

注意：

- 不是每个包都包含全部 `data/` 文件。可选能力是否导出、是否完整，以 `snapshot.json` 的 `categories` 状态为准（`COMPLETE` / `COMPLETE_WITH_ERRORS` / `NOT_IMPLEMENTED`）。
- `manifest.json` 记录图标的渲染状态（`OK` / `TRANSPARENT` / `UNSUPPORTED` / `FAILED`）与内容哈希。
- 导出包不含账号信息或 API Key；`origin=GAME_GUI` 只是渲染来源声明，不是数字签名或来源认证。
- `snapshot.json` 的环境指纹覆盖客户端/服务端资源与运行时对象；资源包哈希是“规范化路径+内容”的 SHA-256，不是资源包 ZIP 的原始字节哈希，不能单独作为跨环境复用的充分条件。

## 配置

首次启动会在实例的 `config/` 下生成：

- `mkb_export-common.toml`
- `mkb_export_reversal.json`

`mkb_export-common.toml` 的主要选项（`[rendering]`）：

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `itemSize` | 64 | 物品 PNG 分辨率，16–512 |
| `entitySize` | 256 | 实体 PNG 分辨率，仅 256 或 512 |
| `includeNonLiving` | false | 是否包含非 LivingEntity |
| `spawnSamples` | true | 是否为 Mob 准备出生初始化样本 |
| `ageVariants` | true | 是否额外采集幼年样本 |
| `maxCompositionParts` | 64 | 单次外观构图的最大节点数 |
| `maxAdapterVariants` | 8 | 旧版外观适配器每个实体的最大变体数 |
| `maxVariantSamples` | 128 | 每个实体按外观轴采样的上限 |
| `adapterBudgetMillis` | 1000 | 外观适配器回调预算（毫秒） |
| `appearanceAdapters` | true | 是否启用内置/服务提供的外观适配器 |

`mkb_export_reversal.json` 是实体朝向修正名单，默认：

```json
["minecraft:ender_dragon"]
```

可以追加实体注册名；空数组表示关闭全部修正。文件已存在时不会被覆盖。无效配置会阻止导出并报告路径。

命令 `/mkb_export item_size`、`entity_size`、`entity_nonliving` 会通过加载器配置 API 保存，下次启动继续生效。

## 工程结构

```text
common/                 Java 17 协议、哈希、暂存发布与单元测试（不依赖 Minecraft）
shared-client/          客户端命令、任务编排、渲染与知识采集；被两个平台分别编译
neoforge-1.21.1/        NeoForge 入口与 1.21.1 API 适配
forge-1.20.1/           Forge 入口与 1.20.1 API 适配
verification-adapter/   可选的外观适配器示例（仅 -PappearanceExample 时纳入）
docs/                   协议、实体、验收与历史版本说明
gradle/wrapper/         固定 Gradle 8.14.3
```

`shared-client` 不是独立发布模块；两个平台模块各自把它编译进 JAR，避免把某一游戏版本的 Minecraft 类泄漏到通用协议层。

## 从源码构建

要求：

- JDK **17** 与 JDK **21**（Gradle JVM 用 21；Forge 模块以 17 为目标编译）；若 IDE 未自动发现 JDK，请调整 `gradle.properties` 的 `org.gradle.java.installations.paths`；
- 能访问 Gradle Plugin Portal、NeoForge/Forge Maven、Maven Central 和 Mojang 公开资源；
- 工程自带 Gradle Wrapper（8.14.3），不需要预装 Gradle。

在源码根目录执行（IntelliJ IDEA 可导入 `settings.gradle`，Gradle JVM 设为 21）：

```powershell
.\gradlew.bat :common:test
.\gradlew.bat :neoforge-1.21.1:runClient
.\gradlew.bat :forge-1.20.1:runClient
.\gradlew.bat assembleRelease
```

发行 JAR 汇总到：

```text
build/release/mkb_export-neoforge-1.21.1-0.16.4.jar
build/release/mkb_export-forge-1.20.1-0.16.4.jar
```

只安装这两个发行 JAR；不要安装 `common` JAR、sources JAR 或 `build/devlibs/` 下的开发产物。

如果 NeoForge 官方 Maven 不可达，可以通过可信镜像构建：

```powershell
.\gradlew.bat -PneoForgeMaven=https://<可信镜像>/releases assembleRelease
```

`gradle.properties` 中的 `neoForgeMaven` 是项目级覆盖，只影响 `net.neoforged` 命名空间；主站可直连时可删除。

使用已有游戏实例调试：

```powershell
.\gradlew.bat :neoforge-1.21.1:runClient "-PgameDirectory=D:/Minecraft/minecraft/.minecraft/versions/1.21.1-NeoForge-MKB"
.\gradlew.bat :forge-1.20.1:runClient "-PgameDirectory=D:/Minecraft/minecraft/.minecraft/versions/1.20.1-Forge-MKB"
```

同一个实例不要同时加载源码开发运行和已安装的 `mkb_export` JAR。

## 验证

```powershell
.\gradlew.bat :common:test
```

覆盖注册名、哈希、失败记录、取消清理、不覆盖旧导出、路径与预算校验。

实机烟测（会在完成后自动退出）：

```powershell
.\gradlew.bat :neoforge-1.21.1:runClient -PexportSmoke
.\gradlew.bat :forge-1.20.1:runClient -PexportSmoke
.\gradlew.bat :neoforge-1.21.1:runClient -PexportSmoke=all
.\gradlew.bat :neoforge-1.21.1:runClient -PexportSmoke=world "-PgameDirectory=D:/tmp/mkb-neo-world-smoke"
```

`world` 烟测会创建临时世界并验证取消/不发布/重新导出；必须使用专用临时目录，不要指向日常存档。`all` 是对全部已注册物品的压力验收。

## 已知限制

- **单人世界限定**：普通导出依赖内置服务端；不能从主菜单或多人服务器导出完整知识快照。
- **渲染不是万能的**：需要玩家、世界、特殊图形上下文或自定义渲染器的物品/实体可能失败或得到全透明图；清单会标记 `FAILED` / `TRANSPARENT`，不会伪造可用图片。
- **动画取帧**：动画或时间相关模型取绘制时的当前帧，整批图标不保证处于同一动画时刻。
- **实体取景**：大型/多体节模型可能触边或被标记 `POSSIBLY_CLIPPED`；未适配的模组实体不声明完整支持。
- **知识覆盖是“可枚举配置”**：Java 事件、脚本、动态生成器、召唤仪式等无法通过统一注册表穷举；结构模板不等于实际生成位置；交易概率是工厂入选概率，不是最终物品概率。具体边界见各 `docs/` 说明。
- **版本绑定**：JAR 与 Minecraft/加载器版本一一对应；升级环境后需要重新导出，不能拿旧包冒充当前环境。
- **无签名**：导出包是数据快照，不是经过认证的发布物。

## 常见问题

**聊天栏没有 `/mkb_export` 命令？**

确认 JAR 与实例的 Minecraft/加载器版本匹配，且安装在客户端 `mods/`；查看 `logs/latest.log` 是否有加载错误。

**执行命令没有反应？**

必须在**单人世界**中、世界加载完成后执行；主菜单不支持普通导出。用 `/mkb_export status` 查看是否有任务在跑。

**导出很慢或卡住？**

大型整合包会逐帧渲染。确认没有同时运行两个导出；可先减小 `size`，关闭其他占用 GPU 的程序，检查磁盘空间，再重试。必要时 `/mkb_export cancel`，然后用 `checkpoints` + `resume` 恢复。

**图标缺失或全透明？**

查看 `manifest.json` 中该条目的 `status`。`FAILED` 表示渲染/编码失败；`TRANSPARENT` 表示渲染成功但没有可见像素。自定义渲染器、缺少上下文或模型问题都可能导致这种结果。

**桌面软件导入失败？**

选择包含 `manifest.json` 的完整导出目录，不要选择 `.mkb-export-*` 检查点；新版导出包可能需要新版桌面软件。

**恢复检查点被拒绝？**

检查点绑定世界会话、加载器/导出器版本和环境指纹。更换世界、重启游戏或升级模组后请重新导出。

**崩溃了？**

保留 `logs/latest.log`、`crash-reports/` 和导出目录，先用最小模组环境复现；不要删除现场。

## 文档

- [模块与运行约束](docs/architecture.md)
- [清单与导出格式 v1](docs/mkb-export-v1.md)
- [知识快照协议](docs/mkb-snapshot-protocol.md)
- [增量、恢复与状态协议](docs/export-r4.md)
- [实体样本与捕获](docs/entity-samples-3-4.md)
- [实体构图与兼容性](docs/entity-composition-p5.md)
- [外观适配器 API](docs/entity-appearance-adapter-api.md)
- [版本记录（历史 README）](docs/changelog.md)

## 许可

MIT License，Copyright (c) 2026 Yagen。Mod ID：`mkb_export`；Java 包：`yagen.waitmydawn.mkb_export`。
