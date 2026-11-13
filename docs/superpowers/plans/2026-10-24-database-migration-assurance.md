# 绘梦数据库规范化与迁移安全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task in the current session. Use subagent-driven-development only if the user explicitly chooses delegation. Steps use checkbox (`- [ ]`) syntax for tracking. This document is a plan, not an executed migration or a passing test report.

**Goal:** 在保持买家、商家、管理员业务及旧客户端兼容的前提下实施业务表规范化；迁移前证明数据保留、功能连通、故障停止和恢复能力。

**Architecture:** 以当前数据字典和 3NF 设计为依据，先建立只读预检与语义对照，再在隔离副本构建目标库和兼容 API。采用完整备份、目标库演练、停止写入后切换的顺序；未经演练的规范化步骤不能通过日常启动自动作用到原业务库。保留旧 API 字段及离线协议，只有全部门槛通过才执行现场切换。

**Tech Stack:** Express、better-sqlite3、SQLite、Node.js 24、React、Android Java/XML、现有 Windows 控制中心。

**Spec:** [数据库 3NF 调整设计](../specs/2026-10-24-database-normalization-design.md)

## 2026-10-24 执行状态

以下是获批时的原计划及拟定文件名，保留原细项作为设计记录；实际执行采用统一 `server/scripts/normalize.ts` 的 check/rehearse/cutover/recover 子命令，避免重复 CLI。构建清单增加实际产物指纹；地址快照增加键存在位图。完整命令、失败记录、证据及覆盖限制见 [实际验收记录](../../verification/2026-10-24-database-normalization.md)。

- [x] Task 1：只读预检和逐资源语义基线；实际旧库预检通过。
- [x] Task 2：完整备份、损坏拒绝和独立恢复；明确恢复撤销会话。
- [x] Task 3：七个兼容投影和四个有序子关系；重复内容和地址缺键原义保留。
- [x] Task 4：010/011 副本升级、失败注入、自增高水位及真实数据等价对照。
- [x] Task 5：订单、审核、通知、部分/累计全额退款及三角色权限适配；最终服务端 128/128。
- [x] Task 6：旧协议回执重放及归档 1.1.6 APK 断网队列、重连上传/拉取。
- [x] Task 7：完整导出/恢复、Chrome 实际操作及买家商家模拟器联动、三轮恢复服务检查。
- [x] Task 8：启动保护、四阶段硬退出、并发维护锁、实际停写服务探针；正式切换 COMPLETE。
- [x] Task 9：当前 47 表字典、历史 40 表字典、实际验收报告及保留旧库；未上传或发布。

原计划细项中的测试名称/文件名为设计定位，不表示实现必须采用相同命名；执行状态及证据以上述记录为准。实体手机、外网、真实支付与真实断电不在已通过声明中。

## Global Constraints

- 不引入离线注册；注册继续要求连接服务。当前支付、退款、物流仍为 DEMO。
- 保留当前品牌风格、角色导航与页面功能，不把数据库改造扩展为重新设计 UI。
- 所有现有编号、金额单位、审核记录、订单事件、已读状态、用户上传图片和待同步回执保留。全额退款不得恢复成可发货订单。
- 金额为整数分；已成交单价、地址和名称不从当前资料重新补算或覆盖。
- 规范化范围为正式业务关系；同步协议载荷、回执和缓存保留明确的技术存储边界，不宣称全部技术表都已关系化。
- 保留 001—011 原迁移历史。当前数据库只读；本计划确认前不执行迁移或新增会被启动器自动发现的正式迁移文件。
- 用户数据库、完整备份、原始 API 基线、账号凭据和上传资源不进入 Git 或 APK。验收报告只输出汇总、结构问题代码和必要脱敏编号。
- 本计划中的新增文件与类型是拟实现接口，不表示当前仓库已具有这些能力。所有任务默认未完成。
- 先完成隔离验收，再进入真实库维护窗口；不能把“编译成功”“空库初始化成功”当成旧库迁移成功。

## Review Focus

