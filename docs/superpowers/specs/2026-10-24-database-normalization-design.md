# 绘梦业务数据库 3NF 调整设计

日期：2026-10-24。阶段：设计待审阅，尚未实施迁移。

## 目标与现状

用户要求：买家、商家、管理员的数据形成真实业务对应；正式业务表符合第三范式；逐表说明清楚；保留现有功能及角色界面差异。

本轮交付是当前数据字典和结构调整设计。业务代码、API、Android 私有库及业务数据库均不因本文改变。当前未提交的订单状态、商品详情局部界面修改属于上一批工作，不混入本设计。

当前技术栈为 Express、better-sqlite3、SQLite、React 管理端、Android Java/XML。业务库默认 `server/data/huimeng.db`。本次读取当前库结构时，项目运行库 SQLite 版本为 3.53.2，服务端有 40 张自定义表、306 个字段；Android `huimeng-local.db` 源码有 7 张本地技术表。字段与约束逐项见 [数据字典](../../database-dictionary.md)。

## 必须保留的业务约束

1. 商家保存商品后提交审核，由管理员审核发布。买家只读取已发布且销售中的版本；商家修改内容后继续重新送审。
2. 一次结算可跨商家，每个商家形成独立订单。商家只能查看和操作本店订单，买家只能查看自己的订单；管理员按权限处理订单并写审计。
3. 已完成部分退款后的订单仍可履约剩余数量；同笔售后不重复退款或回库，同订单可有多笔已完成退款。
4. 所有现有编号、金额单位、审核记录、订单事件、已读状态、用户上传图片和待同步回执保留。全额退款不得恢复成可发货订单。
5. 不引入离线注册；注册继续要求连接服务。当前支付、退款、物流仍为 DEMO。
6. 保留当前品牌风格、角色导航与页面功能，不把数据库改造扩展为重新设计 UI。

## 方案比较与建议

| 方案 | 做法 | 优点 | 代价与局限 |
| --- | --- | --- | --- |
| A：说明现状并保留全部结构 | 补字典，保留已有重复关联和多值字段 | 兼容成本最低 | 不能解决已经识别的严格规范化问题 |
| B：规范化正式业务模型，保留有边界的技术缓存 | 拆业务多值明细、统一归属来源、结构化成交地址；API 在边界组装原格式 | 满足业务模型规范化方向，减少客户端变化 | 需要迁移、查询适配及跨角色回归 |
| C：连同离线协议存储全部重构 | 重建本地投影、操作载荷、回执及分页缓存 | 存储形式更统一 | 范围大，旧队列和幂等兼容风险高，不属于当前必要改造 |

建议采用 B。严格 3NF 的验收范围是正式业务关系；服务端同步载荷、幂等回执、分页快照及 Android 离线投影属于明确列出的技术存储，不以“保留 JSON”冒充“全库所有表严格 3NF”。如果验收要求连所有技术存储都必须关系化，则应另立协议改造设计，不直接实施本方案。

## 规范判断依据与修正说明

3NF 应按候选键和函数依赖判断：对非平凡依赖 X → A，X 应为超键，或 A 为主属性。不能用“有自增 ID”“有外键”直接判定，也不能把所有跨表重复路径都算作同一种违规。

