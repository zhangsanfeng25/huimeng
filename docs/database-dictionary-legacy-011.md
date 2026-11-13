# 绘梦数据库数据字典（迁移前 001—011 历史结构）

核对日期：2026-10-24。本文保留正式切换前的 40 表结构。当前 47 表见 [现用字典](database-dictionary.md)；下文“当前”指迁移前核对时点。

## 阅读与维护约定

- 服务端默认文件：`server/data/huimeng.db`，实际路径以 `DATABASE_PATH` 配置为准。网页与 Android 通过同一 API 访问它。
- 服务端本次只读检查使用项目的 better-sqlite3 运行库，SQLite 3.53.2；共 40 张自定义表，不含 SQLite 内部表。
- 字段、类型、默认值、主外键、索引及建表语句来自当前库的结构元数据；中文用途依据迁移文件与实际读写代码。没有导出账户、密码摘要、会话或聊天内容。
- SQLite 的 TEXT/INTEGER 是声明类型；整数金额统一以分计，不能直接当成元。0/1 标志是否受 CHECK 保护，以 DDL 为准。
- “未声明 NOT NULL”忠实反映现有 DDL；普通 rowid 表的 TEXT 主键不自动等同显式 NOT NULL，业务必填不等于数据库已禁止 NULL。新设计需补强该约束。
- 表内外键只列已声明约束；逻辑关联另在说明中注明，不把未声明的关联写成数据库保证。默认 NO ACTION 与显式 CASCADE/SET NULL 不同。
- 版本、状态、幂等编号的含义按所在表解释；状态字段不都带枚举 CHECK。UTC 字符串、日期与 Android 毫秒整数不能混用。
- 本文 SQL 是结构展示，不是要执行的迁移脚本。SQLite 没有 MySQL 式 COMMENT 存储机制，逐表说明在本文维护。变更应新增迁移并更新本文，不改写旧迁移历史。
- 规范化设计及保留快照的理由见 [3NF 调整设计](superpowers/specs/2026-10-24-database-normalization-design.md)。Android 私有库见文末附录。

## 服务端表总览

| 表名 | 中文名称 | 类型 | 用途 |
| --- | --- | --- | --- |
| `users` | 用户账户 | 身份与权限 | 保存登录标识、公开资料和密码摘要；买家、商家、管理员共用账户实体。 |
| `user_roles` | 账户角色关联 | 身份与权限 | 一个账户可拥有多个角色，角色切换不复制账户。 |
| `sessions` | 登录会话 | 身份与权限 | 保存令牌摘要、有效期和当前使用的角色。 |
| `merchants` | 商家与店铺 | 商家与商品 | 保存店铺、负责人、入驻联系资料和审批经营状态。 |
| `categories` | 商品分类 | 商家与商品 | 保存分类名称、分类类型标签、排序及启用状态。 |
| `products` | 商品主档 | 商家与商品 | 保存所属商家、分类、发布版本、销售状态及库存。 |
| `product_revisions` | 商品内容修订版 | 商家与商品 | 保存每个商品版本的图文、图书资料、价格和审核记录。 |
| `addresses` | 用户收货地址 | 购物与交易 | 保存用户可维护的当前地址。 |
| `favorites` | 商品收藏关联 | 购物与交易 | 记录用户收藏的商品。 |
| `cart_items` | 购物车明细 | 购物与交易 | 记录用户、商品、数量和是否参与结算。 |
| `checkout_groups` | 跨店铺结算组 | 购物与交易 | 一次结算对应一个买家，可按店铺拆出多张订单。 |
| `orders` | 店铺子订单 | 购物与交易 | 一次结算按商家拆分的订单，保存买家、履约状态及成交地址快照。 |
| `order_items` | 成交商品明细 | 购物与交易 | 保存成交时商品名称、封面、单价和购买数量。 |
| `payments` | 结算支付记录 | 购物与交易 | 记录整个结算组的演示付款。 |
| `order_events` | 订单操作事件 | 购物与交易 | 保存创建、付款、发货、收货等已发生的状态变更。 |
| `stock_movements` | 库存变动流水 | 购物与交易 | 记录商品可用与预留库存的增减原因。 |
| `ratings` | 订单商品评价 | 社区与评价 | 记录已购商品评价、星级及可见性。 |
| `community_posts` | 社区帖子 | 社区与评价 | 保存买家或商家的阅读分享、可见性和关联商品。 |
| `comments` | 社区评论 | 社区与评价 | 保存帖子下的用户评论。 |
| `post_likes` | 社区点赞关联 | 社区与评价 | 记录用户对帖子的点赞关系。 |
| `community_uploads` | 上传图片登记 | 社区与评价 | 登记上传文件路径、所有者及可选帖子归属，也用于校验商家上传图片。 |
| `notifications` | 系统通知 | 通知与消息 | 保存定向或广播通知正文与受众。 |
| `notification_reads` | 旧通知已读关联 | 通知与消息 | 历史迁移遗留的通知已读记录。 |
| `notification_user_state` | 个人通知状态 | 通知与消息 | 每个用户对一条通知的已读和个人隐藏状态。 |
| `conversations` | 买家店铺会话 | 通知与消息 | 为同一买家和商家建立固定私聊会话。 |
| `chat_messages` | 会话消息 | 通知与消息 | 保存消息正文、发送身份及可选商品订单上下文。 |
| `conversation_reads` | 会话已读游标 | 通知与消息 | 保存会话中买家或商家一侧最后已读的消息编号。 |
| `after_sales` | 售后申请 | 售后与反馈 | 保存售后类型、申请金额、原因、审核退货状态。 |
| `after_sale_items` | 售后商品明细 | 售后与反馈 | 保存每次申请选中的订单商品、数量、金额及回库/取消数量。 |
| `after_sale_events` | 售后处理事件 | 售后与反馈 | 保存商家初审、平台复核、寄回、收货及退款等处理历史。 |
| `refunds` | 演示退款流水 | 售后与反馈 | 保存每笔完成的演示退款，用于累计订单退款金额。 |
| `feedbacks` | 意见反馈 | 售后与反馈 | 保存用户或商家提交的问题和处理状态。 |
| `feedback_replies` | 反馈沟通记录 | 售后与反馈 | 保存平台回复和提交人的后续补充。 |
| `audit_logs` | 业务审计日志 | 系统与同步 | 保存后台关键管理动作、对象及结果。 |
| `settings` | 服务设置 | 系统与同步 | 按设置键保存服务配置值。 |
| `schema_migrations` | 数据库迁移记录 | 系统与同步 | 登记已应用的迁移文件版本，防止重复执行。 |
| `sync_receipts` | 服务端同步回执 | 系统与同步 | 保存离线操作的幂等回执与请求摘要。 |
| `sync_entity_map` | 本地远程实体映射 | 系统与同步 | 映射账号资源中的本地编号与服务端编号。 |
| `sync_snapshots` | 分页同步快照 | 系统与同步 | 固定一次同步分页期间的数据集合，避免分页读取不一致。 |
| `sync_media` | 同步媒体处理记录 | 系统与同步 | 保存离线图片上传操作的摘要、地址与阶段。 |

## 身份与权限

### users — 用户账户

用途：保存登录标识、公开资料和密码摘要；买家、商家、管理员共用账户实体。

主键：`id`。

维护说明：用户名、邮箱、电话各自唯一；SQLite UNIQUE 允许多个 NULL。密码摘要与邮箱电话不作为公开资料导出。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `username` | TEXT | NOT NULL | 无显式默认 | 登录用户名 |
| `email` | TEXT | 未声明 NOT NULL | 无显式默认 | 绑定邮箱，可未填写 |
| `phone` | TEXT | 未声明 NOT NULL | 无显式默认 | 联系电话 |
| `nickname` | TEXT | NOT NULL | 无显式默认 | 公开昵称 |
| `password_hash` | TEXT | NOT NULL | 无显式默认 | 密码校验摘要，非明文密码 |
| `avatar_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 头像资源地址 |
| `status` | TEXT | NOT NULL | 'ACTIVE' | 业务状态，取值及限制见下方 DDL |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |

外键：

本表没有已声明外键；逻辑关联以用途与维护说明为准。

索引与唯一性：

- `sqlite_autoindex_users_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_users_2`：唯一索引，字段 `username`。
- `sqlite_autoindex_users_3`：唯一索引，字段 `email`。
- `sqlite_autoindex_users_4`：唯一索引，字段 `phone`。

触发器：

- `users_sync_version`：更新时若版本未变则递增同步版本；详情以迁移 008 为准。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  nickname TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  avatar_url TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
, version INTEGER NOT NULL DEFAULT 1);
```