1. **双写期间崩溃或重复运行**：原业务、迁移标识、库存及退款只产生一次正确结果；由任务 4、8 的失败注入和重复运行测试覆盖。
2. **旧客户端离线请求重放**：原请求摘要和去重作用域保持，已有回执不再次扣款或回库；由任务 6 的旧协议样本与重复三次重放覆盖。
3. **旧 JSON 含重复条目、无效值或未知地址键**：重复顺序保留，坏数据停止而不静默修复；由任务 1、4 的预检和回填对照测试覆盖。
4. **同结算组不同商家、部分售后多次退款**：归属不串店，金额与剩余履约数量一致；由任务 3、5、7 的权限及业务测试覆盖。
5. **恢复到旧代码或启动器误升级**：备份可恢复且配套源码版本匹配，未经验收的收敛迁移不能自动执行；由任务 2、8、9 的恢复和启动保护测试覆盖。

## 文件职责与拟定接口

`Database` 以下指 better-sqlite3 的连接类型。新增模块不导入 `core/db.ts` 的全局业务连接，由调用者显式传入连接，避免测试误连默认数据库。

| 文件 | 职责与接口 |
| --- | --- |
| `server/src/normalization/types.ts` | 定义 `SchemaPhase = 'legacy' \| 'expanded' \| 'normalized'`、`AuditIssue`、`PreflightReport`、`BusinessBaseline`、`ComparisonReport`、`MigrationManifest` |
| `server/src/normalization/preflight.ts` | `inspectNormalization(db: Database): PreflightReport`；只读结构、完整性、归属及金额校验，不修复 |
| `server/src/normalization/baseline.ts` | `captureBusinessBaseline(db: Database, phase: SchemaPhase): BusinessBaseline`；映射为旧业务合同，逐实体稳定排序和摘要 |
| `server/src/normalization/compare.ts` | `compareBusinessBaselines(before: BusinessBaseline, after: BusinessBaseline): ComparisonReport`；返回精确的差异分类及数量，不把差异当警告忽略 |
| `server/src/normalization/media.ts` | `captureMediaManifest(uploadDir: string): Array<{path: string; size: number; sha256: string}>`；拒绝越界和符号链接 |
| `server/src/normalization/relations.ts` | `readOrderedValues(db: Database, relation: OrderedRelation, parentId: string): string[]`、`replaceOrderedValues(db: Database, relation: OrderedRelation, parentId: string, values: string[]): void`；relation 仅允许设计中的四个多值子表，不接受任意 SQL 表名 |
| `server/src/normalization/projections.ts` | `installCompatibilityViews(db: Database, phase: SchemaPhase): void`；建立 `compat_orders`、`compat_order_items`、`compat_after_sales`、`compat_refunds`、`compat_ratings` 读模型，提供原字段 |
| `server/src/normalization/target-schema.sql` | 完整目标 DDL、主键、候选键、外键、索引、检查约束与必要触发器；不放进日常自动迁移目录 |
| `server/src/normalization/build-target.ts` | `buildNormalizedCopy(options: {sourcePath: string; targetPath: string; manifestPath: string}): Promise<MigrationManifest>`；只允许源与目标不同路径、目标不存在；源只读，目标失败不覆盖源 |
| `server/src/normalization/schema-guard.ts` | `assertSupportedSchema(db: Database, expected: SchemaPhase): void`；拒绝旧代码写新结构、半迁移结构及未确认的自动收敛 |
| `server/scripts/normalization-check.ts` | `--database` 显式指定只读检查目标，无默认写入或迁移动作 |
| `server/scripts/normalization-rehearse.ts` | `--source` 与 `--output` 必填；完整备份副本演练，生成私有汇总证据和恢复验证结果 |
| `server/scripts/normalization-cutover.ts` | 显式维护窗口命令，要求有效演练清单、已停止服务、当前完整备份及当前状态重新校验；本计划通过后再实现，不在日常启动中隐式调用 |
| `server/test/helpers/normalization-fixture.ts` | `createNormalizationFixture(options: {through: '010' \| '011'; case: 'standard' \| 'cross-shop-partial' \| 'legacy-notice'}): Promise<{databasePath: string; uploadDir: string; dispose(): Promise<void>}>`；仅临时目录，保留旧约束升级样本 |