| 当前对象 | 已确认事实 | 设计处理 |
| --- | --- | --- |
| `orders` | 同一 `checkout_group_id` 决定买家；结算组可有多个店铺订单，该字段不是订单超键，`user_id` 也不是主属性 | 去掉订单中重复买家，按结算组关联获取；同组有效期也改从结算组读取 |
| `after_sales` | `order_id` 决定所属商家和买家；允许同订单多次已结束售后，`order_id` 不是售后超键 | 归属统一沿售后 → 订单 → 结算组读取；不保留可独立写入的重复归属 |
| `order_items.subtotal_cents` | 当前业务明确以 `unit_price_cents × quantity` 得到小计；两者组合不是该行的候选键 | 正式明细不再独立维护小计，查询层计算，API 保留原小计字段 |
| 商品、帖子、售后图片及图书目录 | JSON 数组代表可独立排序、增删的业务条目 | 拆成有顺序的子表，先满足严格关系建模的原子性要求 |
| `orders.status` 与 `logistics_status` | 当前业务状态可由履约状态映射，现有双写有不一致风险 | 存一套履约状态，服务端投影现有两个 API 字段；迁移前必须校验旧值符合映射 |
| `ratings` | `order_item_id` 有 UNIQUE，是候选键 | 不能仅因另存 `user_id/product_id` 就称其为表内 3NF 反例；为减少跨表一致性风险，目标仍改由订单明细关联取值 |
| `refunds` | `after_sale_id` 有 UNIQUE，是候选键 | 不是简单的表内传递依赖反例；目标移除重复 `order_id` 路径，按售后单关联回订单 |
| 订单和结算合计、库存摘要 | 子表汇总并不自动构成父表内的非键传递依赖 | 保留具有明确业务含义的确认金额与库存余额，增加事务校验；不机械删除所有汇总值 |
| 作者署名、作者简介 | 当前没有保证作者姓名唯一，资料按商品版本保存 | 暂不按姓名拆作者主表，避免合并同名作者或破坏版本资料 |
| `categories.parent_name` | 当前是分类类型标签，不是已有父分类实体 | 保持现义并明确注释；真正的分类树属于另一个功能需求 |

## 目标关系结构

以下均为目标设计；当前数据字典仍按原结构记载。

### 1. 商品、社区、售后的多值内容

新增四个关系表，服务端写入与父记录变更在同一事务内完成：

| 目标表 | 主键与字段 | 外键与约束 |
| --- | --- | --- |
| `product_revision_images` | `revision_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `url TEXT NOT NULL`；主键 `(revision_id, position)` | revision → product_revisions.id，ON DELETE CASCADE；位置 >= 0；URL 非空 |
| `product_revision_contents` | `revision_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `title TEXT NOT NULL`；主键 `(revision_id, position)` | revision → product_revisions.id，ON DELETE CASCADE；位置 >= 0；标题非空 |
| `community_post_images` | `post_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `url TEXT NOT NULL`；主键 `(post_id, position)` | post → community_posts.id，ON DELETE CASCADE；位置 >= 0；URL 非空 |
| `after_sale_images` | `after_sale_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `url TEXT NOT NULL`；主键 `(after_sale_id, position)` | after_sale → after_sales.id，ON DELETE RESTRICT；位置 >= 0；URL 非空 |

位置是同一父记录内的数组顺序，不按 URL 去重或重排历史数据。原数组允许重复时保留重复条目，不能仅为新唯一索引丢弃历史项。新增数量限制仍沿用现有接口，不因拆表放宽。

`cover_url` 保持为父记录的独立封面属性；封面可同时出现在详情图片中，两者用途不同。原数组字段在兼容阶段保留，仅由统一写入路径同步；完成比较验收后才从正式父表移除。失效路径或文件缺失只记录，不把它改成另一张图，也不删除原引用。

### 2. 订单与售后的唯一归属来源

目标关系与候选键：

- `checkout_groups(id PK, user_id FK, total_cents, status, version, idempotency_key, request_hash, created_at, expires_at)`，另有 UNIQUE(user_id, idempotency_key)。它保存一次结算的买家与统一有效期。
- `orders(id PK, checkout_group_id FK, merchant_id FK, total_cents, logistics_status, version, paid_at, shipped_at, completed_at, cancelled_at, created_at, updated_at)`。不再物理保存 `user_id`、`expires_at`、`status`、`address_snapshot_json`；分别由结算组、履约映射及地址快照关系组装。
- `order_items(id PK, order_id FK, product_id FK, product_name, cover_url, unit_price_cents, quantity)`。名称、封面和单价为成交时快照；小计按单价乘数量计算。不能用当前商品价格重新算历史成交单价。
- `after_sales(id PK, order_id FK, type, amount_cents, reason, description, status, merchant_note, return_address, carrier, tracking_no, version, created_at, updated_at)`。移除物理 `buyer_id/merchant_id/images_json/client_id`，client_id 由下述请求关联提供。未结束售后仍按 order_id 建部分唯一索引。
- `ratings` 以 `id` 为主键，`order_item_id UNIQUE`，移除物理 `user_id/product_id`；API 和权限校验通过订单项、订单和结算组取值。
- `refunds` 保留 `id PK, after_sale_id UNIQUE FK, amount_cents, method, status, created_at`；API 的 order_id 通过售后关联计算。同订单仍支持多笔退款。

