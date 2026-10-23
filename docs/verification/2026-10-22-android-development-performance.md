# Android 开发性能优化验证（2026-10-22）

## 范围与结论

用户确认问题为 Android Studio 打开工程／Gradle 同步较慢，而不是 App 首屏。此次完成有限的构建配置精简与数据库重复计算优化；没有改业务流程、接口、数据库结构、图片、版本号或历史发行物，也没有提交、上传或发布。

使用 systematic-debugging 定位、simplify 保持行为、verification-before-completion 验证，并进行了独立只读代码审查，未发现需修复的问题。

## 工程体量与现场证据

- `android-app/app/src/main/java`：105 个 Java 文件，约 0.44 MiB。
- `res`：约 24.59 MiB；`assets`：约 14.49 MiB；`app/build`：约 188.04 MiB。后者为可再生的构建产物，不代表业务代码体量。以上为改动前现场统计。
- 最大 Java 文件为 ServiceActivity，约 34 KiB。它的多种业务职责可另行拆分，但本次没有把大规模拆分当作同步提速措施。
- IDE 的 2026-10-22 11:45:06 日志显示模型缓存命中、未触发同步；11:45:10 有 Qoder 插件配置请求超时。只能证明这些事件存在，不能证明该插件解释全部启动延迟。
- IDE 内置 JBR 为 25，工程 Criteria 则指定 JetBrains JDK 21；实测 daemon 使用本机已安装的 JBR 21。保持 Criteria，避免误切换到不兼容的运行环境。
- 本轮只读模拟器未能加载原快照，实际冷启动约 112 秒；这是设备启动证据，不是 IDE 或 Gradle 同步耗时。

## 实际修改

1. 删除 settings 中未使用的 Foojay resolver 插件声明。工程未声明 Java toolchain；Gradle 8.13 的 Daemon Criteria 自带的平台 JDK 下载地址仍保留。不升级 Gradle、AGP 或业务依赖。
2. 开启 `org.gradle.caching` 和 `org.gradle.configuration-cache`。配置缓存用于重复任务图，不代表 IDE 模型同步一定提速。保持 2 GiB heap，没有全局调整 IDE、停用插件或清空缓存。
3. LocalDatabase.saveConfirmed 同一对象的 JSON 序列化从 4 次减为 1 次；acknowledge 的最终确认分支从 2 次减为 1 次。SQL、事务边界及待同步投影处理不变。
4. 更新 Android Studio 打开说明，区分源码级别 17 与 daemon JDK 21，解释缓存、工程打开范围和排查路径。

## 测量结果与限制

用户级现有依赖缓存、离线模式，profile 保存在 `output/android-optimization-20261022/` 和 `android-app/build/reports/profile/`：

| 测量 | 结果 | 解释 |
| --- | --- | --- |
| 修改前 help，新 daemon | 38.862 秒 | 启动、配置和本机缓存预热混合成本 |
| 修改前 help，复用 daemon | 2.509 秒 | 热运行基线 |
| 修改后 help，首次生成配置缓存 | 33.942 秒 | 修改构建脚本触发重新配置／脚本编译 |
| 修改后 help，复用配置缓存 | 3.326 秒 | 配置缓存确认复用，但总耗时未优于原热基线 |
| QA assembleDebug + assembleDebugAndroidTest + lintDebug | 3 分 35 秒 | 改包名、处理资源与依赖后的首次实际构建 |
| 同一 QA 命令再次运行 | 约 2 秒 | 76 个任务 up-to-date，1 个执行；配置缓存复用 |
| 恢复默认包名后相同构建检查 | 28 秒 | 30 个任务执行，47 个 up-to-date |

不能把首次构建与增量构建的差异算成代码优化收益，也不能把新 daemon 与热 daemon 互相比作优化前后。没有通过 IDE 真实重启或 Tooling API 模型同步的前后测量，因此尚未证实 IDE 打开／同步总耗时改善；主要确认的是构建缓存可用、减少了一项插件解析依赖及数据库重复序列化。

调查中使用工作区空依赖缓存的离线 help 因缺少 Foojay 插件失败；该失败不作为性能基线。构建参数曾因 PowerShell 拆分而失败，随后加引号修正。首次测试安装误取历史 `intermediates/apk` 的 IDE APK，元数据核对发现目标包名不匹配；之后使用本轮 `outputs/apk/debug` 的 QA APK 重新安装，以下结果均来自正确 QA 包。

## 本次验证

- 默认包名及独立 QA 包名：assembleDebug、assembleDebugAndroidTest、lintDebug 均成功。
- Lint：0 errors，511 warnings；没有声称清理既有全部告警。
- API 34 只读模拟器，独立包名 `com.huimeng.bookshop.optimizationqa`：LocalDatabaseTest 7、LocalSyncTest 5、LocalConflictTest 3、LocalBusinessTest 7，共 **22 项通过**。
- 涵盖事务失败回滚、重开数据库、空间／来源隔离、示例不上传、旧购物车迁移、快照与待提交修改保留、回执不覆盖新修改、冲突恢复和本地商品／买家／社区／订单逻辑。没有运行完整 UI 回归、API 26 或真机测试。
- 独立审查未发现 actionable bug；`git diff --check` 通过。
- 测试后关闭本任务创建的只读模拟器；原 AVD 持久数据、历史 APK 和业务数据库保留。

## 本地候选 APK

`output/android-optimization-20261022/final/huimeng-1.1.5-optimization-local.apk`

- 本地 Debug 候选，非新发行版：`com.huimeng.bookshop`，versionName 1.1.5，versionCode 32。
- v2 签名验证通过。
- SHA-256：`0CDE7433920DDDA927DD9B6E446B11BFD97890993D40DEEFB6F576244765E474`。
- 设备回归使用同源码的独立 QA 包，默认包名候选只完成构建、lint、版本与签名检查。
- 没有删图片或功能来压缩安装包，未进行 APK 体积优化；此次不更新 GitHub Release。

主要日志：`build-qa.log`、`build-qa-repeat.log`、`build-default.log`、`database-regression.log`、`final/signature.txt`，均位于上述 output 目录。