类型的必要字段：

- `OrderedRelation = 'product_revision_images' | 'product_revision_contents' | 'community_post_images' | 'after_sale_images'`；名称固定，不插值任意来源的表名。
- `AuditIssue = {code: string; entity: string; count: number; blocking: boolean}`，报告中不保存电话、地址、密码或消息正文。
- `PreflightReport = {phase: SchemaPhase; integrityOk: boolean; foreignKeysOk: boolean; issues: AuditIssue[]; canProceed: boolean}`；存在任何阻断项时 canProceed=false。
- `BusinessBaseline` 包含模型版本、按资源及稳定主键排列的实体摘要、表计数、业务金额统计、事件与关联集合摘要；原始字段只在进程内比对，不写入公开报告。
- `ComparisonReport = {equal: boolean; differences: Array<{resource: string; kind: string; count: number}>}`；所有非允许技术差异使 equal=false。
- `MigrationManifest` 记录源/目标结构版本、源备份清单摘要、目标库摘要、媒体清单摘要、预检和数据对照结果、恢复演练结果、对应源码提交及构建标识。缺少任一结果不视为已验收。

除恢复工具本来会撤销的会话及迁移版本记录外，不把业务差异列入默认允许名单。恢复时会话变化应单独报告，并明确要求重新登录；正式迁移本身不顺便清理账户或回执。

## Task 1：先建立预检和数据保留基线

**Files:** 新建上述 types、preflight、baseline、compare 模块；新建 `server/test/helpers/normalization-fixture.ts`、`server/test/normalization-preflight.test.ts`、`server/test/normalization-baseline.test.ts`。

**Interfaces:** 产生 `inspectNormalization`、`captureBusinessBaseline`、`compareBusinessBaselines`；后续所有迁移和演练必须消费这份结果。

- [ ] 在临时库写失败测试 `preflight_rejects_inconsistent_ownership_without_writing`：订单买家与结算组不同或售后商家与订单不同，断言 canProceed=false；检查前后库摘要相同。
- [ ] 写失败测试 `preflight_rejects_bad_json_unknown_address_keys_and_invalid_status`：逐个注入错误 JSON、非字符串图片项、未知地址字段、错误状态映射；断言问题代码明确，不生成修复后的数据。
- [ ] 写失败测试 `baseline_detects_changed_money_identity_event_and_image_order`：分别改一分金额、订单号、事件、图片顺序与缺失关联，每次断言 compare.equal=false；无变化副本为 true。
- [ ] 实现只读 `integrity_check`、`foreign_key_check`，核对金额合计、售后归属、退款上限、库存非负、发布版本所属、已读游标所属和幂等唯一性。已有历史演示库存无预留流水时单独分类，不能反向制造库存。
- [ ] `baseline` 对已知 JSON 字段解析后比较语义并保留数组顺序；普通文本原样比较，不能 trim 后掩盖变更。忽略 SQLite rowid 重新分配，不忽略业务编号。
- [ ] 初始基线直接以只读 SQL 取得，不能调用会执行超时取消或会话续期的接口后再声称“迁移前完全未变”。涉及超时逻辑的隔离对照使用一致的测试时钟；正式服务恢复后正常发生的超时事件单独核对，不关闭生产过期规则。
- [ ] 运行：在 `server/` 执行 `node --import tsx --test test/normalization-preflight.test.ts test/normalization-baseline.test.ts`。先看到正确失败，再实现到全通过。

## Task 2：证明完整备份真的能恢复

**Files:** 新建 media 模块、`server/test/normalization-backup.test.ts`；扩展 `server/scripts/test-maintenance.mjs`，复用 `html/launcher/data-manager.mjs` 的现有完整备份、validate 和 restore 命令。

**Interfaces:** 产生媒体清单和“恢复后业务基线一致”的证据；不会把业务 CSV 当作恢复输入。