当前售后 UNIQUE(buyer_id, client_id) 在移除 buyer_id 后需替代为独立幂等关联 `after_sale_requests(buyer_id TEXT NOT NULL FK users, client_id TEXT NOT NULL, after_sale_id TEXT NOT NULL UNIQUE FK after_sales)`，主键 `(buyer_id, client_id)`，两个外键均 ON DELETE RESTRICT。迁移保留全部旧去重编号；正式 after_sales 中移除重复的 client_id，由关联表提供该 API 值。关联表记录的是用户提交请求的幂等身份，不是可独立修改的售后归属；写入时必须验证请求买家与订单买家一致。不能把唯一约束简单改成全局 client_id 唯一。

该幂等表包含售后标识候选键，与当前已明确唯一语义一致；候选键依赖不按普通非键传递依赖误判。

履约到 API 业务状态的映射固定如下：

| logistics_status | API status |
| --- | --- |
| WAITING_PAYMENT | PENDING_PAYMENT |
| PREPARING、READY_TO_SHIP | PAID |
| SHIPPED、IN_TRANSIT、OUT_FOR_DELIVERY | SHIPPED |
| COMPLETED | COMPLETED |
| CANCELLED | CANCELLED |

未知履约值或旧值组合不一致必须在迁移前停止，不能猜测修正。退款终态仍由售后和退款流水表达，不把全额退款重写成“确认收货完成”。

### 3. 地址快照与交易事实

新增 `order_address_snapshots`，以 `order_id TEXT NOT NULL PRIMARY KEY REFERENCES orders(id)` 关联订单，ON DELETE RESTRICT。收货人、电话、地区、详细地址分别保存为列，且保留原地址快照中现有的来源地址编号、来源用户编号、默认标志、地址创建/更新时间及版本字段的原值。

明确目标字段：`order_id`、`source_address_id`、`source_user_id`、`recipient`、`phone`、`region`、`detail`、`source_is_default`、`source_created_at`、`source_updated_at`、`source_version`。来源编号是历史追踪值，不设指向可删除当前地址的外键；只有 order_id 是数据库外键。已存在快照缺少可选元数据时允许 NULL，不能从当前地址补填一个不存在的历史值。

兼容层将来源字段还原为原 JSON 键 `id/user_id/is_default/created_at/updated_at/version`，继续输出 `address_snapshot_json` 和管理员 `address`。迁移前检查键集合：若出现上述已知字段外的历史扩展键，先记录其结构并补充映射设计，停止自动迁移。完整旧快照同时保留在受控迁移备份中。

成交商品名称、封面和单价继续保存在 `order_items`，作为当时事实。商家后来改名、调价、换封面，不能改变旧订单。审计中的操作者名称也是当时事实，不用现有昵称覆盖。支付金额、退款流水不按未来商品价格重新计算。

订单和结算确认金额保持整数分，写入时核对订单项小计、店铺订单合计及结算合计；售后申请金额核对所选明细合计，退款累计不得超过成交金额。汇总一致性不能靠范式名称替代。

### 4. 通知兼容与数据库约束

