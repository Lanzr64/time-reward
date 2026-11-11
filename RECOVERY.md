# 1.20.1 Forge 源码恢复记录

## 来源

以 `D:\workspace\LZ-DEV\dev-mc\time_reward-1.20.1-forge-0.1.1.jar` 为 1.20.1 实现基准。原 JAR 的 SHA-256 为 `56fdc2fa369898e7b588eeab8fc69b53ea1ef83542ba54c3edbf3577b90210ae`。

使用 CFR 0.152 反编译出 36 个 Java 顶层类，再用本机 ForgeGradle 缓存中的 1.20.1 `srg_to_official` 映射还原 Minecraft 方法和字段名。修正了反编译器生成的泛型、枚举分支和 try-with-resources 错误。评论记录工具等与 1.21 分支实现一致的文件直接用该分支的可读源码恢复。JAR 内的贴图、中文语言文件和访问转换器也已提取到 `src/main/resources`。

字节码无法恢复原作者的注释和局部变量名。部分复杂点击处理仍保留反编译器生成的控制流标签，但已通过 Java 编译。

## 已恢复的功能

- `B` 键打开客户端奖励背包；12 列滚动界面、搜索、排序和窗口缩放。
- 原版客户端可用的 6 行箱子分页菜单，以及管理员奖励模板编辑。
- 玩家独立奖励库存、随等级扩容、世界存档内的数据文件。
- `/tyj-reward get`、`admin set`、`admin enable [true|false]`、`admin preset`、`admin clean`、`admin addRecord`，以及补回的 `admin reset <target>` 和新增的 `admin view <playerId>`。
- 30 天一级、每级 10 格、奖励模板最多 360 格。

`admin reset <target>` 在提供的 JAR 中已经缺失。补回时沿用旧 1.20 分支的管理员权限和在线玩家参数，并同时清除该玩家的独立奖励存档；否则只重置旧玩家标签不会影响新版领取逻辑。

`admin view <playerId>` 可使用在线玩家名、服务器缓存的玩家名或 UUID 查看已有奖励存档，打开只读分页菜单；没有存档时会提示管理员。`admin enable true` 开启奖励领取，`admin enable false` 关闭并关闭当前奖励菜单；不带参数的 `admin enable` 保留为开启别名。

## 构建

在 PowerShell 中：

```powershell
$env:JAVA_HOME = 'D:\ENV\jdk\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME = 'D:\ENV\gradle-cache'
.\gradlew.bat build --offline --no-daemon --project-cache-dir 'D:\ENV\gradle-project-cache\time-reward'
```

产物：`build/libs/time_reward-1.20.1-forge-0.1.3.jar`。恢复构建已能编译和打包，但尚未在游戏中做交互验证；与原 JAR 的字节不完全相同。

0.1.3 修正客户端背包滚动行和窗口缩放的同步：缩放时重算可见行数，滚动位置锁定到整行，保持滚动条与菜单偏移一致；即使无需滚动条，也重新摆放玩家背包槽位。打开背包和排序后按绝对行号同步完整库存，让滚动立即显示对应行的物品。单个槽位更新也使用绝对索引。客户端和服务端都需要更新到此版本。