- [ ] 写失败测试 `restore_preserves_business_and_uploads`：在隔离库创建订单、两笔已完成部分退款、帖子图片、通知状态、同步回执，完整备份后恢复到另一个隔离路径；断言业务基线、媒体路径和字节摘要相等，会话撤销按现有工具单独验证。
- [ ] 写失败测试 `tampered_backup_or_missing_media_leaves_destination_unchanged`：修改库摘要、删除清单文件、增加未登记图片、使用越界路径或符号链接；逐例断言恢复失败且目标基线不变。
- [ ] 实现 `captureMediaManifest`，清单稳定排序；不把服务端内置默认图漏当成用户上传文件，也不因旧引用缺图删除引用。旧缺失资源形成基线，新缺失必须阻断。
- [ ] 备份时把库与上传文件的一致性纳入维护窗口。现有在线 SQLite backup 能保证库一致性，但文件复制不是跨数据库和图片的原子事务；正式切换前必须停止业务写入再取得最终备份。
- [ ] 在 `server/` 运行 `node --import tsx --test test/normalization-backup.test.ts` 和 `node scripts/test-maintenance.mjs`；摘要不一致、恢复未验证均不得进入切换。

## Task 3：建立兼容读模型，固定旧 API 合同

**Files:** 新建 projections、relations 模块；改动 `server/src/services/{shop-data,product-data,book-details,community-data,merchant-data,after-sale-items}.ts`，以及 `server/src/modules/{shop,admin,community}.ts` 中相应查询；新建 `server/test/normalization-contract.test.ts`。

**Interfaces:** 五个 compat 视图保留旧字段与相同类型；四类有序内容仅经 relation 白名单访问。写入仍通过现有服务事务，视图不承担任意写入。

- [ ] 写失败测试 `legacy_and_normalized_contracts_are_semantically_equal`：相同隔离数据分别由 legacy、目标结构组装商品、审核、买卖双方订单、后台订单、社区、售后、退款、通知响应；比较所有现有字段，不只比较列表长度。
- [ ] 写失败测试 `cross_shop_access_remains_forbidden`：用户访问别人的订单、商家访问另一店订单、买家访问后台，HTTP 结果与旧版一致并拒绝。
- [ ] 保持数组顺序、金额单位、字段大小写、NULL 与空串的既有区别。地址快照还原来源键；API 保留 status/logistics_status，不要求旧 App 修改本地 schema。
- [ ] 逐表列出目标候选键和函数依赖，核对 1NF/2NF/3NF。ratings 和 refunds 的唯一候选键按设计正确解释，不能用错误的范式判断扩大改造。
- [ ] 在 `server/` 运行 `node --import tsx --test test/normalization-contract.test.ts`，红灯原因须为缺少兼容实现；完成后所有合同及越权用例通过。

## Task 4：仅在副本中构建目标结构并核对历史数据

**Files:** 新建 target-schema.sql、build-target 模块和 `server/test/normalization-migration.test.ts`；正式版本号在执行时检查仓库最新迁移后分配，不提前创建可自动执行的 012 文件。

**Interfaces:** 消费任务 1—3 的预检、基线、视图；产生与源库业务基线相等的目标库和 MigrationManifest。

- [ ] 写失败测试 `migration_preserves_all_ids_snapshots_and_ordered_duplicates`：覆盖重复图片、空目录、商品后来改价、地址后来修改/删除、多个已完成售后及同名不同作者；断言源目标的原合同数据相等。
- [ ] 写失败测试 `old_010_and_011_databases_upgrade_without_reseeding`：从两个旧结构升级到候选结构，不以重置演示库替代迁移；全部旧主键、确认金额、事件、客户端去重键保留。
- [ ] 写失败测试 `failed_copy_never_changes_source_or_publishes_manifest`：在建表后、复制中、约束创建前后和清单落盘前注入失败；断言源库不变，没有“通过”清单，目标不可作为正式库启用。
- [ ] 目标表按设计建立，迁移来源只读；在独立目标连接的事务中复制业务、幂等和历史记录，不在真实源库上 DROP 表试错。保持源、目标路径不同且目标最初不存在。
- [ ] 有序图片与目录逐项回填，地址逐键映射；售后幂等关联、通知已读事实的未知时间保持 NULL；不得按当前地址、当前角色或当前价格猜测历史事实。
- [ ] 目标库在提交前校验全部外键及金额。处理 products 与 product_revisions 的循环引用时使用目标 DDL 的延迟约束或明确的事务插入顺序，不全局关闭外键跳过错误。
- [ ] 保持每张表的主键、业务唯一性、部分唯一索引与同步版本语义；复制历史事件的显式 id，校验新插入不会复用旧编号。全量数据摘要比较通过后才生成成功清单。
- [ ] 在 `server/` 运行 `node --import tsx --test test/normalization-migration.test.ts`。重复检查同一目标必须无业务副作用，重复构建同名目标应拒绝覆盖。