- 已读事实统一到新关系 `notification_read_receipts(notification_id TEXT NOT NULL FK notifications, user_id TEXT NOT NULL FK users, read_at TEXT NULL)`，主键 `(notification_id,user_id)`，两个外键 ON DELETE CASCADE。记录存在即已读；read_at 可空表示历史已读时间未知。新操作写入真实已读时刻，不生成伪造的历史时间。
- `notification_user_state` 最终只保存 `(notification_id,user_id,deleted_at)` 的个人隐藏状态，退出重复的 read_at 字段。先归并现有个人状态、`notification_reads` 和定向通知旧 is_read/read_at；有已读标志但无时间的旧记录仍建立已读事实，时间保持 NULL。多个来源都存在时间时优先个人状态时间，其次旧关联时间，再其次定向通知时间；冲突值记入迁移报告并由原备份保留。
- 兼容阶段保留旧读取语义；结束后通知正文表不再独立维护个人 is_read/read_at，旧 `notification_reads` 从活动模型退出。旧表和备份在验收完成前不删除，现有 API 继续输出相同的 is_read 与个人隐藏行为。
- 所有目标主键明确 NOT NULL；枚举和 0/1 标志按业务补 CHECK，金额与数量采用现有单位、范围及有效性校验。
- 增加 `products.published_revision_id` 的引用完整性校验，并保证版本属于该商品且允许发布。单纯指向 revision.id 的外键不能独自保证“版本属于本商品”，需要在事务业务规则或经测试的组合约束中落实。
- 关联售后项必须属于售后所指订单；会话已读消息必须属于该会话。现有单列外键无法覆盖的跨表条件纳入事务校验和回归测试。
- 交易、审批和审计不因账户或商品停用级联删除。新增明细表的删除动作按父实体的既有保留规则选择，不能统一套用 CASCADE。

## 技术存储与 3NF 范围边界

保留服务端 `sync_receipts.result_json`、`sync_snapshots.data_json`，以及 Android `local_entities`、`sync_operations` 等技术表的 JSON 存储。它们分别是幂等回执、固定分页快照和待同步协议载荷，允许与正式业务结构不同。

`settings` 是配置键值，`schema_migrations` 是迁移版本登记；它们不需要为了形式上的一致拆成业务实体。角色快照、消息发送身份及联系人申请资料按事实语义处理，不强制从用户当前角色或当前电话派生。

对外结论应表述为“正式业务模型按 3NF 设计，快照、缓存和协议记录有明确说明”，不能写成“所有 47 张表全部通过严格 3NF 证明”。实施前还需以目标 DDL 逐表列出候选键与函数依赖，检查是否存在尚未识别的部分依赖或传递依赖。当前设计不把未做的完整形式化证明当作已完成验收。

## API、同步与界面兼容

所有现有路由及客户端响应字段保持不变；数据库拆表通过共享服务层和序列化层适配：

| 使用位置 | 需要保持的外部行为 |
| --- | --- |
| `server/src/services/shop-data.ts`、`modules/shop.ts` | 商品图书目录和图片继续输出数组；买家、商家订单继续获得原 user_id、merchant_id、status、expires_at 及快照字段 |
| `services/orders.ts`、`core/order-lifecycle.ts` | 原结算、付款、取消、发货、收货接口、金额与状态迁移不变；权限仍在 SQL 查询和业务事务中校验 |
| `services/products.ts`、`product-data.ts`、`book-details.ts` | 图片与目录从子表组装，审核按版本读取；维护内容在同一事务写入父子表 |
| `services/customer-services.ts`、`after-sale-items.ts` | 售后仍返回原 buyer_id/merchant_id/client_id/order_id；重复申请与退款保持幂等 |
| `modules/admin.ts` | 订单和审核详情继续完整展示，操作版本检查、理由、事件和审计保留 |
| `services/notice-data.ts` | 历史和新通知的已读、隐藏行为一致 |
| `sync/snapshots.ts`、`sync/` | 响应 envelope、字段和资源名称保持；旧 pending 请求及其摘要不能因迁移被改写；已有 receipts 继续复用 |
| `html/launcher/data-manager.mjs` | 完整备份可恢复新旧结构；业务导出增加售后明细和新拆分业务表，维持敏感字段过滤及 CSV 公式转义 |
| `scripts/export-android-offline.mjs` | 导出数据继续满足原 Android 导入格式，不把新的内部 SQL 表名当成协议资源名 |

旧客户端可继续读取原数组、状态和地址对象，不强制同步升级 Android 本地数据库。避免双份数据长期双写；兼容字段只作为迁移过渡，由同一事务更新，并设定验收后退出条件。