</details>

### user_roles — 账户角色关联

用途：一个账户可拥有多个角色，角色切换不复制账户。

主键：`user_id` + `role`。

维护说明：复合主键防止重复角色；取得商家角色仍需通过商家审批与营业状态校验。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `role`（主键2） | TEXT | NOT NULL | 无显式默认 | 角色代码 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_user_roles_1`：唯一索引，字段 `user_id` + `role`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE user_roles (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK(role IN ('USER','MERCHANT','ADMIN','OPERATOR','SUPPORT')),
  PRIMARY KEY(user_id, role)
);
```

</details>

### sessions — 登录会话

用途：保存令牌摘要、有效期和当前使用的角色。

主键：`token_hash`。

维护说明：只存令牌摘要；退出、密码修改后会话应撤销。active_role 本身没有角色枚举 CHECK，权限仍由服务端复核。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `token_hash`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 登录令牌的摘要 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `active_role` | TEXT | NOT NULL | 无显式默认 | 会话当前使用的角色 |
| `expires_at` | TEXT | NOT NULL | 无显式默认 | 过期时间 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_sessions_1`：唯一索引，字段 `token_hash`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  active_role TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
```

</details>

## 商家与商品

### merchants — 商家与店铺

用途：保存店铺、负责人、入驻联系资料和审批经营状态。

主键：`id`。

维护说明：负责人唯一；联系资料是入驻申请资料，不能自动随账户联系方式覆盖。删除应保留历史交易。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `owner_user_id` | TEXT | NOT NULL | 无显式默认 | 商家负责人用户编号 |
| `shop_name` | TEXT | NOT NULL | 无显式默认 | 店铺名称 |
| `description` | TEXT | NOT NULL | '' | 说明正文 |
| `approval_status` | TEXT | NOT NULL | 'PENDING' | 入驻审批状态：PENDING / APPROVED / REJECTED |
| `business_status` | TEXT | NOT NULL | 'PENDING' | 经营状态：PENDING / OPEN / SUSPENDED |
| `rejection_reason` | TEXT | 未声明 NOT NULL | 无显式默认 | 驳回原因 |
| `applied_at` | TEXT | NOT NULL | 无显式默认 | 入驻申请或迁移应用时间 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `contact_name` | TEXT | NOT NULL | '' | 入驻联系人姓名 |
| `contact_phone` | TEXT | NOT NULL | '' | 入驻联系电话 |
| `business_address` | TEXT | NOT NULL | '' | 入驻经营地址 |
| `reviewed_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 审核时间 |
| `reviewed_by` | TEXT | 未声明 NOT NULL | 无显式默认 | 审核操作者用户编号 |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |

外键：

- `reviewed_by` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `owner_user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_merchants_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_merchants_2`：唯一索引，字段 `owner_user_id`。

触发器：

- `merchants_sync_version`：更新时若版本未变则递增同步版本；详情以迁移 008 为准。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE merchants (
  id TEXT PRIMARY KEY,
  owner_user_id TEXT NOT NULL UNIQUE REFERENCES users(id),
  shop_name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  approval_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(approval_status IN ('PENDING','APPROVED','REJECTED')),
  business_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(business_status IN ('PENDING','OPEN','SUSPENDED')),
  rejection_reason TEXT,
  applied_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
, contact_name TEXT NOT NULL DEFAULT '', contact_phone TEXT NOT NULL DEFAULT '', business_address TEXT NOT NULL DEFAULT '', reviewed_at TEXT, reviewed_by TEXT REFERENCES users(id), version INTEGER NOT NULL DEFAULT 1);
```

</details>

### categories — 商品分类

用途：保存分类名称、分类类型标签、排序及启用状态。

主键：`id`。

维护说明：parent_name 当前为分类类型标签，未建成父分类实体；不能把它误认为外键。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `name` | TEXT | NOT NULL | 无显式默认 | 名称 |
| `parent_name` | TEXT | NOT NULL | 无显式默认 | 分类类型标签；目前不是父分类外键 |
| `sort_order` | INTEGER | NOT NULL | 0 | 显示排序值 |
| `status` | TEXT | NOT NULL | 'ENABLED' | 业务状态，取值及限制见下方 DDL |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

本表没有已声明外键；逻辑关联以用途与维护说明为准。

索引与唯一性：

