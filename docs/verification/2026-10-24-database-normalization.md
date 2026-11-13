# 数据库规范化迁移验收记录

日期：2026-10-24；Windows、Node 24.21.0、better-sqlite3 SQLite 3.53.2；Android API 34 模拟器。源码基线提交为 `8fa20e226c3c020d7688c0ece173f16f6133d82f`。本文按实际执行更新，未发布 GitHub 或新 APK。

## 当前阶段

隔离验收及最终完整回归通过，正式库已完成受控切换并恢复服务。当前 47 表，含保留的历史 `notification_reads`；迁移前 40 表原库和完整备份均保留。

## 已执行与结果

| 检查 | 实际命令或操作 | 结果与证据 |
| --- | --- | --- |
| 完整构建 | 根目录 `npm run build` | 通过；源码与实际服务端、网站构建摘要绑定；`final-build.log` |
| TypeScript 检查 | `npm run lint` | 通过；`final-lint.log` |
| 完整项目回归 | 根目录 `npm test` | 首轮及补充累计退款断言后的最终轮均为服务端 128/128；启动器启动、日志、实时同步、备份恢复、布局及响应检查均通过；`final-root-test.log`、`final-root-test-02.log` |
| 切换保护 | `node --import tsx --test test/normalization-build-artifacts.test.ts test/normalization-cutover.test.ts` | 6/6；`review-fixes-green-02.log`。覆盖旧/损坏构建、并发切换、真实服务不可用、四阶段硬退出和恢复 |
| 聊天游标与反馈 | 定向运行 `normalization-business.test.ts` | RED 复现跨会话消息编号误写；修正后 GREEN；`chat-cursor-red.log`、`chat-cursor-green.log` |
| 累计全额退款 | 同文件 `normalized_partial_refunds...` | 部分 1000 分加剩余 5000 分；累计 6000 分、额度清零、回库仅 2 件、禁止继续发货；`cumulative-refund-http-final.log` |
| 源数据副本与恢复 | `assure-current-data.mjs`（私有验收脚本） | 新旧逐资源摘要相等；17 个上传文件一致；旧库及候选完整备份、独立恢复通过；规范化恢复库三轮启动/停止/恢复通过；`current-data-final-assurance.log` |
| Android 构建 | QA 包 assembleDebug、assembleDebugAndroidTest，默认包 assembleDebugAndroidTest、lintDebug，均 `--offline` | 构建通过。lint 0 错误、516 警告；`task7-android-build.log`、`android-old-client-test-build.log` |
| Android 本地同步 | LocalSyncTest、LocalConflictTest | 8/8；`android-core-retry.log` |
| Android 买家与商家 | CustomerServicesLiveTest：购买、付款、消息、反馈、售后；商家回复与初审 | 2/2；`android-services-live.log`。界面与接口调用均注明在测试源码，不将初审 API 调用说成按钮点击 |
| 三端终态 | Chrome 管理员处理后，买家读取反馈、退款、消息 | 1/1；`android-refund-outcome-final.log`。前一次在管理员尚未回复时运行失败，未当作产品故障；`android-refund-outcome.log` 保留 |
| 旧版实包 | 安装归档 `output/android-1.1.6/huimeng-1.1.6.apk`，核对内部版本及设备上 APK SHA-256 | versionCode 33、versionName 1.1.6；归档与安装文件 SHA-256 均为 `c1d68fe6e7eae533cfe1d74b685c1161c1d3a2f46cdcb672e24d570182206dfd` |
| 旧版离线队列 | LocalFirstFlowTest 同一 `normalization116` 本地测试库，先停服创建地址/图片帖子，再重连 | 两项分别 1/1；`old116-offline-queue.log`、`old116-online-replay.log`；真实旧 APK 与规范化隔离服务互通 |
| 管理员 Chrome | 商品详情、订单发货、售后复核/模拟退款、反馈回复/解决、商品审核 | 实际点击并核对保存结果。发货与退款的原生确认框由用户完成，其余操作由工具执行；截图与 `browser-review-result.json` |
| 审核可见性 | 隔离商家接口送审 → Chrome 管理员通过 → 买家目录接口 | 通过前 404，通过后 200，目录两章节保留；`browser-review-setup.log`、`browser-review-result.json` |

日志、截图、数据库与备份均保存在被 Git 忽略的 `.superpowers/sdd/2026-10-24-database-migration-assurance/`。不得把这些业务资料加入源码发布。