## 迁移、回退与数据保护设计

1. **只读预检**：确认迁移版本、表结构、完整性、外键、订单归属、状态映射、JSON 数组类型、地址键集合、唯一约束和金额合计；异常先出结构化报告并停止。不能静默去重、补金额或丢弃无效条目。
2. **完整备份**：使用现有 SQLite 一致性备份机制，包含业务库、上传文件及清单，验证副本可打开。业务 CSV 不替代此备份。备份不进 Git 或 APK。
3. **隔离库演练**：先在备份副本应用新迁移，验证全部旧版本到新版本的升级，检查行数、编号、顺序、金额及对外 JSON。不得以空库 seed 通过代替旧库升级通过。
4. **兼容发布阶段**：增量建立子表和兼容读写层，回填历史数据，逐项比较新旧结果；保留旧物理字段直到验收。系统在该阶段不能标记为“物理结构已全部 3NF”。
5. **收敛阶段**：兼容验证通过后，在停止业务写入的维护窗口内执行已演练的表重建和旧字段退出。具体 SQL、外键依赖顺序、索引触发器重建及提交前检查留待实施计划确定，不直接执行本文结构草案。
6. **最终校验**：`integrity_check`、`foreign_key_check`、明细及状态统计、HTTP 合同和旧队列重放全部通过后恢复服务。迁移记录与结构变更原子提交；失败不得留下半迁移记录。
7. **回退**：失败时回滚未提交事务；若已发布后出现问题，停止写入并按完整备份恢复库与上传资源，再运行相应源码版本。不直接覆盖正在写入的 DB 文件，不单独丢弃 WAL；恢复后会话处理遵循现有工具。已在新结构写入的业务须先核对与保存，不能无条件丢弃恢复点之后的数据。

迁移命名继续递增，保留 001—011 原文件；实施时按仓库当时最新版本分配新编号，不预先占用 012。控制中心启动会自动应用迁移，因此正式迁移文件只在升级路径、恢复及旧客户端兼容演练通过后纳入可运行版本。

## 验收标准

本轮文档验收：40 张服务端表和全部字段有说明；7 张本地技术表有边界说明；数据字典与当前 DDL、默认值、外键、索引匹配；所有内部链接有效；不包含业务行数据；业务库摘要不变。

后续实施验收：

- 正式业务目标表逐表列明候选键及函数依赖，确认 1NF、2NF、3NF；保留的成交快照和技术例外有明确语义，不混写为全库证明。
- 原商品、订单、售后、支付、退款、账户、日志编号与记录不丢失，图片和目录顺序保持，地址及成交信息不随当前资料变化。
- 商家送审 → 后台通过/驳回 → 买家可见性；买家跨店下单 → 双店隔离 → 付款 → 发货 → 收货评价，真实 HTTP 测试通过。
- 部分数量、部分金额补偿、再次申请、累计全额、退货回库、重复退款重试均正确；全额退款后无履约操作。
- 后台订单管理、详情、图文编辑、个人通知状态及业务导出/完整恢复通过；实际浏览器和模拟器分别验证，不能用接口测试替代点击验收。
- 旧 Android 待同步操作、幂等键、已确认回执及分页快照兼容；迁移前后的同一账号数据隔离与私聊权限一致。
- 故意损坏 JSON、归属不一致、状态不一致、旧外键缺失等输入会安全终止迁移；中断与重启不产生半升级或重复扣款回库。
- 更新数据字典、售后说明及验收报告，源码发布时按项目规则另行构建并核验新版本 APK。

## 当前交付边界

本设计只提出目标结构和迁移保护要求，不包含可直接运行的 ALTER/DROP 脚本。尚未调整业务 SQL、导出清单、Android schema 或执行真实库迁移，也未新增发布版本。

审阅本设计时重点确认：选择 B 的业务/技术存储范围、成交快照保留、原 API 与旧队列兼容，以及先演练后迁移的顺序。设计确认后再编写具体实施计划及迁移测试，之后按批准范围实施。