- `sqlite_autoindex_categories_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_categories_2`：唯一索引，字段 `name`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE categories (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  parent_name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ENABLED' CHECK(status IN ('ENABLED','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

### products — 商品主档

用途：保存所属商家、分类、发布版本、销售状态及库存。

主键：`id`。

维护说明：published_revision_id 当前没有声明数据库外键；审核通过时更新发布指针。stock_available、stock_reserved 必须与库存流水在同一事务维护。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `merchant_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联商家编号 |
| `category_id` | TEXT | NOT NULL | 无显式默认 | 商品分类编号 |
| `published_revision_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 当前对买家发布的商品修订版编号 |
| `status` | TEXT | NOT NULL | 'DRAFT' | 业务状态，取值及限制见下方 DDL |
| `stock_available` | INTEGER | NOT NULL | 0 | 当前可用库存数量，非负整数 |
| `stock_reserved` | INTEGER | NOT NULL | 0 | 结算占用的预留库存数量，非负整数 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |

外键：

- `category_id` → `categories.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `merchant_id` → `merchants.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_product_merchant`：普通索引，字段 `merchant_id`。定义：`CREATE INDEX idx_product_merchant ON products(merchant_id)`
- `idx_product_status`：普通索引，字段 `status`。定义：`CREATE INDEX idx_product_status ON products(status)`
- `sqlite_autoindex_products_1`：唯一索引，字段 `id`。

触发器：

- `products_sync_version`：更新时若版本未变则递增同步版本；详情以迁移 008 为准。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE products (
  id TEXT PRIMARY KEY,
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  category_id TEXT NOT NULL REFERENCES categories(id),
  published_revision_id TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING_REVIEW','ONLINE','OFFLINE','REJECTED','PLATFORM_OFFLINE')),
  stock_available INTEGER NOT NULL DEFAULT 0 CHECK(stock_available >= 0),
  stock_reserved INTEGER NOT NULL DEFAULT 0 CHECK(stock_reserved >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
, version INTEGER NOT NULL DEFAULT 1);
```

</details>

### product_revisions — 商品内容修订版

用途：保存每个商品版本的图文、图书资料、价格和审核记录。

主键：`id`。

维护说明：商品与版本号联合唯一；图片及目录为 JSON 集合，列入规范化方案。作者名不保证唯一，不能直接据姓名推断作者实体或传递依赖。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `product_id` | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `version` | INTEGER | NOT NULL | 无显式默认 | 同一商品的修订序号，与 products.version 的同步版本不同 |
| `name` | TEXT | NOT NULL | 无显式默认 | 名称 |
| `summary` | TEXT | NOT NULL | 无显式默认 | 内容摘要 |
| `age_range` | TEXT | NOT NULL | 无显式默认 | 适龄范围文本 |
| `price_cents` | INTEGER | NOT NULL | 无显式默认 | 商品版本价格，整数分 |
| `cover_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 封面资源地址 |
| `detail_images_json` | TEXT | NOT NULL | '[]' | 详情图片 URL 有序数组的 JSON 文本 |
| `audio_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 配套音频资源地址 |
| `ar_enabled` | INTEGER | NOT NULL | 0 | AR 标志，0/1；字段存在不代表已实现完整 AR 功能 |
| `ar_resource_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 可选 AR 资源地址 |
| `review_status` | TEXT | NOT NULL | 'DRAFT' | 版本审核状态：DRAFT / PENDING / APPROVED / REJECTED |
| `rejection_reason` | TEXT | 未声明 NOT NULL | 无显式默认 | 驳回原因 |
| `submitted_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 提交审核时间 |
| `reviewed_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 审核时间 |
| `reviewed_by` | TEXT | 未声明 NOT NULL | 无显式默认 | 审核操作者用户编号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `author_name` | TEXT | NOT NULL | '' | 本版本文字作者署名 |
| `illustrator_name` | TEXT | NOT NULL | '' | 本版本绘图作者署名 |
| `publisher` | TEXT | NOT NULL | '' | 本版本发行出版社文本 |
| `publication_date` | TEXT | NOT NULL | '' | 发售日期，YYYY-MM-DD 或空串 |
| `author_bio` | TEXT | NOT NULL | '' | 本版本作者简介 |
| `illustrator_bio` | TEXT | NOT NULL | '' | 本版本绘图作者简介 |
| `contents_json` | TEXT | NOT NULL | '[]' | 图书目录条目有序数组的 JSON 文本 |
| `editorial_review` | TEXT | NOT NULL | '' | 编辑推荐正文 |
| `metadata_source` | TEXT | NOT NULL | '' | 图书资料来源标志，如 fictional_demo / manual |

外键：

- `reviewed_by` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `product_id` → `products.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_product_revisions_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_product_revisions_2`：唯一索引，字段 `product_id` + `version`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE product_revisions (
  id TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  name TEXT NOT NULL,
  summary TEXT NOT NULL,
  age_range TEXT NOT NULL,
  price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
  cover_url TEXT,
  detail_images_json TEXT NOT NULL DEFAULT '[]',
  audio_url TEXT,
  ar_enabled INTEGER NOT NULL DEFAULT 0,
  ar_resource_url TEXT,
  review_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
  rejection_reason TEXT,
  submitted_at TEXT,
  reviewed_at TEXT,
  reviewed_by TEXT REFERENCES users(id),
  created_at TEXT NOT NULL, author_name TEXT NOT NULL DEFAULT '', illustrator_name TEXT NOT NULL DEFAULT '', publisher TEXT NOT NULL DEFAULT '', publication_date TEXT NOT NULL DEFAULT '', author_bio TEXT NOT NULL DEFAULT '', illustrator_bio TEXT NOT NULL DEFAULT '', contents_json TEXT NOT NULL DEFAULT '[]', editorial_review TEXT NOT NULL DEFAULT '', metadata_source TEXT NOT NULL DEFAULT '',
  UNIQUE(product_id, version)
);
```

</details>

## 购物与交易

### addresses — 用户收货地址

用途：保存用户可维护的当前地址。

主键：`id`。

维护说明：订单保存成交地址快照，修改或删除本表不应改变旧订单。默认地址唯一主要由业务事务维护，现有 DDL 没有部分唯一索引。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `recipient` | TEXT | NOT NULL | 无显式默认 | 收货人 |
| `phone` | TEXT | NOT NULL | 无显式默认 | 联系电话 |
| `region` | TEXT | NOT NULL | 无显式默认 | 收货地区文本 |
| `detail` | TEXT | NOT NULL | 无显式默认 | 地址详细部分 |
| `is_default` | INTEGER | NOT NULL | 0 | 默认地址标志，0/1 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_addresses_1`：唯一索引，字段 `id`。

触发器：

- `addresses_sync_version`：更新时若版本未变则递增同步版本；详情以迁移 008 为准。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE addresses (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient TEXT NOT NULL,
  phone TEXT NOT NULL,
  region TEXT NOT NULL,
  detail TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
, version INTEGER NOT NULL DEFAULT 1);
```

</details>

### favorites — 商品收藏关联

用途：记录用户收藏的商品。

主键：`user_id` + `product_id`。

维护说明：用户与商品复合主键，重复收藏不产生第二条记录。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `product_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `product_id` → `products.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。
- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_favorites_1`：唯一索引，字段 `user_id` + `product_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE favorites (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id, product_id)
);
```

</details>

### cart_items — 购物车明细

用途：记录用户、商品、数量和是否参与结算。

主键：`user_id` + `product_id`。

维护说明：用户与商品复合主键；数量大于零，真实库存和价格在结算时重新校验。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `product_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `quantity` | INTEGER | NOT NULL | 无显式默认 | 商品数量 |
| `selected` | INTEGER | NOT NULL | 1 | 购物车选中标志，0/1 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `product_id` → `products.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。
- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_cart_items_1`：唯一索引，字段 `user_id` + `product_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE cart_items (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  selected INTEGER NOT NULL DEFAULT 1,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(user_id, product_id)
);
```

</details>

### checkout_groups — 跨店铺结算组

用途：一次结算对应一个买家，可按店铺拆出多张订单。

主键：`id`。

维护说明：用户与幂等键联合唯一；总金额为结算金额，重复付款和超时关闭需统一处理整组。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `total_cents` | INTEGER | NOT NULL | 无显式默认 | 订单或结算总金额，整数分 |
| `status` | TEXT | NOT NULL | 无显式默认 | 业务状态，取值及限制见下方 DDL |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |
| `idempotency_key` | TEXT | NOT NULL | 无显式默认 | 业务幂等键 |
| `request_hash` | TEXT | NOT NULL | 无显式默认 | 请求内容摘要 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `expires_at` | TEXT | NOT NULL | 无显式默认 | 过期时间 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_checkout_groups_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_checkout_groups_2`：唯一索引，字段 `user_id` + `idempotency_key`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE checkout_groups (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  total_cents INTEGER NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('PENDING','PAID','CLOSED')),
  version INTEGER NOT NULL DEFAULT 1,
  idempotency_key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  UNIQUE(user_id, idempotency_key)
);
```

</details>

### orders — 店铺子订单

用途：一次结算按商家拆分的订单，保存买家、履约状态及成交地址快照。

主键：`id`。

维护说明：checkout_group_id 可确定买家，user_id 为重复关联；同组有效期也重复保存。订单业务状态与物流状态需统一映射。见 3NF 设计。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `checkout_group_id` | TEXT | NOT NULL | 无显式默认 | 所属结算组编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `merchant_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联商家编号 |
| `address_snapshot_json` | TEXT | NOT NULL | 无显式默认 | 下单时完整地址快照 JSON；不随当前地址变化 |
| `total_cents` | INTEGER | NOT NULL | 无显式默认 | 订单或结算总金额，整数分 |
| `status` | TEXT | NOT NULL | 无显式默认 | 业务状态，取值及限制见下方 DDL |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |
| `expires_at` | TEXT | NOT NULL | 无显式默认 | 过期时间 |
| `paid_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 支付时间 |
| `shipped_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 发货时间 |
| `completed_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 完成时间 |
| `cancelled_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 取消时间 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `logistics_status` | TEXT | NOT NULL | 'WAITING_PAYMENT' | 履约状态代码，映射订单业务状态 |

外键：

- `merchant_id` → `merchants.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `checkout_group_id` → `checkout_groups.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_order_merchant_status`：普通索引，字段 `merchant_id` + `status`。定义：`CREATE INDEX idx_order_merchant_status ON orders(merchant_id, status)`
- `idx_order_user_status`：普通索引，字段 `user_id` + `status`。定义：`CREATE INDEX idx_order_user_status ON orders(user_id, status)`
- `sqlite_autoindex_orders_1`：唯一索引，字段 `id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE orders (
  id TEXT PRIMARY KEY,
  checkout_group_id TEXT NOT NULL REFERENCES checkout_groups(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  address_snapshot_json TEXT NOT NULL,
  total_cents INTEGER NOT NULL CHECK(total_cents >= 0),
  status TEXT NOT NULL CHECK(status IN ('PENDING_PAYMENT','PAID','SHIPPED','COMPLETED','CANCELLED')),
  version INTEGER NOT NULL DEFAULT 1,
  expires_at TEXT NOT NULL,
  paid_at TEXT,
  shipped_at TEXT,
  completed_at TEXT,
  cancelled_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
, logistics_status TEXT NOT NULL DEFAULT 'WAITING_PAYMENT');
```