## 独立审查处理

审查提出三个问题：实际服务健康检查前过早完成切换；源码指纹未绑定实际构建；并发切换可能相互覆盖状态。均先增加失败断言，再修复并通过上述定向测试。切换现在必须完成停写探针的健康、静态资源及业务阻断检查，且持有整个切换过程的排他维护锁。另补全聊天游标归属事务校验和 47 表候选键、函数依赖、用途及技术存储边界字典。

## 失败、警告与覆盖边界

- 早期 Android 广泛继承测试被 `com.android.systemui` ANR 中断，不能称 41 项通过；保留 `android-local-regression.log` 和 `normalization-emulator-state.png`。重启后按明确测试集合重新验收。
- Android lint 的 516 条警告主要包括 HardcodedText 340、SetTextI18n 48、Overdraw 29、兼容 TextView 属性 22、NotifyDataSetChanged 14、UnusedResources 12 等；本次不扩大为全量界面/资源重构。
- 网页发现原有轻微展示问题：切换资源页时旧行短暂闪现；地址快照的地区标签显示 `region`；商品审核通过的说明仍标为“驳回原因”。记录后续小范围修整，未将其归因于数据库迁移，也不宣称页面零瑕疵。
- 只验证本地服务、Chrome、模拟器及归档旧 APK；未覆盖实体手机、外网部署、真实支付渠道、机器断电及所有未来输入。进程硬退出不等同真实断电。
- 恢复工具按原规则撤销登录会话，需重新登录。恢复对照只显式排除会话，不忽略订单、金额、库存、消息、反馈、图片或同步回执。
- 完成开放写入后禁止直接恢复旧库覆盖新订单；需先停写、保存新数据并制定差量保留方案。

## 备份与回退入口

最终副本目录为 `current-data-copy-03/`：`candidate.json`、`assurance-result.json`、旧库与新库完整备份、三轮独立恢复库，以及迁移前源码归档 `legacy-source-8fa20e2.zip`。精确路径由该目录的 JSON 记录。维护入口为 `server/dist/scripts/normalize.js`，必须显式提供数据库、工作根目录、候选、媒体、证据及端口；日常启动不会自动执行规范化迁移。

切换过程中失败时先保持停写，按维护状态执行 `recover`；状态 COMPLETE 后禁止自动覆盖式回退。保留旧库、完整备份与原始媒体，历史同步去重回执不得清空。

## 最终切换记录

2026-10-24 已执行 `node dist/scripts/normalize.js cutover`，显式指定正式库、候选、证据清单、媒体目录、工作根目录和端口 3000。切换前正常 SQLite checkpoint 返回 busy=0、log=0、checkpointed=0，主库摘要未变；WAL/SHM 由 SQLite 正常关闭清理，未强删。

- 维护状态 `COMPLETE`，`restoreVerified=true`；开放写入前的健康检查通过，两个构建静态资源可读取，业务路由确认保持停写。见 `actual-cutover.log`。
- 正式库预检：normalized、47 表、integrity ok、foreign_key_check 无异常、issues 为空。与保留旧库的逐资源业务基线比较 `equal=true`、differences 为空；见 `actual-post-check.json`。
- 保留旧库：`server/data/huimeng.db.legacy-f8178337-1a91-4e5f-904a-738bb5d0f6f4`，SHA-256 仍为 `574f53d9d31474f89946163d4f10a15c03f8e9b39a942f07d441a6d051bff237`。
- 安装时新库 SHA-256：`53c619045e7e5cae9e627c6008c985bf1e9ac1db15accd4c7b928c47ef465ec9`；恢复正常写入后该值可以随真实业务变化，不能再当作永久固定值。
- 已停止 3000/3100 隔离服务及模拟器测试 App 同步。正式服务恢复于 `http://127.0.0.1:3000/`：health、网站入口、商品目录均返回 200，maintenance=false；见 `actual-service-running.json`。
- 验收新增商品、订单、消息、反馈和退款仅存在隔离数据库，未复制到正式库。当前数据字典已更新至 47 表；原 40 表字典留在 `docs/database-dictionary-legacy-011.md`。

此次完成本地受控迁移；实体手机、外网和真实支付等覆盖限制仍按前文记录。未创建源码提交、GitHub Release 或新版本 APK。