## Task 5：适配全部写入路径及种子初始化

**Files:** 修改 `server/src/services/{products,product-status,orders,buyer,community,customer-services,notices}.ts`、`server/src/core/{order-lifecycle,order-notifications}.ts`、`server/src/modules/admin.ts`、`server/scripts/seed.ts`；按实际引用核对 `server/src/sync/operations.ts`。扩展现有 `bookstore-flows`、`interface-completion`、`partial-after-sales`、`admin-management` 测试。

**Interfaces:** 现有 HTTP 与离线业务函数签名不变；内部写目标表和关系表，外部经任务 3 投影返回原合同。

- [ ] 先添加失败用例 `editing_published_content_still_requires_admin_review`、`checkout_ship_confirm_survives_normalization`、`partial_compensation_keeps_remaining_fulfillment`，断言可见性、归属、余额及事件内容，而不是只断言 HTTP 200。
- [ ] 商品父版本与图片/目录子表同事务写入；checkout 写结算组、店铺订单、成交明细和结构化快照同事务；仅保存一套履约状态。
- [ ] 售后写请求去重关系和所选明细同事务，退款关联经售后回订单；累计金额和恢复数量继续由原业务规则控制。重复退款三次结果、库存和流水数量不变。
- [ ] 写并发用例 `last_stock_unit_cannot_be_oversold` 与 `same_refund_cannot_complete_twice`：两个买家争抢最后一件时仅一个结算成功，库存和预留均非负；同售后并发重试只产生一笔退款与一次恢复数量。相同幂等键的并发结算返回同一结算结果，不新增第二组订单。
- [ ] 通知以“已读事实存在”和个人隐藏状态组装旧行为；已读但未知时间的历史记录仍已读，不伪造时间。
- [ ] 种子初始化同时支持完整新库；已存在账户、上传、订单和角色不重置。不能让迁移后每次启动重新 INSERT 已删除旧字段或覆盖历史快照。
- [ ] 搜索所有被删除字段的读写引用并逐项归类为兼容投影、已替换或保留快照，避免只改 REST 路由而漏掉同步与维护 SQL。
- [ ] 在 `server/` 运行 `npm test`；现有所有测试仍需通过，新增用例也要经历正确红灯和绿灯。不能修改旧断言来掩盖业务变化。

## Task 6：验证旧 App、离线队列和分页同步

**Files:** 修改 `server/src/sync/{snapshots,operations,receipts}.ts` 和 `scripts/export-android-offline.mjs` 中必要读取；新建 `server/test/normalization-sync-compat.test.ts`；扩展 `android-app/app/src/androidTest/java/com/huimeng/bookshop/core/LocalSyncTest.java`、`LocalConflictTest.java` 及 `ui/LocalFirstFlowTest.java` 中相关回归，不改本地 schema。

**Interfaces:** 同步资源名、operationId、请求载荷、payload_hash、base_version 和回执合同保持原义；不修改 Android 7 张本地技术表来绕过服务兼容。

