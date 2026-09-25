# Eye Cross

<p>
  <img src="https://img.shields.io/badge/Minecraft-26.1.2-6BFF8C?style=flat-square&labelColor=0C0F14&color=6BFF8C" alt="Minecraft 26.1.2">
  <img src="https://img.shields.io/badge/Fabric-Loader_0.19.3%2B-B78CFF?style=flat-square&labelColor=0C0F14&color=B78CFF" alt="Fabric Loader 0.19.3+">
  <img src="https://img.shields.io/badge/License-MIT-7C8698?style=flat-square&labelColor=0C0F14&color=7C8698" alt="MIT">
</p>

---

**Eye Cross** 是一个纯客户端 Fabric 模组：模组会自动记录末影之眼的飞行轨迹，把每条轨迹拟合成一条直线，两条以上直线求最小二乘交点，扔两颗眼就能拿到要塞坐标。只扔一颗眼也有用：模组按 Java 版要塞环带分布（128 个要塞、8 个以原点 0,0 为圆心的圆环）结合投掷方向，立刻给出一个粗略坐标与误差带。进入要塞后，模组还会扫描并定位末地传送门的位置。

## 快速上手

**前置模组：**

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)（≥26.1.154，配置文件与配置界面的运行依赖）

**联动模组：**

- [Mod Menu](https://modrinth.com/mod/modmenu)（≥18.0.0，在模组菜单中打开 Eye Cross 配置界面）
- [Xaero&#39;s Minimap](https://modrinth.com/mod/xaeros-minimap)（≥26.2.0，路标联动）

**操作方法：**

1. 安装 mod，进入主世界；
2. 随手扔出一颗末影之眼——它破碎或掉落时，模组将自动记录飞行轨迹，并会按要塞环带给出「第几环 + 粗略坐标 + 误差带」的估测；
3. 走远一点，让第二次投掷朝向不同的方向，再扔一颗，即可精确求交；
4. 聊天栏给出要塞坐标与误差，点击「[点击传送]」即可一键 `/tp @s X ~ Z`；
5. 进入要塞后，模组还会自动扫描末地传送门，并用彩色半透明方框标出（黄 = 未放末影之眼、绿 = 已放眼、紫 = 传送门方块）。

扔得越多越准（最多保留最近 12 条）。精确求交后世界中会在估算位置显示信标式光柱；只扔一颗拿到估测时，世界中会用水平圆环把大致范围标出来。目标超出视距时，你的眼前会出现一支指向要塞的箭头。

**Xaero 联动：** 若安装了 Xaero's Minimap（≥26.2.0），每次算出精确解后，模组会自动把要塞坐标作为一个路径点加入小地图/大地图/路径点菜单（固定名 `Stronghold(EC)`、绿色「S」标记，同 ID 反复覆盖更新）；找到传送门后也可一键把路径点定位到传送门中心（`/eyecross portal`命令或点击聊天栏按钮）。未安装或版本过低时自动跳过，绝不影响估测等主功能。

## 原理

**采样**：每个客户端 tick 记录飞行中眼睛实体的 (x, z)；

**单次估测**：每个世界固定 128 个要塞，分布在以原点 (0,0) 为圆心的 8 个环带（第 1 环 3 个、半径 1239–2857 格 … 第 8 环 9 个、半径 22742–24362 格）；末影之眼飞向最近的要塞，因此把投掷射线与 8 个环带求交，取最先被穿过的环带——区间中点即估测点，区间半宽 + 投掷角度噪声合成为误差带；

**拟合**：对整条轨迹做总体最小二乘（PCA），得到一条直线；

**求交**：对所有直线最小化「点到直线距离平方和」，解 2×2 正规方程；两线夹角小于 2° 判为近平行，提示换位重试。

## 配置

配置文件为 `config/eye-cross.json5`（首次启动自动生成）。可用以下方式修改：

1. 直接编辑文件，然后执行 `/eyecross config reload` 热重载（可使用`/eyecross config path` 查看配置文件位置。）；
2. 装了 Cloth Config + Mod Menu 时，在模组菜单里点 Eye Cross 的「配置」按钮，改完保存即生效；

| 配置项                      | 默认值            | 说明                                               |
| --------------------------- | ----------------- | -------------------------------------------------- |
| `portalEnabled`           | `true`          | 末地传送门功能总开关（扫描、方框、光柱、聊天提示） |
| `estimateRing`            | `true`          | 单次投掷后绘制估测范围环                           |
| `directionArrow`          | `true`          | 要塞在屏幕外时绘制方向箭头                         |
| `hudEnabled`              | `true`          | HUD 持久化默认开关（仍可用`/eyecross hud` 切换） |
| `xaeroWaypoints`          | `true`          | 是否推送/更新 Xaero 路径点                         |
| `beamMinY` / `beamMaxY` | `-64` / `320` | 光柱垂直截断上下界（世界可建筑高度）               |
| `beamHalfWidth`           | `0.35`          | 光柱半宽（格），要塞光柱与传送门光柱共用           |
| `scanInterval`            | `10`            | 末地传送门扫描间隔（tick）                         |
| `solutionBeamColor`       | `FFFFAA00`      | 要塞解光柱颜色（AARRGGBB）                         |
| `portalBeamColor`         | `FFAA00FF`      | 传送门中心光柱颜色（AARRGGBB）                     |
| `portalFrameNoEyeColor`   | `FFFFFF00`      | 未放眼的传送门框架方块颜色（AARRGGBB）             |
| `portalFrameHasEyeColor`  | `FF00FF00`      | 已放眼的传送门框架方块颜色（AARRGGBB）             |
| `portalBlockColor`        | `FFAA00FF`      | 末地传送门方块颜色（AARRGGBB）                     |

## 命令

| 命令                                                         | 作用                                          |
| ------------------------------------------------------------ | --------------------------------------------- |
| `/eyecross help`                                           | 命令用法说明（`/eyecross` 全部子命令）      |
| `/eyecross status`                                         | 每条轨迹与当前解/估测的详情（坐标可点击传送） |
| `/eyecross rings`                                          | 列出要塞环带分布表（各环数量与半径区间）      |
| `/eyecross reset`                                          | 清空轨迹，重新记录                            |
| `/eyecross hud`                                            | 显示 / 隐藏 HUD                               |
| `/eyecross hudpos topleft\|topright\|bottomleft\|bottomright` | 把 HUD 移到屏幕左上 / 右上 / 左下 / 右下      |
| `/eyecross portal`                                         | 把 Xaero 路径点改到已检测到的传送门中心       |
| `/eyecross config reload`                                  | 重新加载配置文件                              |
| `/eyecross config path`                                    | 显示配置文件路径                              |

## 版本支持

| 分支        | Minecraft | Fabric Loader | Fabric API     | 构建产物                              |
| ----------- | --------- | ------------- | -------------- | ------------------------------------- |
| `main`    | 26.1.2    | 0.19.3        | 0.155.2+26.1.2 | `eye-cross-fabric-1.2.0-26.1.2.jar` |
| `mc-26.2` | 26.2      | 0.19.5        | 0.160.0+26.2   | `eye-cross-fabric-1.1.0-26.2.jar`   |

## 多语言

文案全部走翻译 key，内置 `en_us` / `zh_cn` / `zh_tw` / `zh_hk` / `lzh`（`assets/eye-cross/lang/`），跟随游戏语言设置（簡中 / 繁中（台灣）/ 繁中（香港）/ 文言文），可被资源包覆盖或补充其他语言。翻译 key 与 `%s` 占位符须与 `en_us.json` 保持一致。

## 构建

需要 JDK 25。

```bash
./gradlew build      # 产物在 build/libs/
./gradlew runClient  # 开发环境试玩
```

产物命名规则：`<模组名>-fabric-<模组版本>-<Minecraft版本>.jar`，例如 `eye-cross-fabric-1.2.0-26.1.2.jar`；sources jar 同名加 `-sources` 后缀。各分支的版本对应关系见上表。

## License

[MIT](./LICENSE) © RSPqfgn