</details>

### order_items — 成交商品明细

用途：保存成交时商品名称、封面、单价和购买数量。

主键：`id`。

维护说明：product_name、cover_url、unit_price_cents 是成交快照，不从商品当前版本反向覆盖；subtotal_cents 当前按单价乘数量计算，存在可派生值。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `order_id` | TEXT | NOT NULL | 无显式默认 | 关联订单编号 |
| `product_id` | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `product_name` | TEXT | NOT NULL | 无显式默认 | 成交时商品名称快照 |
| `cover_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 封面资源地址 |
| `unit_price_cents` | INTEGER | NOT NULL | 无显式默认 | 成交时商品单价，整数分 |
| `quantity` | INTEGER | NOT NULL | 无显式默认 | 商品数量 |
| `subtotal_cents` | INTEGER | NOT NULL | 无显式默认 | 成交商品小计，当前为单价乘数量，整数分 |

外键：

- `product_id` → `products.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `order_id` → `orders.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_order_items_1`：唯一索引，字段 `id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE order_items (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id),
  product_name TEXT NOT NULL,
  cover_url TEXT,
  unit_price_cents INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0),
  subtotal_cents INTEGER NOT NULL
);
```

</details>

### payments — 结算支付记录

用途：记录整个结算组的演示付款。

主键：`id`。

维护说明：每个结算组最多一笔支付；支付幂等键唯一。目前没有接入真实支付渠道。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `checkout_group_id` | TEXT | NOT NULL | 无显式默认 | 所属结算组编号 |
| `amount_cents` | INTEGER | NOT NULL | 无显式默认 | 本记录金额，整数分 |
| `method` | TEXT | NOT NULL | 'DEMO' | 当前付款方式如 DEMO_BALANCE / DEMO_ALIPAY / DEMO_WECHAT |
| `idempotency_key` | TEXT | NOT NULL | 无显式默认 | 业务幂等键 |
| `paid_at` | TEXT | NOT NULL | 无显式默认 | 支付时间 |

外键：

- `checkout_group_id` → `checkout_groups.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_payments_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_payments_2`：唯一索引，字段 `checkout_group_id`。
- `sqlite_autoindex_payments_3`：唯一索引，字段 `idempotency_key`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE payments (
  id TEXT PRIMARY KEY,
  checkout_group_id TEXT NOT NULL UNIQUE REFERENCES checkout_groups(id),
  amount_cents INTEGER NOT NULL,
  method TEXT NOT NULL DEFAULT 'DEMO',
  idempotency_key TEXT NOT NULL UNIQUE,
  paid_at TEXT NOT NULL
);
```

</details>

### order_events — 订单操作事件

用途：保存创建、付款、发货、收货等已发生的状态变更。

主键：`id`。

维护说明：按事件展示历史，不能由当前状态生成不存在的历史步骤；actor_id 为逻辑关联，现有 DDL 未声明外键。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `order_id` | TEXT | NOT NULL | 无显式默认 | 关联订单编号 |
| `actor_id` | TEXT | NOT NULL | 无显式默认 | 操作者用户编号 |
| `action` | TEXT | NOT NULL | 无显式默认 | 事件或操作动作代码 |
| `from_status` | TEXT | 未声明 NOT NULL | 无显式默认 | 操作前状态 |
| `to_status` | TEXT | NOT NULL | 无显式默认 | 操作后状态 |
| `note` | TEXT | 未声明 NOT NULL | 无显式默认 | 操作说明 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `order_id` → `orders.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

无独立索引记录；INTEGER PRIMARY KEY 由 rowid 支持，不一定在索引清单中单列。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE order_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  actor_id TEXT NOT NULL,
  action TEXT NOT NULL,
  from_status TEXT,
  to_status TEXT NOT NULL,
  note TEXT,
  created_at TEXT NOT NULL
);
```

</details>

### stock_movements — 库存变动流水

用途：记录商品可用与预留库存的增减原因。

主键：`id`。

维护说明：增量允许为负；订单关联可空。流水与库存摘要需保持事务一致，不能单独修改其中一方。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `product_id` | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `order_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 关联订单编号 |
| `type` | TEXT | NOT NULL | 无显式默认 | 业务类型，取值见 DDL 或业务校验 |
| `available_delta` | INTEGER | NOT NULL | 无显式默认 | 可用库存增量，可正可负 |
| `reserved_delta` | INTEGER | NOT NULL | 无显式默认 | 预留库存增量，可正可负 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `order_id` → `orders.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `product_id` → `products.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

无独立索引记录；INTEGER PRIMARY KEY 由 rowid 支持，不一定在索引清单中单列。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id TEXT NOT NULL REFERENCES products(id),
  order_id TEXT REFERENCES orders(id),
  type TEXT NOT NULL,
  available_delta INTEGER NOT NULL,
  reserved_delta INTEGER NOT NULL,
  created_at TEXT NOT NULL
);
```

</details>

## 社区与评价

### ratings — 订单商品评价

用途：记录已购商品评价、星级及可见性。

主键：`id`。

维护说明：order_item_id UNIQUE 是候选键；额外保存用户与商品用于查询，属于跨表重复路径，不能仅凭有外键就断言此表违反 3NF。deleted_at 为软删除。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `order_item_id` | TEXT | NOT NULL | 无显式默认 | 关联订单商品明细编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `product_id` | TEXT | NOT NULL | 无显式默认 | 关联商品编号 |
| `stars` | INTEGER | NOT NULL | 无显式默认 | 评价星级，1 至 5 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `status` | TEXT | NOT NULL | 'PUBLIC' | 业务状态，取值及限制见下方 DDL |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `deleted_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 软删除或个人隐藏时间，NULL 表示未删除/隐藏 |

外键：