- [ ] 写失败测试 `old_pending_requests_and_receipts_replay_exactly_once`：在 legacy 阶段生成真实旧协议请求和回执；迁移后重放已有成功操作三次，订单数、退款数、库存无新增；尚未确认操作执行一次并正确返回映射。
- [ ] 写失败测试 `multi_shop_local_order_mapping_survives_upgrade`：离线跨店结算回执保持正确的多个远端订单编号，不错误映射到单个商家。
- [ ] 写失败测试 `snapshot_paging_keeps_a_single_consistent_contract`：新旧分页缓存、过期后重取、断线恢复都保持资源内一致性；移除数据库列不能导致旧快照无法读取。
- [ ] 旧请求原始内容不重算摘要、不修改客户端幂等键；迁移已有 receipt 不改变其历史结果。新目标实体仍通过现有版本冲突机制返回明确冲突。
- [ ] 根目录运行 `npm test` 中同步相关检查，Android 在隔离包与隔离 API 上运行旧队列、重连和页面读取用例。验证旧 1.1.6 安装包可读取新服务合同，另行记录是否覆盖真实手机。

## Task 7：补齐导出、完整恢复与三角色现场验收

**Files:** 修改 `html/launcher/data-manager.mjs` 的 exportTables 和必要查询、扩展 `server/scripts/test-maintenance.mjs`；新建 `server/test/normalization-export.test.ts`；更新数据字典、售后说明及 `docs/verification/2026-10-24-database-normalization.md`（该报告仅在实际执行后创建并填入结果）。

**Interfaces:** 完整备份保留原始目标结构；业务导出包含 after_sale_items 及新业务关系表，兼容输出旧逻辑字段，继续过滤 password/token/secret 和转义 CSV 公式。

- [ ] 先写失败测试 `export_contains_partial_sale_items_and_normalized_relations`，断言所选数量、整数分金额、媒体顺序及关联编号均存在；敏感字段不存在。
- [ ] 运行构建及静态检查：根目录 `npm run build`、`npm run lint`、`npm test`；Android `:app:assembleDebug :app:lintDebug --offline`。失败不能以旧日志替代本次结果，警告分类记录。
- [ ] 独立 HTTP 服务验证商家送审、管理员通过/驳回、买家浏览；跨店下单、付款、两店订单隔离、发货、收货、评价；部分及累计全额退款、聊天、反馈、通知全过程。
- [ ] 实际浏览器检查管理员审核与订单操作、详情、售后和反馈；模拟器分别检查买家和商家。接口成功不能代替按钮点击；浏览器确认弹窗阻塞时该点击用例仍标未通过。
- [ ] 从迁移后的完整备份恢复到第三个隔离库，重新运行业务基线和用户资源摘要比较；备份损坏必须失败且不碰目标数据。
- [ ] 确认页面外观、角色导航、旧 App 登录和消息读取无非预期变化。截图、日志、接口和数据库证据分别标注，不能互相代替。

## Task 8：启动保护、失败注入和维护窗口切换

**Files:** 新建 schema-guard、normalization-check/rehearse/cutover 脚本；修改 `server/scripts/migrate.ts`、`server/src/main.ts` 和 `html/launcher/prepare.mjs` 中有关结构兼容的启动判断；新建 `server/test/normalization-cutover.test.ts`，扩展 `scripts/test-launcher-startup.mjs`。

**Interfaces:** 消费完整 MigrationManifest；只读检查与副本演练显式指定路径。现场切换不接受仅凭一个布尔参数跳过校验。