- `product_id` → `products.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `order_item_id` → `order_items.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_ratings_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_ratings_2`：唯一索引，字段 `order_item_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE ratings (
  id TEXT PRIMARY KEY,
  order_item_id TEXT NOT NULL UNIQUE REFERENCES order_items(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  product_id TEXT NOT NULL REFERENCES products(id),
  stars INTEGER NOT NULL CHECK(stars BETWEEN 1 AND 5),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN')),
  created_at TEXT NOT NULL
, deleted_at TEXT);
```

</details>

### community_posts — 社区帖子

用途：保存买家或商家的阅读分享、可见性和关联商品。

主键：`id`。

维护说明：images_json 是多图集合；author_role 是发表时身份事实，不由当前账户角色反推。report_count 是摘要计数，不能据此声称已有完整举报明细模块。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `author_user_id` | TEXT | NOT NULL | 无显式默认 | 帖子作者用户编号 |
| `author_role` | TEXT | NOT NULL | 无显式默认 | 发布当时角色 |
| `title` | TEXT | NOT NULL | 无显式默认 | 标题 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `cover_url` | TEXT | 未声明 NOT NULL | 无显式默认 | 封面资源地址 |
| `images_json` | TEXT | NOT NULL | '[]' | 图片 URL 有序数组的 JSON 文本 |
| `product_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 关联商品编号 |
| `status` | TEXT | NOT NULL | 'PUBLIC' | 业务状态，取值及限制见下方 DDL |
| `report_count` | INTEGER | NOT NULL | 0 | 帖子举报摘要计数 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `product_id` → `products.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `author_user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_post_status_created`：普通索引，字段 `status` + `created_at`。定义：`CREATE INDEX idx_post_status_created ON community_posts(status, created_at DESC)`
- `sqlite_autoindex_community_posts_1`：唯一索引，字段 `id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE community_posts (
  id TEXT PRIMARY KEY,
  author_user_id TEXT NOT NULL REFERENCES users(id),
  author_role TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  cover_url TEXT,
  images_json TEXT NOT NULL DEFAULT '[]',
  product_id TEXT REFERENCES products(id),
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN','DELETED')),
  report_count INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

### comments — 社区评论

用途：保存帖子下的用户评论。

主键：`id`。

维护说明：删除帖子会级联删除评论；状态字段现有 DDL 没有枚举 CHECK。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `post_id` | TEXT | NOT NULL | 无显式默认 | 关联社区帖子编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `status` | TEXT | NOT NULL | 'PUBLIC' | 业务状态，取值及限制见下方 DDL |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `post_id` → `community_posts.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_comments_1`：唯一索引，字段 `id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE comments (
  id TEXT PRIMARY KEY,
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC',
  created_at TEXT NOT NULL
);
```

</details>

### post_likes — 社区点赞关联

用途：记录用户对帖子的点赞关系。

主键：`post_id` + `user_id`。

维护说明：帖子与用户复合主键，点赞数量由关联记录统计。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `post_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 关联社区帖子编号 |
| `user_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。
- `post_id` → `community_posts.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_post_likes_user`：普通索引，字段 `user_id`。定义：`CREATE INDEX idx_post_likes_user ON post_likes(user_id)`
- `sqlite_autoindex_post_likes_1`：唯一索引，字段 `post_id` + `user_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE post_likes (
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY (post_id, user_id)
);
```

</details>

### community_uploads — 上传图片登记

用途：登记上传文件路径、所有者及可选帖子归属，也用于校验商家上传图片。

主键：`path`。

维护说明：主键为路径；文件内容在上传目录，数据库不保存二进制图片。帖子删除时解除关联，不等于文件必然可删除。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `path`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 上传文件相对 URL 路径 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 原上传用户，用于上传权限与引用校验 |
| `post_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 关联社区帖子编号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `post_id` → `community_posts.id`；ON DELETE SET NULL，ON UPDATE NO ACTION。
- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_community_uploads_user`：普通索引，字段 `user_id`。定义：`CREATE INDEX idx_community_uploads_user ON community_uploads(user_id)`
- `sqlite_autoindex_community_uploads_1`：唯一索引，字段 `path`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE community_uploads (
  path TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id TEXT REFERENCES community_posts(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL
);
```

</details>

## 通知与消息

### notifications — 系统通知

用途：保存定向或广播通知正文与受众。

主键：`id`。

维护说明：is_read/read_at 是旧定向通知兼容字段；当前用户状态以 notification_user_state 为主，不能据全局 is_read 标识广播已读。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `user_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 定向接收用户；NULL 表示广播，按 audience 计算受众 |
| `audience` | TEXT | NOT NULL | 无显式默认 | 通知受众标识 |
| `title` | TEXT | NOT NULL | 无显式默认 | 标题 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `status` | TEXT | NOT NULL | 'SENT' | 业务状态，取值及限制见下方 DDL |
| `is_read` | INTEGER | NOT NULL | 0 | 旧定向通知兼容已读标志 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `read_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 已读时间 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_notification_user_created`：普通索引，字段 `user_id` + `created_at`。定义：`CREATE INDEX idx_notification_user_created ON notifications(user_id, created_at DESC)`
- `sqlite_autoindex_notifications_1`：唯一索引，字段 `id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE notifications (
  id TEXT PRIMARY KEY,
  user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
  audience TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'SENT',
  is_read INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  read_at TEXT
);
```

</details>

### notification_reads — 旧通知已读关联

用途：历史迁移遗留的通知已读记录。

主键：`notification_id` + `user_id`。

维护说明：005 已复制到 notification_user_state；当前通知服务不再以它作为新的主状态表。保留兼容，未在本轮删除。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `notification_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 关联通知编号 |
| `user_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `read_at` | TEXT | NOT NULL | 无显式默认 | 已读时间 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。
- `notification_id` → `notifications.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_notification_reads_user`：普通索引，字段 `user_id`。定义：`CREATE INDEX idx_notification_reads_user ON notification_reads(user_id)`
- `sqlite_autoindex_notification_reads_1`：唯一索引，字段 `notification_id` + `user_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE notification_reads (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT NOT NULL,
  PRIMARY KEY (notification_id, user_id)
);
```

</details>

### notification_user_state — 个人通知状态

用途：每个用户对一条通知的已读和个人隐藏状态。

主键：`notification_id` + `user_id`。

维护说明：通知与用户复合主键；deleted_at 仅代表该用户隐藏，不是删除通知正文。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `notification_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 关联通知编号 |
| `user_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `read_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 已读时间 |
| `deleted_at` | TEXT | 未声明 NOT NULL | 无显式默认 | 软删除或个人隐藏时间，NULL 表示未删除/隐藏 |

外键：

- `user_id` → `users.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。
- `notification_id` → `notifications.id`；ON DELETE CASCADE，ON UPDATE NO ACTION。

索引与唯一性：

- `idx_notification_user_state_user`：普通索引，字段 `user_id` + `deleted_at`。定义：`CREATE INDEX idx_notification_user_state_user ON notification_user_state(user_id, deleted_at)`
- `sqlite_autoindex_notification_user_state_1`：唯一索引，字段 `notification_id` + `user_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE notification_user_state (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT,
  deleted_at TEXT,
  PRIMARY KEY (notification_id, user_id)
);
```

</details>

### conversations — 买家店铺会话

用途：为同一买家和商家建立固定私聊会话。

主键：`id`。

维护说明：买家与商家联合唯一；不向无关账号公开会话。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `buyer_id` | TEXT | NOT NULL | 无显式默认 | 买家用户编号 |
| `merchant_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联商家编号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `merchant_id` → `merchants.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `buyer_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `conversations_merchant`：普通索引，字段 `merchant_id` + `updated_at`。定义：`CREATE INDEX conversations_merchant ON conversations(merchant_id,updated_at)`
- `sqlite_autoindex_conversations_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_conversations_2`：唯一索引，字段 `buyer_id` + `merchant_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE conversations(id TEXT PRIMARY KEY,buyer_id TEXT NOT NULL REFERENCES users(id),merchant_id TEXT NOT NULL REFERENCES merchants(id),created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(buyer_id,merchant_id));
```

</details>

### chat_messages — 会话消息

用途：保存消息正文、发送身份及可选商品订单上下文。

主键：`id`。

维护说明：发送人、发送角色、客户端消息编号联合唯一，用于重试去重；角色是发送当时身份。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `conversation_id` | TEXT | NOT NULL | 无显式默认 | 关联会话编号 |
| `sender_id` | TEXT | NOT NULL | 无显式默认 | 发送人用户编号 |
| `sender_role` | TEXT | NOT NULL | 无显式默认 | 发送当时角色 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `product_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 关联商品编号 |
| `order_id` | TEXT | 未声明 NOT NULL | 无显式默认 | 关联订单编号 |
| `client_id` | TEXT | NOT NULL | 无显式默认 | 客户端去重编号；作用域见唯一约束 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `order_id` → `orders.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `product_id` → `products.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `sender_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `conversation_id` → `conversations.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `chat_messages_page`：普通索引，字段 `conversation_id` + `id`。定义：`CREATE INDEX chat_messages_page ON chat_messages(conversation_id,id)`
- `sqlite_autoindex_chat_messages_1`：唯一索引，字段 `sender_id` + `sender_role` + `client_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE chat_messages(id INTEGER PRIMARY KEY AUTOINCREMENT,conversation_id TEXT NOT NULL REFERENCES conversations(id),sender_id TEXT NOT NULL REFERENCES users(id),sender_role TEXT NOT NULL CHECK(sender_role IN ('USER','MERCHANT')),content TEXT NOT NULL,product_id TEXT REFERENCES products(id),order_id TEXT REFERENCES orders(id),client_id TEXT NOT NULL,created_at TEXT NOT NULL,UNIQUE(sender_id,sender_role,client_id));
```

</details>

### conversation_reads — 会话已读游标

用途：保存会话中买家或商家一侧最后已读的消息编号。

主键：`conversation_id` + `reader_role`。

维护说明：以会话与角色作复合主键；last_message_id 默认 0，未声明消息外键，业务需保证游标属于该会话。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `conversation_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 关联会话编号 |
| `reader_role`（主键2） | TEXT | NOT NULL | 无显式默认 | 会话阅读侧：USER / MERCHANT |
| `last_message_id` | INTEGER | NOT NULL | 0 | 此侧最后已读消息编号；0 为未读起点 |

外键：

- `conversation_id` → `conversations.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_conversation_reads_1`：唯一索引，字段 `conversation_id` + `reader_role`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE conversation_reads(conversation_id TEXT NOT NULL REFERENCES conversations(id),reader_role TEXT NOT NULL CHECK(reader_role IN ('USER','MERCHANT')),last_message_id INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(conversation_id,reader_role));
```

</details>

## 售后与反馈

### after_sales — 售后申请

用途：保存售后类型、申请金额、原因、审核退货状态。

主键：`id`。

维护说明：order_id 可确定买家和商家，额外 buyer_id/merchant_id 为重复归属。每单仅允许一笔未结束售后，已完成部分退款后可再次申请。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `order_id` | TEXT | NOT NULL | 无显式默认 | 关联订单编号 |
| `buyer_id` | TEXT | NOT NULL | 无显式默认 | 买家用户编号 |
| `merchant_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联商家编号 |
| `type` | TEXT | NOT NULL | 无显式默认 | REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 |
| `amount_cents` | INTEGER | NOT NULL | 无显式默认 | 本次申请总退款额，应等于售后明细金额合计 |
| `reason` | TEXT | NOT NULL | 无显式默认 | 售后申请原因 |
| `description` | TEXT | NOT NULL | '' | 说明正文 |
| `images_json` | TEXT | NOT NULL | '[]' | 图片 URL 有序数组的 JSON 文本 |
| `status` | TEXT | NOT NULL | 无显式默认 | 业务状态，取值及限制见下方 DDL |
| `merchant_note` | TEXT | NOT NULL | '' | 商家初审处理说明 |
| `return_address` | TEXT | NOT NULL | '' | 本次售后使用的退回地址文本 |
| `carrier` | TEXT | NOT NULL | '' | 买家退货承运商 |
| `tracking_no` | TEXT | NOT NULL | '' | 买家退货物流单号 |
| `client_id` | TEXT | NOT NULL | 无显式默认 | 客户端去重编号；作用域见唯一约束 |
| `version` | INTEGER | NOT NULL | 1 | 乐观并发或同步版本号 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `merchant_id` → `merchants.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `buyer_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `order_id` → `orders.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `after_sales_buyer`：普通索引，字段 `buyer_id` + `created_at`。定义：`CREATE INDEX after_sales_buyer ON after_sales(buyer_id,created_at)`
- `after_sales_merchant`：普通索引，字段 `merchant_id` + `status` + `created_at`。定义：`CREATE INDEX after_sales_merchant ON after_sales(merchant_id,status,created_at)`
- `one_active_after_sale`：唯一索引，字段 `order_id`；部分索引，条件见下方 SQL。定义：`CREATE UNIQUE INDEX one_active_after_sale ON after_sales(order_id)  WHERE status NOT IN ('REJECTED','CANCELLED','COMPLETED')`
- `sqlite_autoindex_after_sales_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_after_sales_2`：唯一索引，字段 `buyer_id` + `client_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE after_sales (
 id TEXT PRIMARY KEY, order_id TEXT NOT NULL REFERENCES orders(id), buyer_id TEXT NOT NULL REFERENCES users(id), merchant_id TEXT NOT NULL REFERENCES merchants(id),
 type TEXT NOT NULL CHECK(type IN ('REFUND_ONLY','RETURN_REFUND')), amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
 reason TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', images_json TEXT NOT NULL DEFAULT '[]',
 status TEXT NOT NULL CHECK(status IN ('MERCHANT_REVIEW','ADMIN_REVIEW','WAITING_RETURN','RETURNING','REFUND_PENDING','COMPLETED','REJECTED','CANCELLED')),
 merchant_note TEXT NOT NULL DEFAULT '', return_address TEXT NOT NULL DEFAULT '', carrier TEXT NOT NULL DEFAULT '', tracking_no TEXT NOT NULL DEFAULT '',
 client_id TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, UNIQUE(buyer_id,client_id)
);
```

</details>

### after_sale_items — 售后商品明细

用途：保存每次申请选中的订单商品、数量、金额及回库/取消数量。

主键：`after_sale_id` + `order_item_id`。

维护说明：复合主键防止同一申请重复商品；金额以分计。restock_quantity 表示按业务规则确定的恢复数量，不等于任何部分补偿都会取消商品。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `after_sale_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 关联售后申请编号 |
| `order_item_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 关联订单商品明细编号 |
| `quantity` | INTEGER | NOT NULL | 无显式默认 | 商品数量 |
| `amount_cents` | INTEGER | NOT NULL | 无显式默认 | 本记录金额，整数分 |
| `restock_quantity` | INTEGER | NOT NULL | 0 | 本次按规则确定的恢复库存或取消数量 |

外键：

- `order_item_id` → `order_items.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `after_sale_id` → `after_sales.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `after_sale_items_order_item`：普通索引，字段 `order_item_id`。定义：`CREATE INDEX after_sale_items_order_item ON after_sale_items(order_item_id)`
- `sqlite_autoindex_after_sale_items_1`：唯一索引，字段 `after_sale_id` + `order_item_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE after_sale_items (
 after_sale_id TEXT NOT NULL REFERENCES after_sales(id),
 order_item_id TEXT NOT NULL REFERENCES order_items(id),
 quantity INTEGER NOT NULL CHECK(quantity>0),
 amount_cents INTEGER NOT NULL CHECK(amount_cents>0),
 restock_quantity INTEGER NOT NULL DEFAULT 0 CHECK(restock_quantity>=0),
 PRIMARY KEY(after_sale_id,order_item_id)
);
```

</details>

### after_sale_events — 售后处理事件

用途：保存商家初审、平台复核、寄回、收货及退款等处理历史。

主键：`id`。

维护说明：关联售后和操作者；平台判断与操作说明应保留，不用最终状态替代事件。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `after_sale_id` | TEXT | NOT NULL | 无显式默认 | 关联售后申请编号 |
| `actor_id` | TEXT | NOT NULL | 无显式默认 | 操作者用户编号 |
| `action` | TEXT | NOT NULL | 无显式默认 | 事件或操作动作代码 |
| `note` | TEXT | NOT NULL | '' | 操作说明 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `actor_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `after_sale_id` → `after_sales.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

无独立索引记录；INTEGER PRIMARY KEY 由 rowid 支持，不一定在索引清单中单列。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE after_sale_events(id INTEGER PRIMARY KEY AUTOINCREMENT,after_sale_id TEXT NOT NULL REFERENCES after_sales(id),actor_id TEXT NOT NULL REFERENCES users(id),action TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL);
```

</details>

### refunds — 演示退款流水

用途：保存每笔完成的演示退款，用于累计订单退款金额。

主键：`id`。

维护说明：011 后 order_id 不再唯一，支持同订单多次退款；after_sale_id 仍唯一防止同笔售后重复退款。该唯一字段是候选键，order_id 的跨表重复不单独构成表内 3NF 反例。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `after_sale_id` | TEXT | NOT NULL | 无显式默认 | 关联售后申请编号 |
| `order_id` | TEXT | NOT NULL | 无显式默认 | 关联订单编号 |
| `amount_cents` | INTEGER | NOT NULL | 无显式默认 | 本记录金额，整数分 |
| `method` | TEXT | NOT NULL | 无显式默认 | 固定 DEMO，只登记演示退款 |
| `status` | TEXT | NOT NULL | 无显式默认 | 固定 COMPLETED；已完成的演示流水 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `order_id` → `orders.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `after_sale_id` → `after_sales.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `refunds_order`：普通索引，字段 `order_id`。定义：`CREATE INDEX refunds_order ON refunds(order_id)`
- `sqlite_autoindex_refunds_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_refunds_2`：唯一索引，字段 `after_sale_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE "refunds" (
 id TEXT PRIMARY KEY, after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),
 order_id TEXT NOT NULL REFERENCES orders(id), amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
 method TEXT NOT NULL CHECK(method='DEMO'),status TEXT NOT NULL CHECK(status='COMPLETED'),created_at TEXT NOT NULL
);
```

</details>

### feedbacks — 意见反馈

用途：保存用户或商家提交的问题和处理状态。

主键：`id`。

维护说明：用户与客户端编号联合唯一防止重复提交；提交身份是历史事实。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `submitter_role` | TEXT | NOT NULL | 无显式默认 | 提交反馈当时角色 |
| `title` | TEXT | NOT NULL | 无显式默认 | 标题 |
| `type` | TEXT | NOT NULL | 无显式默认 | 反馈分类文本，由服务端输入校验 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `status` | TEXT | NOT NULL | 无显式默认 | 业务状态，取值及限制见下方 DDL |
| `client_id` | TEXT | NOT NULL | 无显式默认 | 客户端去重编号；作用域见唯一约束 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `feedbacks_owner`：普通索引，字段 `user_id` + `created_at`。定义：`CREATE INDEX feedbacks_owner ON feedbacks(user_id,created_at)`
- `sqlite_autoindex_feedbacks_1`：唯一索引，字段 `id`。
- `sqlite_autoindex_feedbacks_2`：唯一索引，字段 `user_id` + `client_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE feedbacks(id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id),submitter_role TEXT NOT NULL,title TEXT NOT NULL,type TEXT NOT NULL,content TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('OPEN','PROCESSING','RESOLVED')),client_id TEXT NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(user_id,client_id));
```

</details>

### feedback_replies — 反馈沟通记录

用途：保存平台回复和提交人的后续补充。

主键：`id`。

维护说明：按发送人和客户端编号去重，保留各次回复，不覆盖原反馈正文。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `feedback_id` | TEXT | NOT NULL | 无显式默认 | 关联反馈编号 |
| `sender_id` | TEXT | NOT NULL | 无显式默认 | 发送人用户编号 |
| `sender_role` | TEXT | NOT NULL | 无显式默认 | 发送当时角色 |
| `content` | TEXT | NOT NULL | 无显式默认 | 正文 |
| `client_id` | TEXT | NOT NULL | 无显式默认 | 客户端去重编号；作用域见唯一约束 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `sender_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。
- `feedback_id` → `feedbacks.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_feedback_replies_1`：唯一索引，字段 `sender_id` + `client_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE feedback_replies(id INTEGER PRIMARY KEY AUTOINCREMENT,feedback_id TEXT NOT NULL REFERENCES feedbacks(id),sender_id TEXT NOT NULL REFERENCES users(id),sender_role TEXT NOT NULL,content TEXT NOT NULL,client_id TEXT NOT NULL,created_at TEXT NOT NULL,UNIQUE(sender_id,client_id));
```

</details>

## 系统与同步

### audit_logs — 业务审计日志

用途：保存后台关键管理动作、对象及结果。

主键：`id`。

维护说明：operator_name 是操作当时名称快照；对象类型不同，object_id 不指向固定一张表。日志不应随账户昵称变化重写。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | INTEGER | INTEGER 主键，空值插入时分配编号 | 无显式默认 | 本表记录编号 |
| `request_id` | TEXT | NOT NULL | 无显式默认 | 审计请求编号 |
| `operator_id` | TEXT | NOT NULL | 无显式默认 | 操作人编号 |
| `operator_name` | TEXT | NOT NULL | 无显式默认 | 操作当时名称快照 |
| `module` | TEXT | NOT NULL | 无显式默认 | 操作所属模块 |
| `action` | TEXT | NOT NULL | 无显式默认 | 事件或操作动作代码 |
| `object_id` | TEXT | NOT NULL | 无显式默认 | 被操作对象编号，按模块解释 |
| `result` | TEXT | NOT NULL | 无显式默认 | 本次操作结果，如成功 |
| `summary` | TEXT | 未声明 NOT NULL | 无显式默认 | 内容摘要 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

本表没有已声明外键；逻辑关联以用途与维护说明为准。

索引与唯一性：

无独立索引记录；INTEGER PRIMARY KEY 由 rowid 支持，不一定在索引清单中单列。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id TEXT NOT NULL,
  operator_id TEXT NOT NULL,
  operator_name TEXT NOT NULL,
  module TEXT NOT NULL,
  action TEXT NOT NULL,
  object_id TEXT NOT NULL,
  result TEXT NOT NULL,
  summary TEXT,
  created_at TEXT NOT NULL
);
```

</details>

### settings — 服务设置

用途：按设置键保存服务配置值。

主键：`key`。

维护说明：value 是通用配置序列化值；本表不代表可以存放并公开导出密钥。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `key`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 设置或元数据键 |
| `value` | TEXT | NOT NULL | 无显式默认 | 设置或元数据值 |
| `updated_at` | TEXT | NOT NULL | 无显式默认 | 最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

本表没有已声明外键；逻辑关联以用途与维护说明为准。

索引与唯一性：

- `sqlite_autoindex_settings_1`：唯一索引，字段 `key`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

### schema_migrations — 数据库迁移记录

用途：登记已应用的迁移文件版本，防止重复执行。

主键：`version`。

维护说明：版本是迁移文件名，不是客户端或商品版本。禁止改写已应用版本来强行重跑迁移。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `version`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 已应用迁移的版本文件名，不是整数并发版本 |
| `applied_at` | TEXT | NOT NULL | 无显式默认 | 迁移应用时间 |

外键：

本表没有已声明外键；逻辑关联以用途与维护说明为准。

索引与唯一性：

- `sqlite_autoindex_schema_migrations_1`：唯一索引，字段 `version`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE schema_migrations(version TEXT PRIMARY KEY, applied_at TEXT NOT NULL);
```