- [ ] 写失败测试 `ordinary_startup_cannot_run_unverified_cutover`：模拟日常启动旧库，未经副本验收的规范化收敛不能运行；显示明确维护提示，原库不变。
- [ ] 写失败测试 `stale_manifest_or_running_service_blocks_cutover`：备份后源数据变化、候选库变化、媒体变化、源码构建不匹配、服务仍运行时，切换拒绝且不移动原文件。
- [ ] 写失败测试 `interrupted_switch_recovers_matching_database_and_code`：在暂存、旧库保留、候选安装及启动校验各阶段注入中断；重启能够识别切换阶段，不混用新库与旧写入代码，不错误声明完成。
- [ ] 日常迁移脚本仍保留版本检查，但规范化收敛只经独立维护入口。新空库初始化、现有旧库、完整目标库、半成品库分别判断；兼容处理必须在代码和迁移打包前演练。
- [ ] `main.ts` 的 schema guard 必须先于路由模块初始化执行；当前路由存在模块加载时准备 SQL 的情况，不能仅在现有静态 import 后插入判断。使用明确的启动顺序，在校验后再加载路由。控制中心 prepare 的构建指纹纳入新增规范化脚本及结构资产，避免误用旧产物。
- [ ] 验收清单、旧版源代码与完整备份均可定位且恢复演练通过后，停止服务、App 同步及所有维护写入，重新获取最终一致性备份和基线。源变化则废弃旧候选，重新构建核对，不能继续使用过期“通过”证据。
- [ ] 切换前验证解析后的源、目标和暂存路径都位于明确指定的工作目录；拒绝相同路径、越界路径与符号链接。保留旧库和旧资源，不能先删除再尝试安装新库。
- [ ] 使用经故障注入测试的分阶段状态清单进行文件切换，考虑 Windows 文件占用、同卷重命名及 WAL/SHM。服务未停止、未完成 checkpoint 或存在连接时停止，不强删 WAL。
- [ ] 切换后先在未开放业务写入的状态检查完整性、业务基线、媒体、健康和静态资源响应；业务合同由独立只读投影核对。GET 订单接口可能执行超时取消，不能凭 HTTP 方法将它当成无副作用检查。成功后才开放写入；出现任意阻断项恢复配套旧版本与完整备份。
- [ ] 开放新写入后若需回退，先停止写入并保存新数据，制定差量保留方案；不得声称“随时恢复旧备份”而丢弃期间新订单。
- [ ] 连续启动、停止、恢复三轮，验证迁移只应用一次、既有回执不重复执行；前台登录、读写、消息和资源加载稳定后结束维护。

## Task 9：交付与发布边界

- [ ] 汇总每项门槛的实际命令、时间、环境、结果与证据路径。没有执行的项目标为未验证，任何阻断项未解决不得写“迁移成功”。
- [ ] 当前结构字典更新为最终目标结构，旧结构说明作为历史版本保留；声明正式业务表的 3NF 验证范围及技术例外。
- [ ] 备份、媒体、迁移摘要和回退入口交付给用户；记录需重新登录的情况及真实手机、外网、并发覆盖限制。
- [ ] 当前未获得上传指令，不新增 GitHub Release。后续若用户要求上传，按 AGENTS.md 同步递增 Android 版本、构建核验 APK 并保留历史版本。

## 允许切换的证据矩阵

| 门槛 | 必须看到的结果 | 不通过时 |
| --- | --- | --- |
| 当前库预检 | 完整性 ok，外键无异常，归属/状态/金额无未解释差异 | 停止，先报告现存问题 |
| 完整备份 | 库与用户资源清单一致，独立恢复验证通过 | 停止，不改真实库 |
| 副本迁移 | 逐实体业务摘要相等，编号、金额、快照、排序及事件不丢失 | 保留原库，修复迁移 |
| 写入与权限 | 三角色真实 HTTP 全链路及越权测试通过 | 不开放写入 |
| 旧客户端与同步 | 旧 App 合同兼容，重复请求不重复副作用 | 不切换 |
| 网页与 App 点击 | 实际浏览器及模拟器主要操作通过 | 未通过项继续处理，不用接口结果顶替 |
| 启动与中断 | 自动启动保护、中断恢复、重复启动均通过 | 不进入维护窗口 |
| 最终源数据 | 最终停写备份与候选基线匹配，无过期清单 | 重做候选及对照 |
| 回退 | 配套旧代码、完整备份和恢复能力均已验证 | 不切换 |

本矩阵减少已知风险并使失败可以停止和恢复，不构成对所有设备、所有未来输入“绝对零缺陷”的承诺。只有实际执行并留下证据的门槛才能标记为通过。

## 自审与执行交接

本计划已对应设计中的多值关系、归属来源、地址快照、通知兼容、技术存储边界、API/同步、导出恢复及迁移回退章节安排任务。五类 Review Focus 均有相应测试任务；目前没有任何执行项被勾选。

建议由主助手在当前会话顺序实施，原因是迁移 DDL、读写合同、同步和切换相互依赖，保持单一修改者便于控制版本。若用户明确选择子代理方式，再按适用技能委派；不得将“规划已写完”当成授权立即修改真实业务库。