</details>

### sync_receipts — 服务端同步回执

用途：保存离线操作的幂等回执与请求摘要。

主键：`user_id` + `operation_id`。

维护说明：用户与操作编号复合主键；result_json 是协议回执。已确认操作不可重复执行业务副作用。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `operation_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 同步操作唯一编号 |
| `client_id` | TEXT | NOT NULL | 无显式默认 | 执行同步操作的客户端标识 |
| `payload_hash` | TEXT | NOT NULL | 无显式默认 | 操作请求载荷摘要 |
| `result_json` | TEXT | NOT NULL | 无显式默认 | 幂等操作回执 JSON 文本 |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_sync_receipts_1`：唯一索引，字段 `user_id` + `operation_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE sync_receipts (
 user_id TEXT NOT NULL REFERENCES users(id), operation_id TEXT NOT NULL,
 client_id TEXT NOT NULL, payload_hash TEXT NOT NULL, result_json TEXT NOT NULL,
 created_at TEXT NOT NULL, PRIMARY KEY(user_id,operation_id)
);
```

</details>

### sync_entity_map — 本地远程实体映射

用途：映射账号资源中的本地编号与服务端编号。

主键：`user_id` + `resource` + `local_id`。

维护说明：用户、资源、本地编号共同确定远程编号；用于离线创建后的依赖重写。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `resource`（主键2） | TEXT | NOT NULL | 无显式默认 | 同步资源类型代码 |
| `local_id`（主键3） | TEXT | NOT NULL | 无显式默认 | 设备本地实体编号 |
| `remote_id` | TEXT | NOT NULL | 无显式默认 | 服务端实体编号 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_sync_entity_map_1`：唯一索引，字段 `user_id` + `resource` + `local_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE sync_entity_map (
 user_id TEXT NOT NULL REFERENCES users(id),resource TEXT NOT NULL,local_id TEXT NOT NULL,
 remote_id TEXT NOT NULL,PRIMARY KEY(user_id,resource,local_id)
);
```

</details>

### sync_snapshots — 分页同步快照

用途：固定一次同步分页期间的数据集合，避免分页读取不一致。

主键：`id`。

维护说明：data_json 是有有效期的协议缓存，不是第二套业务主记录；过期后可重取，不据此宣称全库为严格关系模型。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id`（主键1） | TEXT | 未声明 NOT NULL | 无显式默认 | 本表记录编号 |
| `user_id` | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `resource` | TEXT | NOT NULL | 无显式默认 | 同步资源类型代码 |
| `data_json` | TEXT | NOT NULL | 无显式默认 | 同步分页集合快照 JSON 文本 |
| `expires_at` | TEXT | NOT NULL | 无显式默认 | 过期时间 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_sync_snapshots_1`：唯一索引，字段 `id`。
- `sync_snapshots_expiry`：普通索引，字段 `expires_at`。定义：`CREATE INDEX sync_snapshots_expiry ON sync_snapshots(expires_at)`

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE sync_snapshots (
 id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id),resource TEXT NOT NULL,
 data_json TEXT NOT NULL,expires_at TEXT NOT NULL
);
```

</details>

### sync_media — 同步媒体处理记录

用途：保存离线图片上传操作的摘要、地址与阶段。

主键：`user_id` + `operation_id`。

维护说明：用户与操作编号复合主键；PREPARED/READY 是媒体协议状态，重试需要校验摘要。

| 字段 | 声明类型 | 非空声明 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `user_id`（主键1） | TEXT | NOT NULL | 无显式默认 | 所属或关联用户编号 |
| `operation_id`（主键2） | TEXT | NOT NULL | 无显式默认 | 同步操作唯一编号 |
| `sha256` | TEXT | NOT NULL | 无显式默认 | 媒体内容 SHA-256 摘要 |
| `url` | TEXT | NOT NULL | 无显式默认 | 媒体资源地址 |
| `purpose` | TEXT | NOT NULL | 无显式默认 | 媒体业务用途代码 |
| `state` | TEXT | NOT NULL | 无显式默认 | 协议处理阶段，取值见 DDL |
| `created_at` | TEXT | NOT NULL | 无显式默认 | 创建时间；服务端业务通常保存 UTC ISO 8601 字符串 |

外键：

- `user_id` → `users.id`；ON DELETE NO ACTION，ON UPDATE NO ACTION。

索引与唯一性：

- `sqlite_autoindex_sync_media_1`：唯一索引，字段 `user_id` + `operation_id`。

<details>
<summary>当前建表语句（包含 CHECK 等精确约束）</summary>

```sql
CREATE TABLE sync_media (
 user_id TEXT NOT NULL REFERENCES users(id),operation_id TEXT NOT NULL,
 sha256 TEXT NOT NULL,url TEXT NOT NULL,purpose TEXT NOT NULL,
 state TEXT NOT NULL CHECK(state IN ('PREPARED','READY')),created_at TEXT NOT NULL,
 PRIMARY KEY(user_id,operation_id)
);
```

</details>

## Android 私有数据库附录

文件名为 `huimeng-local.db`，由 `LocalDatabase` 创建，当前 schema version=1，保存在对应安装包私有数据库目录；普通包与 QA 包各自独立。下表依据当前源码，未连接用户设备或导出设备内容。SharedPreferences 中的会话、外观等不属于 SQLite 表。

这 7 张表是离线投影、队列及协议缓存，JSON 是协议设计的一部分；不将它们宣称为全部业务实体的严格 3NF 实现，也不在本次设计中重构。

### Android.local_spaces — 本地账号空间

用途：按来源服务和账号隔离本地内容，记录当前空间及当前角色。

字段说明：id=空间编号；origin=来源服务；user_id=绑定服务端账号；role=当前角色；active=是否当前空间；created_at=创建毫秒时间。非空账号按 origin+user_id 唯一，active=1 最多一个。

```sql
CREATE TABLE local_spaces(id TEXT PRIMARY KEY,origin TEXT NOT NULL DEFAULT '',user_id TEXT NOT NULL DEFAULT '',role TEXT NOT NULL DEFAULT 'USER',active INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL);
```

### Android.local_entities — 本地实体投影

用途：保存离线业务显示值与最近确认值。

字段说明：space_id/resource/id 为复合主键；remote_id=服务端编号；data=当前投影 JSON；confirmed=上次确认 JSON；version=服务端版本；revision=本地修订计数；deleted/sample=删除与示例标志。

```sql
CREATE TABLE local_entities(space_id TEXT NOT NULL REFERENCES local_spaces(id),resource TEXT NOT NULL,id TEXT NOT NULL,remote_id TEXT,data TEXT NOT NULL,confirmed TEXT,version INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0,sample INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(space_id,resource,id));
```

### Android.sync_operations — 本地待同步操作

用途：与本地业务投影在同一事务写入的操作队列。

字段说明：operation_id=唯一操作编号；space_id=账号空间；resource/entity_id/action=目标与动作；payload=请求 JSON；payload_hash=请求摘要；base_version=基准版本；dependencies=依赖编号数组；state=队列阶段；attempts/next_attempt_at/error=重试信息；created_at=创建毫秒时间。

```sql
CREATE TABLE sync_operations(operation_id TEXT PRIMARY KEY,space_id TEXT NOT NULL REFERENCES local_spaces(id),resource TEXT NOT NULL,entity_id TEXT NOT NULL,action TEXT NOT NULL,payload TEXT NOT NULL,payload_hash TEXT NOT NULL DEFAULT '',base_version INTEGER NOT NULL DEFAULT 0,dependencies TEXT NOT NULL DEFAULT '[]',state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','IN_FLIGHT','WAITING_AUTH','BLOCKED','CONFLICT','ACKED')),attempts INTEGER NOT NULL DEFAULT 0,next_attempt_at INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL);
```

### Android.local_media — 本地媒体队列

用途：记录图片文件、摘要、远程地址与上传阶段。

字段说明：space_id/id 为复合主键；path=本地文件路径；sha256=内容摘要；url=上传后 URL；purpose=业务用途；state=处理状态。

```sql
CREATE TABLE local_media(space_id TEXT NOT NULL REFERENCES local_spaces(id),id TEXT NOT NULL,path TEXT NOT NULL,sha256 TEXT NOT NULL,url TEXT NOT NULL DEFAULT '',purpose TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'PENDING',PRIMARY KEY(space_id,id));
```

### Android.sync_results — 本地同步回执

用途：登记已确认的操作结果，支持恢复及幂等确认。

字段说明：operation_id 为主键并关联 sync_operations；result=回执 JSON；confirmed_at=确认毫秒时间。

```sql
CREATE TABLE sync_results(operation_id TEXT PRIMARY KEY REFERENCES sync_operations(operation_id),result TEXT NOT NULL,confirmed_at INTEGER NOT NULL);
```

### Android.local_metadata — 本地技术元数据

用途：键值形式保存导入和同步辅助状态。

字段说明：key 为主键；value 为序列化值。不应将其作为第二套服务端业务实体。

```sql
CREATE TABLE local_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);
```

### Android.snapshot_pages — 本地分页暂存

用途：暂存同一快照的各页数据，完成后组装导入。

字段说明：space_id/resource/snapshot_id/offset 为复合主键；data=该页 JSON；当前 DDL 对 space_id 没有声明外键。

```sql
CREATE TABLE snapshot_pages(space_id TEXT NOT NULL,resource TEXT NOT NULL,snapshot_id TEXT NOT NULL,offset INTEGER NOT NULL,data TEXT NOT NULL,PRIMARY KEY(space_id,resource,snapshot_id,offset));
```

## 文档维护与覆盖边界

- 本次共说明服务端 40 张表、306 个字段、62 条已声明外键字段关联，以及 Android 源码中 7 张本地技术表。迁移 001—011 已纳入核对。
- 当前库与迁移是现状，设计文档中的目标表尚未建立。每次增加字段、状态或外键后，更新表总览、对应字段表及设计进度。
- 现有控制中心业务导出使用 `html/launcher/data-manager.mjs` 中的 20 表清单，不等于完整数据库备份；目前未包含 `after_sale_items`。需要完整恢复或部分售后明细时使用完整备份，并把完善导出列为后续实施验收项。
- 数据字典只描述结构与规则；不包含真实用户数据、口令、令牌、媒体文件或业务库副本。
