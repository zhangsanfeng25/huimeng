# 绘梦数据库数据字典（当前规范化结构）

核对日期：2026-10-24。正式业务库已完成受控规范化切换，实际检查为 47 张表、完整性及外键通过、新旧业务摘要相同。以下字段来自与正式库一致的 target-schema.sql；迁移前 40 表见 [旧结构字典](database-dictionary-legacy-011.md)，过程及覆盖范围见 [验收记录](verification/2026-10-24-database-normalization.md)。

## 规范化口径

正式业务表按非空候选键和已确认的函数依赖检查 3NF：每个非平凡 X → A 的决定因素 X 是超键，或 A 为主属性。以下逐表列候选键及 K → 其余属性；这基于已确认业务语义，不宣称自动证明所有未来函数依赖。可空 UNIQUE、部分唯一索引不当作全表候选键。外键说明参照完整 DDL，不等同业务归属校验。

协议回执、分页快照、设置及会话等技术存储明确单列；其 JSON 是技术载荷，不冒充规范化业务明细。notification_reads 为只读历史归档。Android 7 张本地技术表本次不改，见当前字典附录。整数金额单位为分；历史成交与审计快照不随现值修改。

## 47 张表总览

|表名|中文名称|作用类型|用途|
|---|---|---|---|
|`addresses`|用户收货地址|购物与交易|保存用户可维护的当前地址。|
|`after_sale_events`|售后处理事件|售后与反馈|保存商家初审、平台复核、寄回、收货及退款等处理历史。|
|`after_sale_images`|售后凭证图片|售后与反馈|按售后单及位置保存凭证图片。|
|`after_sale_items`|售后商品明细|售后与反馈|保存每次申请选中的订单商品、数量、金额及回库/取消数量。|
|`after_sale_requests`|售后请求去重|售后与反馈|保留买家提交请求的幂等编号与售后关联。|
|`after_sales`|售后申请|售后与反馈|保存售后类型、申请金额、原因、审核退货状态。|
|`audit_logs`|业务审计日志|系统与同步|保存后台关键管理动作、对象及结果。|
|`cart_items`|购物车明细|购物与交易|记录用户、商品、数量和是否参与结算。|
|`categories`|商品分类|商家与商品|保存分类名称、分类类型标签、排序及启用状态。|
|`chat_messages`|会话消息|通知与消息|保存消息正文、发送身份及可选商品订单上下文。|
|`checkout_groups`|跨店铺结算组|购物与交易|一次结算对应一个买家，可按店铺拆出多张订单。|
|`comments`|社区评论|社区与评价|保存帖子下的用户评论。|
|`community_post_images`|社区帖子图片|社区与评价|按帖子及位置保存图片。|
|`community_posts`|社区帖子|社区与评价|保存买家或商家的阅读分享、可见性和关联商品。|
|`community_uploads`|上传图片登记|社区与评价|登记上传文件路径、所有者及可选帖子归属，也用于校验商家上传图片。|
|`conversation_reads`|会话已读游标|通知与消息|保存会话中买家或商家一侧最后已读的消息编号。|
|`conversations`|买家店铺会话|通知与消息|为同一买家和商家建立固定私聊会话。|
|`favorites`|商品收藏关联|购物与交易|记录用户收藏的商品。|
|`feedback_replies`|反馈沟通记录|售后与反馈|保存平台回复和提交人的后续补充。|
|`feedbacks`|意见反馈|售后与反馈|保存用户或商家提交的问题和处理状态。|
|`merchants`|商家与店铺|商家与商品|保存店铺、负责人、入驻联系资料和审批经营状态。|
|`notification_read_receipts`|通知已读事实|通知与消息|记录用户确已读；历史时间未知时 read_at 保持 NULL。|
|`notification_reads`|旧通知已读关联|技术/归档：通知与消息|历史原样保留的已读记录归档；活动读写使用 notification_read_receipts。|
|`notification_user_state`|个人通知状态|通知与消息|仅保存用户对通知的个人隐藏状态。|
|`notifications`|系统通知|通知与消息|保存定向或广播通知正文与受众。|
|`order_address_snapshots`|订单地址快照|购物与交易|保存成交当时地址各字段及原键是否存在，独立于当前地址。|
|`order_events`|订单操作事件|购物与交易|保存创建、付款、发货、收货等已发生的状态变更。|
|`order_items`|成交商品明细|购物与交易|保存成交时商品名称、封面、单价和购买数量。|
|`orders`|店铺子订单|购物与交易|保存店铺子订单、结算组、确认金额与唯一履约状态；买家与有效期从结算组读取。|
|`payments`|结算支付记录|购物与交易|记录整个结算组的演示付款。|
|`post_likes`|社区点赞关联|社区与评价|记录用户对帖子的点赞关系。|
|`product_revision_contents`|商品版本目录|商家与商品|按版本及位置保存图书目录，允许重复标题。|
|`product_revision_images`|商品版本图片|商家与商品|按版本及位置保存详情图片，允许重复 URL。|
|`product_revisions`|商品内容修订版|商家与商品|保存版本内容、图书资料、价格及审核事实；图片和目录改由子表保存。|
|`products`|商品主档|商家与商品|保存所属商家、分类、发布版本、销售状态及库存。|
|`ratings`|订单商品评价|社区与评价|记录已购商品评价、星级及可见性。|
|`refunds`|演示退款流水|售后与反馈|保存每笔完成的演示退款，用于累计订单退款金额。|
|`schema_migrations`|数据库迁移记录|技术/归档：系统与同步|登记已应用的迁移文件版本，防止重复执行。|
|`sessions`|登录会话|技术/归档：身份与权限|保存令牌摘要、有效期和当前使用的角色。|
|`settings`|服务设置|技术/归档：系统与同步|按设置键保存服务配置值。|
|`stock_movements`|库存变动流水|购物与交易|记录商品可用与预留库存的增减原因。|
|`sync_entity_map`|本地远程实体映射|技术/归档：系统与同步|映射账号资源中的本地编号与服务端编号。|
|`sync_media`|同步媒体处理记录|技术/归档：系统与同步|保存离线图片上传操作的摘要、地址与阶段。|
|`sync_receipts`|服务端同步回执|技术/归档：系统与同步|保存离线操作的幂等回执与请求摘要。|
|`sync_snapshots`|分页同步快照|技术/归档：系统与同步|固定一次同步分页期间的数据集合，避免分页读取不一致。|
|`user_roles`|账户角色关联|身份与权限|一个账户可拥有多个角色，角色切换不复制账户。|
|`users`|用户账户|身份与权限|保存登录标识、公开资料和密码摘要；买家、商家、管理员共用账户实体。|

## addresses — 用户收货地址

用途：保存用户可维护的当前地址。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`recipient`|TEXT|NOT NULL|无|收货人|
|`phone`|TEXT|NOT NULL|无|联系电话|
|`region`|TEXT|NOT NULL|无|收货地区文本|
|`detail`|TEXT|NOT NULL|无|地址详细部分|
|`is_default`|INTEGER|NOT NULL|0|默认地址标志，0/1|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|

外键：user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_addresses_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE addresses (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient TEXT NOT NULL,
  phone TEXT NOT NULL,
  region TEXT NOT NULL,
  detail TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);
```

</details>

## after_sale_events — 售后处理事件

用途：保存商家初审、平台复核、寄回、收货及退款等处理历史。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`after_sale_id`|TEXT|NOT NULL|无|关联售后申请编号|
|`actor_id`|TEXT|NOT NULL|无|操作者用户编号|
|`action`|TEXT|NOT NULL|无|事件或操作动作代码|
|`note`|TEXT|NOT NULL|''|操作说明|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：actor_id → users.id（删除 NO ACTION）；after_sale_id → after_sales.id（删除 NO ACTION）。

唯一索引：无独立唯一索引，主键以 DDL 为准。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE after_sale_events(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  after_sale_id TEXT NOT NULL REFERENCES after_sales(id),
  actor_id TEXT NOT NULL REFERENCES users(id),
  action TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL
);
```

</details>

## after_sale_images — 售后凭证图片

用途：按售后单及位置保存凭证图片。

非空候选键：(after_sale_id, position)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`after_sale_id`|TEXT|PK 1；NOT NULL|无|所属售后单编号|
|`position`|INTEGER|PK 2；NOT NULL|无|从 0 开始的父记录内顺序，不按内容去重|
|`url`|TEXT|NOT NULL|无|资源地址|

外键：after_sale_id → after_sales.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_after_sale_images_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE after_sale_images(after_sale_id TEXT NOT NULL REFERENCES after_sales(id),position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(after_sale_id,position));
```

</details>

## after_sale_items — 售后商品明细

用途：保存每次申请选中的订单商品、数量、金额及回库/取消数量。

非空候选键：(after_sale_id, order_item_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 复合键决定本次申请数量、金额与回库数量；不同售后可分次补偿，同一数量并不决定金额。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`after_sale_id`|TEXT|PK 1；NOT NULL|无|关联售后申请编号|
|`order_item_id`|TEXT|PK 2；NOT NULL|无|关联订单商品明细编号|
|`quantity`|INTEGER|NOT NULL|无|商品数量|
|`amount_cents`|INTEGER|NOT NULL|无|本记录金额，整数分|
|`restock_quantity`|INTEGER|NOT NULL|0|本次按规则确定的恢复库存或取消数量|

外键：order_item_id → order_items.id（删除 NO ACTION）；after_sale_id → after_sales.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_after_sale_items_1。

<details><summary>当前建表语句</summary>

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

## after_sale_requests — 售后请求去重

用途：保留买家提交请求的幂等编号与售后关联。

非空候选键：(buyer_id, client_id)；(after_sale_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 after_sale_id 也是候选键；买家 + client_id 是请求身份。与订单买家一致需事务校验，单列外键不能证明这一点。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`buyer_id`|TEXT|PK 1；NOT NULL|无|提交幂等请求的买家编号，事务校验与订单买家一致|
|`client_id`|TEXT|PK 2；NOT NULL|无|该买家提交的客户端幂等编号|
|`after_sale_id`|TEXT|NOT NULL|无|所属售后单编号|

外键：after_sale_id → after_sales.id（删除 NO ACTION）；buyer_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_after_sale_requests_2；sqlite_autoindex_after_sale_requests_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE after_sale_requests(buyer_id TEXT NOT NULL REFERENCES users(id),client_id TEXT NOT NULL,after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),PRIMARY KEY(buyer_id,client_id));
```

</details>

## after_sales — 售后申请

用途：保存售后类型、申请金额、原因、审核退货状态。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 order_id 不是候选键，同订单可有多次结束售后；买家、商家经订单关联获取，不独立存储。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`order_id`|TEXT|NOT NULL|无|关联订单编号|
|`type`|TEXT|NOT NULL|无|REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款|
|`amount_cents`|INTEGER|NOT NULL|无|本次申请总退款额，应等于售后明细金额合计|
|`reason`|TEXT|NOT NULL|无|售后申请原因|
|`description`|TEXT|NOT NULL|''|说明正文|
|`status`|TEXT|NOT NULL|无|业务状态，取值及限制见下方 DDL|
|`merchant_note`|TEXT|NOT NULL|''|商家初审处理说明|
|`return_address`|TEXT|NOT NULL|''|本次售后使用的退回地址文本|
|`carrier`|TEXT|NOT NULL|''|买家退货承运商|
|`tracking_no`|TEXT|NOT NULL|''|买家退货物流单号|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：order_id → orders.id（删除 NO ACTION）。

唯一索引：one_active_after_sale（部分索引，条件见结构源文件）；sqlite_autoindex_after_sales_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE after_sales (
  id TEXT NOT NULL PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  type TEXT NOT NULL CHECK(type IN ('REFUND_ONLY','RETURN_REFUND')),
  amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
  reason TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL CHECK(status IN ('MERCHANT_REVIEW','ADMIN_REVIEW','WAITING_RETURN','RETURNING','REFUND_PENDING','COMPLETED','REJECTED','CANCELLED')),
  merchant_note TEXT NOT NULL DEFAULT '',
  return_address TEXT NOT NULL DEFAULT '',
  carrier TEXT NOT NULL DEFAULT '',
  tracking_no TEXT NOT NULL DEFAULT '',
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

## audit_logs — 业务审计日志

用途：保存后台关键管理动作、对象及结果。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 operator_name 是操作当时快照，operator_id 不决定历史名称；同一操作者日后可能改名。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`request_id`|TEXT|NOT NULL|无|审计请求编号|
|`operator_id`|TEXT|NOT NULL|无|操作人编号|
|`operator_name`|TEXT|NOT NULL|无|操作当时名称快照|
|`module`|TEXT|NOT NULL|无|操作所属模块|
|`action`|TEXT|NOT NULL|无|事件或操作动作代码|
|`object_id`|TEXT|NOT NULL|无|被操作对象编号，按模块解释|
|`result`|TEXT|NOT NULL|无|本次操作结果，如成功|
|`summary`|TEXT|可空|无|内容摘要|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：无声明外键。

唯一索引：无独立唯一索引，主键以 DDL 为准。

<details><summary>当前建表语句</summary>

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

## cart_items — 购物车明细

用途：记录用户、商品、数量和是否参与结算。

非空候选键：(user_id, product_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`product_id`|TEXT|PK 2；NOT NULL|无|关联商品编号|
|`quantity`|INTEGER|NOT NULL|无|商品数量|
|`selected`|INTEGER|NOT NULL|1|购物车选中标志，0/1|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：product_id → products.id（删除 CASCADE）；user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_cart_items_1。

<details><summary>当前建表语句</summary>

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

## categories — 商品分类

用途：保存分类名称、分类类型标签、排序及启用状态。

非空候选键：(id)；(name)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 parent_name 是分类类型标签，没有父分类实体或分类树函数依赖。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`name`|TEXT|NOT NULL|无|名称|
|`parent_name`|TEXT|NOT NULL|无|分类类型标签；目前不是父分类外键|
|`sort_order`|INTEGER|NOT NULL|0|显示排序值|
|`status`|TEXT|NOT NULL|'ENABLED'|业务状态，取值及限制见下方 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：无声明外键。

唯一索引：sqlite_autoindex_categories_2；sqlite_autoindex_categories_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE categories (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  parent_name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'ENABLED' CHECK(status IN ('ENABLED','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

## chat_messages — 会话消息

用途：保存消息正文、发送身份及可选商品订单上下文。

非空候选键：(id)；(sender_id, sender_role, client_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 sender_role 是本次发送身份，与 sender_id 组成请求幂等身份；一个账号可切换角色。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`conversation_id`|TEXT|NOT NULL|无|关联会话编号|
|`sender_id`|TEXT|NOT NULL|无|发送人用户编号|
|`sender_role`|TEXT|NOT NULL|无|发送当时角色|
|`content`|TEXT|NOT NULL|无|正文|
|`product_id`|TEXT|可空|无|关联商品编号|
|`order_id`|TEXT|可空|无|关联订单编号|
|`client_id`|TEXT|NOT NULL|无|客户端去重编号；作用域见唯一约束|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：order_id → orders.id（删除 NO ACTION）；product_id → products.id（删除 NO ACTION）；sender_id → users.id（删除 NO ACTION）；conversation_id → conversations.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_chat_messages_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE chat_messages(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  sender_id TEXT NOT NULL REFERENCES users(id),
  sender_role TEXT NOT NULL CHECK(sender_role IN ('USER','MERCHANT')),
  content TEXT NOT NULL,
  product_id TEXT REFERENCES products(id),
  order_id TEXT REFERENCES orders(id),
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(sender_id,sender_role,client_id)
);
```

</details>

## checkout_groups — 跨店铺结算组

用途：一次结算对应一个买家，可按店铺拆出多张订单。

非空候选键：(id)；(user_id, idempotency_key)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`total_cents`|INTEGER|NOT NULL|无|订单或结算总金额，整数分|
|`status`|TEXT|NOT NULL|无|业务状态，取值及限制见下方 DDL|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|
|`idempotency_key`|TEXT|NOT NULL|无|业务幂等键|
|`request_hash`|TEXT|NOT NULL|无|请求内容摘要|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`expires_at`|TEXT|NOT NULL|无|过期时间|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_checkout_groups_2；sqlite_autoindex_checkout_groups_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE checkout_groups (
  id TEXT NOT NULL PRIMARY KEY,
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

## comments — 社区评论

用途：保存帖子下的用户评论。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`post_id`|TEXT|NOT NULL|无|关联社区帖子编号|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`content`|TEXT|NOT NULL|无|正文|
|`status`|TEXT|NOT NULL|'PUBLIC'|业务状态，取值及限制见下方 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 NO ACTION）；post_id → community_posts.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_comments_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE comments (
  id TEXT NOT NULL PRIMARY KEY,
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC',
  created_at TEXT NOT NULL
);
```

</details>

## community_post_images — 社区帖子图片

用途：按帖子及位置保存图片。

非空候选键：(post_id, position)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`post_id`|TEXT|PK 1；NOT NULL|无|所属帖子编号|
|`position`|INTEGER|PK 2；NOT NULL|无|从 0 开始的父记录内顺序，不按内容去重|
|`url`|TEXT|NOT NULL|无|资源地址|

外键：post_id → community_posts.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_community_post_images_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE community_post_images(post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(post_id,position));
```

</details>

## community_posts — 社区帖子

用途：保存买家或商家的阅读分享、可见性和关联商品。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 author_role 是发帖时身份，一个账号可有多角色；不能由当前角色覆盖历史身份。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`author_user_id`|TEXT|NOT NULL|无|帖子作者用户编号|
|`author_role`|TEXT|NOT NULL|无|发布当时角色|
|`title`|TEXT|NOT NULL|无|标题|
|`content`|TEXT|NOT NULL|无|正文|
|`cover_url`|TEXT|可空|无|封面资源地址|
|`product_id`|TEXT|可空|无|关联商品编号|
|`status`|TEXT|NOT NULL|'PUBLIC'|业务状态，取值及限制见下方 DDL|
|`report_count`|INTEGER|NOT NULL|0|帖子举报摘要计数|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：product_id → products.id（删除 NO ACTION）；author_user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_community_posts_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE community_posts (
  id TEXT NOT NULL PRIMARY KEY,
  author_user_id TEXT NOT NULL REFERENCES users(id),
  author_role TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  cover_url TEXT,
  product_id TEXT REFERENCES products(id),
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN','DELETED')),
  report_count INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

## community_uploads — 上传图片登记

用途：登记上传文件路径、所有者及可选帖子归属，也用于校验商家上传图片。

非空候选键：(path)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`path`|TEXT|PK 1；NOT NULL|无|上传文件相对 URL 路径|
|`user_id`|TEXT|NOT NULL|无|原上传用户，用于上传权限与引用校验|
|`post_id`|TEXT|可空|无|关联社区帖子编号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：post_id → community_posts.id（删除 SET NULL）；user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_community_uploads_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE community_uploads (
  path TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id TEXT REFERENCES community_posts(id) ON DELETE SET NULL,
  created_at TEXT NOT NULL
);
```

</details>

## conversation_reads — 会话已读游标

用途：保存会话中买家或商家一侧最后已读的消息编号。

非空候选键：(conversation_id, reader_role)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 复合键决定最后已读消息；消息必须属于本会话，服务事务校验。0 表示尚无已读消息，不是消息外键。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`conversation_id`|TEXT|PK 1；NOT NULL|无|关联会话编号|
|`reader_role`|TEXT|PK 2；NOT NULL|无|会话阅读侧：USER / MERCHANT|
|`last_message_id`|INTEGER|NOT NULL|0|此侧最后已读消息编号；0 为未读起点|

外键：conversation_id → conversations.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_conversation_reads_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE conversation_reads(
  conversation_id TEXT NOT NULL REFERENCES conversations(id),
  reader_role TEXT NOT NULL CHECK(reader_role IN ('USER','MERCHANT')),
  last_message_id INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(conversation_id,reader_role)
);
```

</details>

## conversations — 买家店铺会话

用途：为同一买家和商家建立固定私聊会话。

非空候选键：(id)；(buyer_id, merchant_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`buyer_id`|TEXT|NOT NULL|无|买家用户编号|
|`merchant_id`|TEXT|NOT NULL|无|所属或关联商家编号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：merchant_id → merchants.id（删除 NO ACTION）；buyer_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_conversations_2；sqlite_autoindex_conversations_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE conversations(
  id TEXT NOT NULL PRIMARY KEY,
  buyer_id TEXT NOT NULL REFERENCES users(id),
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(buyer_id,merchant_id)
);
```

</details>

## favorites — 商品收藏关联

用途：记录用户收藏的商品。

非空候选键：(user_id, product_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`product_id`|TEXT|PK 2；NOT NULL|无|关联商品编号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：product_id → products.id（删除 CASCADE）；user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_favorites_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE favorites (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id, product_id)
);
```

</details>

## feedback_replies — 反馈沟通记录

用途：保存平台回复和提交人的后续补充。

非空候选键：(id)；(sender_id, client_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`feedback_id`|TEXT|NOT NULL|无|关联反馈编号|
|`sender_id`|TEXT|NOT NULL|无|发送人用户编号|
|`sender_role`|TEXT|NOT NULL|无|发送当时角色|
|`content`|TEXT|NOT NULL|无|正文|
|`client_id`|TEXT|NOT NULL|无|客户端去重编号；作用域见唯一约束|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：sender_id → users.id（删除 NO ACTION）；feedback_id → feedbacks.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_feedback_replies_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE feedback_replies(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  feedback_id TEXT NOT NULL REFERENCES feedbacks(id),
  sender_id TEXT NOT NULL REFERENCES users(id),
  sender_role TEXT NOT NULL,
  content TEXT NOT NULL,
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(sender_id,client_id)
);
```

</details>

## feedbacks — 意见反馈

用途：保存用户或商家提交的问题和处理状态。

非空候选键：(id)；(user_id, client_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`submitter_role`|TEXT|NOT NULL|无|提交反馈当时角色|
|`title`|TEXT|NOT NULL|无|标题|
|`type`|TEXT|NOT NULL|无|反馈分类文本，由服务端输入校验|
|`content`|TEXT|NOT NULL|无|正文|
|`status`|TEXT|NOT NULL|无|业务状态，取值及限制见下方 DDL|
|`client_id`|TEXT|NOT NULL|无|客户端去重编号；作用域见唯一约束|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_feedbacks_2；sqlite_autoindex_feedbacks_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE feedbacks(
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  submitter_role TEXT NOT NULL,
  title TEXT NOT NULL,
  type TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('OPEN','PROCESSING','RESOLVED')),
  client_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(user_id,client_id)
);
```

</details>

## merchants — 商家与店铺

用途：保存店铺、负责人、入驻联系资料和审批经营状态。

非空候选键：(id)；(owner_user_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 联系资料是店铺入驻资料，不由负责人当前用户资料决定。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`owner_user_id`|TEXT|NOT NULL|无|商家负责人用户编号|
|`shop_name`|TEXT|NOT NULL|无|店铺名称|
|`description`|TEXT|NOT NULL|''|说明正文|
|`approval_status`|TEXT|NOT NULL|'PENDING'|入驻审批状态：PENDING / APPROVED / REJECTED|
|`business_status`|TEXT|NOT NULL|'PENDING'|经营状态：PENDING / OPEN / SUSPENDED|
|`rejection_reason`|TEXT|可空|无|驳回原因|
|`applied_at`|TEXT|NOT NULL|无|入驻申请或迁移应用时间|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`contact_name`|TEXT|NOT NULL|''|入驻联系人姓名|
|`contact_phone`|TEXT|NOT NULL|''|入驻联系电话|
|`business_address`|TEXT|NOT NULL|''|入驻经营地址|
|`reviewed_at`|TEXT|可空|无|审核时间|
|`reviewed_by`|TEXT|可空|无|审核操作者用户编号|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|

外键：reviewed_by → users.id（删除 NO ACTION）；owner_user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_merchants_2；sqlite_autoindex_merchants_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE merchants (
  id TEXT NOT NULL PRIMARY KEY,
  owner_user_id TEXT NOT NULL UNIQUE REFERENCES users(id),
  shop_name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  approval_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(approval_status IN ('PENDING','APPROVED','REJECTED')),
  business_status TEXT NOT NULL DEFAULT 'PENDING' CHECK(business_status IN ('PENDING','OPEN','SUSPENDED')),
  rejection_reason TEXT,
  applied_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  contact_name TEXT NOT NULL DEFAULT '',
  contact_phone TEXT NOT NULL DEFAULT '',
  business_address TEXT NOT NULL DEFAULT '',
  reviewed_at TEXT,
  reviewed_by TEXT REFERENCES users(id),
  version INTEGER NOT NULL DEFAULT 1
);
```

</details>

## notification_read_receipts — 通知已读事实

用途：记录用户确已读；历史时间未知时 read_at 保持 NULL。

非空候选键：(notification_id, user_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 复合键决定 read_at，记录存在即已读；NULL 表示旧时间未知，不能补造时间。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`notification_id`|TEXT|PK 1；NOT NULL|无|通知编号|
|`user_id`|TEXT|PK 2；NOT NULL|无|已读用户编号|
|`read_at`|TEXT|可空|无|实际已读时间；历史未知时为 NULL|

外键：user_id → users.id（删除 CASCADE）；notification_id → notifications.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_notification_read_receipts_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE notification_read_receipts (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT,
  PRIMARY KEY (notification_id,user_id)
);
```

</details>

## notification_reads — 旧通知已读关联

用途：历史原样保留的已读记录归档；活动读写使用 notification_read_receipts。

非空候选键：(notification_id, user_id)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`notification_id`|TEXT|PK 1；NOT NULL|无|关联通知编号|
|`user_id`|TEXT|PK 2；NOT NULL|无|所属或关联用户编号|
|`read_at`|TEXT|NOT NULL|无|已读时间|

外键：user_id → users.id（删除 CASCADE）；notification_id → notifications.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_notification_reads_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE notification_reads (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at TEXT NOT NULL,
  PRIMARY KEY (notification_id, user_id)
);
```

</details>

## notification_user_state — 个人通知状态

用途：仅保存用户对通知的个人隐藏状态。

非空候选键：(notification_id, user_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`notification_id`|TEXT|PK 1；NOT NULL|无|关联通知编号|
|`user_id`|TEXT|PK 2；NOT NULL|无|所属或关联用户编号|
|`deleted_at`|TEXT|可空|无|软删除或个人隐藏时间，NULL 表示未删除/隐藏|

外键：user_id → users.id（删除 CASCADE）；notification_id → notifications.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_notification_user_state_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE notification_user_state (
  notification_id TEXT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  deleted_at TEXT,
  PRIMARY KEY (notification_id, user_id)
);
```

</details>

## notifications — 系统通知

用途：保存定向或广播通知正文与受众。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`user_id`|TEXT|可空|无|定向接收用户；NULL 表示广播，按 audience 计算受众|
|`audience`|TEXT|NOT NULL|无|通知受众标识|
|`title`|TEXT|NOT NULL|无|标题|
|`content`|TEXT|NOT NULL|无|正文|
|`status`|TEXT|NOT NULL|'SENT'|业务状态，取值及限制见下方 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_notifications_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE notifications (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
  audience TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'SENT',
  created_at TEXT NOT NULL
);
```

</details>

## order_address_snapshots — 订单地址快照

用途：保存成交当时地址各字段及原键是否存在，独立于当前地址。

非空候选键：(order_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 order_id 决定整份成交快照；source_address_id 可能跨时间出现不同历史值，不决定本行其他字段。field_mask 是精确还原旧格式的技术元数据。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`order_id`|TEXT|PK 1；NOT NULL|无|所属订单编号|
|`field_mask`|INTEGER|NOT NULL|无|原地址 JSON 10 个已知键的存在位图，区别缺键与显式 NULL|
|`source_address_id`|TEXT|可空|无|历史来源地址编号，不引用可删除的当前地址|
|`source_user_id`|TEXT|可空|无|历史来源用户编号|
|`recipient`|TEXT|可空|无|成交收货人快照|
|`phone`|TEXT|可空|无|成交联系电话快照|
|`region`|TEXT|可空|无|成交地区快照|
|`detail`|TEXT|可空|无|成交详细地址快照|
|`source_is_default`|INTEGER|可空|无|历史地址默认标志原值|
|`source_created_at`|TEXT|可空|无|历史地址创建时间原值|
|`source_updated_at`|TEXT|可空|无|历史地址更新时间原值|
|`source_version`|INTEGER|可空|无|历史地址版本原值|

外键：order_id → orders.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_order_address_snapshots_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE order_address_snapshots(
 order_id TEXT NOT NULL PRIMARY KEY REFERENCES orders(id),field_mask INTEGER NOT NULL CHECK(field_mask BETWEEN 0 AND 1023),
 source_address_id TEXT,source_user_id TEXT,recipient TEXT,phone TEXT,region TEXT,detail TEXT,source_is_default INTEGER,
 source_created_at TEXT,source_updated_at TEXT,source_version INTEGER
);
```

</details>

## order_events — 订单操作事件

用途：保存创建、付款、发货、收货等已发生的状态变更。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`order_id`|TEXT|NOT NULL|无|关联订单编号|
|`actor_id`|TEXT|NOT NULL|无|操作者用户编号|
|`action`|TEXT|NOT NULL|无|事件或操作动作代码|
|`from_status`|TEXT|可空|无|操作前状态|
|`to_status`|TEXT|NOT NULL|无|操作后状态|
|`note`|TEXT|可空|无|操作说明|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：order_id → orders.id（删除 CASCADE）。

唯一索引：无独立唯一索引，主键以 DDL 为准。

<details><summary>当前建表语句</summary>

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

## order_items — 成交商品明细

用途：保存成交时商品名称、封面、单价和购买数量。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 名称、封面、单价是成交快照，当前 product_id 不决定这些历史值；小计在兼容投影计算。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`order_id`|TEXT|NOT NULL|无|关联订单编号|
|`product_id`|TEXT|NOT NULL|无|关联商品编号|
|`product_name`|TEXT|NOT NULL|无|成交时商品名称快照|
|`cover_url`|TEXT|可空|无|封面资源地址|
|`unit_price_cents`|INTEGER|NOT NULL|无|成交时商品单价，整数分|
|`quantity`|INTEGER|NOT NULL|无|商品数量|

外键：product_id → products.id（删除 NO ACTION）；order_id → orders.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_order_items_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE order_items (
  id TEXT NOT NULL PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL REFERENCES products(id),
  product_name TEXT NOT NULL,
  cover_url TEXT,
  unit_price_cents INTEGER NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity > 0)
);
```

</details>

## orders — 店铺子订单

用途：保存店铺子订单、结算组、确认金额与唯一履约状态；买家与有效期从结算组读取。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 checkout_group_id 非候选键，因此不再物理保存由它决定的 user_id、expires_at；API status 由 logistics_status 投影。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`checkout_group_id`|TEXT|NOT NULL|无|所属结算组编号|
|`merchant_id`|TEXT|NOT NULL|无|所属或关联商家编号|
|`total_cents`|INTEGER|NOT NULL|无|订单或结算总金额，整数分|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|
|`paid_at`|TEXT|可空|无|支付时间|
|`shipped_at`|TEXT|可空|无|发货时间|
|`completed_at`|TEXT|可空|无|完成时间|
|`cancelled_at`|TEXT|可空|无|取消时间|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`logistics_status`|TEXT|NOT NULL|'WAITING_PAYMENT'|履约状态代码，映射订单业务状态|

外键：merchant_id → merchants.id（删除 NO ACTION）；checkout_group_id → checkout_groups.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_orders_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE orders (
  id TEXT NOT NULL PRIMARY KEY,
  checkout_group_id TEXT NOT NULL REFERENCES checkout_groups(id),
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  total_cents INTEGER NOT NULL CHECK(total_cents >= 0),
  version INTEGER NOT NULL DEFAULT 1,
  paid_at TEXT,
  shipped_at TEXT,
  completed_at TEXT,
  cancelled_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  logistics_status TEXT NOT NULL DEFAULT 'WAITING_PAYMENT'
);
```

</details>

## payments — 结算支付记录

用途：记录整个结算组的演示付款。

非空候选键：(id)；(idempotency_key)；(checkout_group_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 checkout_group_id、idempotency_key 均为非空候选键；确认支付金额是支付事实，不能随商品改价重算。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`checkout_group_id`|TEXT|NOT NULL|无|所属结算组编号|
|`amount_cents`|INTEGER|NOT NULL|无|本记录金额，整数分|
|`method`|TEXT|NOT NULL|'DEMO'|当前付款方式如 DEMO_BALANCE / DEMO_ALIPAY / DEMO_WECHAT|
|`idempotency_key`|TEXT|NOT NULL|无|业务幂等键|
|`paid_at`|TEXT|NOT NULL|无|支付时间|

外键：checkout_group_id → checkout_groups.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_payments_3；sqlite_autoindex_payments_2；sqlite_autoindex_payments_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE payments (
  id TEXT NOT NULL PRIMARY KEY,
  checkout_group_id TEXT NOT NULL UNIQUE REFERENCES checkout_groups(id),
  amount_cents INTEGER NOT NULL,
  method TEXT NOT NULL DEFAULT 'DEMO',
  idempotency_key TEXT NOT NULL UNIQUE,
  paid_at TEXT NOT NULL
);
```

</details>

## post_likes — 社区点赞关联

用途：记录用户对帖子的点赞关系。

非空候选键：(post_id, user_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`post_id`|TEXT|PK 1；NOT NULL|无|关联社区帖子编号|
|`user_id`|TEXT|PK 2；NOT NULL|无|所属或关联用户编号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 CASCADE）；post_id → community_posts.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_post_likes_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE post_likes (
  post_id TEXT NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  PRIMARY KEY (post_id, user_id)
);
```

</details>

## product_revision_contents — 商品版本目录

用途：按版本及位置保存图书目录，允许重复标题。

非空候选键：(revision_id, position)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`revision_id`|TEXT|PK 1；NOT NULL|无|所属商品修订版编号|
|`position`|INTEGER|PK 2；NOT NULL|无|从 0 开始的父记录内顺序，不按内容去重|
|`title`|TEXT|NOT NULL|无|目录章节标题|

外键：revision_id → product_revisions.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_product_revision_contents_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE product_revision_contents(revision_id TEXT NOT NULL REFERENCES product_revisions(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),title TEXT NOT NULL CHECK(length(trim(title))>0),PRIMARY KEY(revision_id,position));
```

</details>

## product_revision_images — 商品版本图片

用途：按版本及位置保存详情图片，允许重复 URL。

非空候选键：(revision_id, position)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`revision_id`|TEXT|PK 1；NOT NULL|无|所属商品修订版编号|
|`position`|INTEGER|PK 2；NOT NULL|无|从 0 开始的父记录内顺序，不按内容去重|
|`url`|TEXT|NOT NULL|无|资源地址|

外键：revision_id → product_revisions.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_product_revision_images_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE product_revision_images(revision_id TEXT NOT NULL REFERENCES product_revisions(id) ON DELETE CASCADE,position INTEGER NOT NULL CHECK(position>=0),url TEXT NOT NULL CHECK(length(trim(url))>0),PRIMARY KEY(revision_id,position));
```

</details>

## product_revisions — 商品内容修订版

用途：保存版本内容、图书资料、价格及审核事实；图片和目录改由子表保存。

非空候选键：(id)；(product_id, version)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 作者名和简介是版本属性，不保证同名作者相同；不能推定 author_name → author_bio。价格为本版本报价。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`product_id`|TEXT|NOT NULL|无|关联商品编号|
|`version`|INTEGER|NOT NULL|无|同一商品的修订序号，与 products.version 的同步版本不同|
|`name`|TEXT|NOT NULL|无|名称|
|`summary`|TEXT|NOT NULL|无|内容摘要|
|`age_range`|TEXT|NOT NULL|无|适龄范围文本|
|`price_cents`|INTEGER|NOT NULL|无|商品版本价格，整数分|
|`cover_url`|TEXT|可空|无|封面资源地址|
|`audio_url`|TEXT|可空|无|配套音频资源地址|
|`ar_enabled`|INTEGER|NOT NULL|0|AR 标志，0/1；字段存在不代表已实现完整 AR 功能|
|`ar_resource_url`|TEXT|可空|无|可选 AR 资源地址|
|`review_status`|TEXT|NOT NULL|'DRAFT'|版本审核状态：DRAFT / PENDING / APPROVED / REJECTED|
|`rejection_reason`|TEXT|可空|无|驳回原因|
|`submitted_at`|TEXT|可空|无|提交审核时间|
|`reviewed_at`|TEXT|可空|无|审核时间|
|`reviewed_by`|TEXT|可空|无|审核操作者用户编号|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`author_name`|TEXT|NOT NULL|''|本版本文字作者署名|
|`illustrator_name`|TEXT|NOT NULL|''|本版本绘图作者署名|
|`publisher`|TEXT|NOT NULL|''|本版本发行出版社文本|
|`publication_date`|TEXT|NOT NULL|''|发售日期，YYYY-MM-DD 或空串|
|`author_bio`|TEXT|NOT NULL|''|本版本作者简介|
|`illustrator_bio`|TEXT|NOT NULL|''|本版本绘图作者简介|
|`editorial_review`|TEXT|NOT NULL|''|编辑推荐正文|
|`metadata_source`|TEXT|NOT NULL|''|图书资料来源标志，如 fictional_demo / manual|

外键：reviewed_by → users.id（删除 NO ACTION）；product_id → products.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_product_revisions_2；sqlite_autoindex_product_revisions_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE product_revisions (
  id TEXT NOT NULL PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  version INTEGER NOT NULL,
  name TEXT NOT NULL,
  summary TEXT NOT NULL,
  age_range TEXT NOT NULL,
  price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
  cover_url TEXT,
  audio_url TEXT,
  ar_enabled INTEGER NOT NULL DEFAULT 0,
  ar_resource_url TEXT,
  review_status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(review_status IN ('DRAFT','PENDING','APPROVED','REJECTED')),
  rejection_reason TEXT,
  submitted_at TEXT,
  reviewed_at TEXT,
  reviewed_by TEXT REFERENCES users(id),
  created_at TEXT NOT NULL,
  author_name TEXT NOT NULL DEFAULT '',
  illustrator_name TEXT NOT NULL DEFAULT '',
  publisher TEXT NOT NULL DEFAULT '',
  publication_date TEXT NOT NULL DEFAULT '',
  author_bio TEXT NOT NULL DEFAULT '',
  illustrator_bio TEXT NOT NULL DEFAULT '',
  editorial_review TEXT NOT NULL DEFAULT '',
  metadata_source TEXT NOT NULL DEFAULT '',
  UNIQUE(product_id, version)
);
```

</details>

## products — 商品主档

用途：保存所属商家、分类、发布版本、销售状态及库存。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`merchant_id`|TEXT|NOT NULL|无|所属或关联商家编号|
|`category_id`|TEXT|NOT NULL|无|商品分类编号|
|`published_revision_id`|TEXT|可空|无|当前对买家发布的商品修订版编号|
|`status`|TEXT|NOT NULL|'DRAFT'|业务状态，取值及限制见下方 DDL|
|`stock_available`|INTEGER|NOT NULL|0|当前可用库存数量，非负整数|
|`stock_reserved`|INTEGER|NOT NULL|0|结算占用的预留库存数量，非负整数|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|

外键：published_revision_id → product_revisions.id（删除 NO ACTION）；category_id → categories.id（删除 NO ACTION）；merchant_id → merchants.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_products_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE products (
  id TEXT NOT NULL PRIMARY KEY,
  merchant_id TEXT NOT NULL REFERENCES merchants(id),
  category_id TEXT NOT NULL REFERENCES categories(id),
  published_revision_id TEXT REFERENCES product_revisions(id) DEFERRABLE INITIALLY DEFERRED,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING_REVIEW','ONLINE','OFFLINE','REJECTED','PLATFORM_OFFLINE')),
  stock_available INTEGER NOT NULL DEFAULT 0 CHECK(stock_available >= 0),
  stock_reserved INTEGER NOT NULL DEFAULT 0 CHECK(stock_reserved >= 0),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);
```

</details>

## ratings — 订单商品评价

用途：记录已购商品评价、星级及可见性。

非空候选键：(id)；(order_item_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 order_item_id 非空唯一，是候选键；买家与商品从成交明细关联获取。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`order_item_id`|TEXT|NOT NULL|无|关联订单商品明细编号|
|`stars`|INTEGER|NOT NULL|无|评价星级，1 至 5|
|`content`|TEXT|NOT NULL|无|正文|
|`status`|TEXT|NOT NULL|'PUBLIC'|业务状态，取值及限制见下方 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`deleted_at`|TEXT|可空|无|软删除或个人隐藏时间，NULL 表示未删除/隐藏|

外键：order_item_id → order_items.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_ratings_2；sqlite_autoindex_ratings_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE ratings (
  id TEXT NOT NULL PRIMARY KEY,
  order_item_id TEXT NOT NULL UNIQUE REFERENCES order_items(id),
  stars INTEGER NOT NULL CHECK(stars BETWEEN 1 AND 5),
  content TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(status IN ('PUBLIC','HIDDEN')),
  created_at TEXT NOT NULL,
  deleted_at TEXT
);
```

</details>

## refunds — 演示退款流水

用途：保存每笔完成的演示退款，用于累计订单退款金额。

非空候选键：(id)；(after_sale_id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 after_sale_id 非空唯一，是候选键；订单从售后单获取。金额是已完成退款事实。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`after_sale_id`|TEXT|NOT NULL|无|关联售后申请编号|
|`amount_cents`|INTEGER|NOT NULL|无|本记录金额，整数分|
|`method`|TEXT|NOT NULL|无|固定 DEMO，只登记演示退款|
|`status`|TEXT|NOT NULL|无|固定 COMPLETED；已完成的演示流水|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：after_sale_id → after_sales.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_refunds_2；sqlite_autoindex_refunds_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE "refunds" (
  id TEXT NOT NULL PRIMARY KEY,
  after_sale_id TEXT NOT NULL UNIQUE REFERENCES after_sales(id),
  amount_cents INTEGER NOT NULL CHECK(amount_cents>=0),
  method TEXT NOT NULL CHECK(method='DEMO'),
  status TEXT NOT NULL CHECK(status='COMPLETED'),
  created_at TEXT NOT NULL
);
```

</details>

## schema_migrations — 数据库迁移记录

用途：登记已应用的迁移文件版本，防止重复执行。

非空候选键：(version)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`version`|TEXT|PK 1；NOT NULL|无|已应用迁移的版本文件名，不是整数并发版本|
|`applied_at`|TEXT|NOT NULL|无|迁移应用时间|

外键：无声明外键。

唯一索引：sqlite_autoindex_schema_migrations_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE schema_migrations(
  version TEXT NOT NULL PRIMARY KEY,
  applied_at TEXT NOT NULL
);
```

</details>

## sessions — 登录会话

用途：保存令牌摘要、有效期和当前使用的角色。

非空候选键：(token_hash)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`token_hash`|TEXT|PK 1；NOT NULL|无|登录令牌的摘要|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`active_role`|TEXT|NOT NULL|无|会话当前使用的角色|
|`expires_at`|TEXT|NOT NULL|无|过期时间|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_sessions_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE sessions (
  token_hash TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  active_role TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);
```

</details>

## settings — 服务设置

用途：按设置键保存服务配置值。

非空候选键：(key)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`key`|TEXT|PK 1；NOT NULL|无|设置或元数据键|
|`value`|TEXT|NOT NULL|无|设置或元数据值|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：无声明外键。

唯一索引：sqlite_autoindex_settings_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE settings (
  key TEXT NOT NULL PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
```

</details>

## stock_movements — 库存变动流水

用途：记录商品可用与预留库存的增减原因。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 同商品可有多次不同增减，流水金额/数量为事件事实；余额在同一事务维护。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|INTEGER|PK 1；INTEGER PK 自动分配|无|本表记录编号|
|`product_id`|TEXT|NOT NULL|无|关联商品编号|
|`order_id`|TEXT|可空|无|关联订单编号|
|`type`|TEXT|NOT NULL|无|业务类型，取值见 DDL 或业务校验|
|`available_delta`|INTEGER|NOT NULL|无|可用库存增量，可正可负|
|`reserved_delta`|INTEGER|NOT NULL|无|预留库存增量，可正可负|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：order_id → orders.id（删除 NO ACTION）；product_id → products.id（删除 NO ACTION）。

唯一索引：无独立唯一索引，主键以 DDL 为准。

<details><summary>当前建表语句</summary>

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

## sync_entity_map — 本地远程实体映射

用途：映射账号资源中的本地编号与服务端编号。

非空候选键：(user_id, resource, local_id)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`resource`|TEXT|PK 2；NOT NULL|无|同步资源类型代码|
|`local_id`|TEXT|PK 3；NOT NULL|无|设备本地实体编号|
|`remote_id`|TEXT|NOT NULL|无|服务端实体编号|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_sync_entity_map_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE sync_entity_map (
  user_id TEXT NOT NULL REFERENCES users(id),
  resource TEXT NOT NULL,
  local_id TEXT NOT NULL,
  remote_id TEXT NOT NULL,
  PRIMARY KEY(user_id,resource,local_id)
);
```

</details>

## sync_media — 同步媒体处理记录

用途：保存离线图片上传操作的摘要、地址与阶段。

非空候选键：(user_id, operation_id)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`operation_id`|TEXT|PK 2；NOT NULL|无|同步操作唯一编号|
|`sha256`|TEXT|NOT NULL|无|媒体内容 SHA-256 摘要|
|`url`|TEXT|NOT NULL|无|媒体资源地址|
|`purpose`|TEXT|NOT NULL|无|媒体业务用途代码|
|`state`|TEXT|NOT NULL|无|协议处理阶段，取值见 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_sync_media_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE sync_media (
  user_id TEXT NOT NULL REFERENCES users(id),
  operation_id TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  url TEXT NOT NULL,
  purpose TEXT NOT NULL,
  state TEXT NOT NULL CHECK(state IN ('PREPARED','READY')),
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id,operation_id)
);
```

</details>

## sync_receipts — 服务端同步回执

用途：保存离线操作的幂等回执与请求摘要。

非空候选键：(user_id, operation_id)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`operation_id`|TEXT|PK 2；NOT NULL|无|同步操作唯一编号|
|`client_id`|TEXT|NOT NULL|无|执行同步操作的客户端标识|
|`payload_hash`|TEXT|NOT NULL|无|操作请求载荷摘要|
|`result_json`|TEXT|NOT NULL|无|幂等操作回执 JSON 文本|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_sync_receipts_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE sync_receipts (
  user_id TEXT NOT NULL REFERENCES users(id),
  operation_id TEXT NOT NULL,
  client_id TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY(user_id,operation_id)
);
```

</details>

## sync_snapshots — 分页同步快照

用途：固定一次同步分页期间的数据集合，避免分页读取不一致。

非空候选键：(id)。

函数依赖：上述每个 K → 本表其余属性。本表列为技术/归档存储，不作全部业务明细已关系化的声明。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`user_id`|TEXT|NOT NULL|无|所属或关联用户编号|
|`resource`|TEXT|NOT NULL|无|同步资源类型代码|
|`data_json`|TEXT|NOT NULL|无|同步分页集合快照 JSON 文本|
|`expires_at`|TEXT|NOT NULL|无|过期时间|

外键：user_id → users.id（删除 NO ACTION）。

唯一索引：sqlite_autoindex_sync_snapshots_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE sync_snapshots (
  id TEXT NOT NULL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  resource TEXT NOT NULL,
  data_json TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
```

</details>

## user_roles — 账户角色关联

用途：一个账户可拥有多个角色，角色切换不复制账户。

非空候选键：(user_id, role)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`user_id`|TEXT|PK 1；NOT NULL|无|所属或关联用户编号|
|`role`|TEXT|PK 2；NOT NULL|无|角色代码|

外键：user_id → users.id（删除 CASCADE）。

唯一索引：sqlite_autoindex_user_roles_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE user_roles (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK(role IN ('USER','MERCHANT','ADMIN','OPERATOR','SUPPORT')),
  PRIMARY KEY(user_id, role)
);
```

</details>

## users — 用户账户

用途：保存登录标识、公开资料和密码摘要；买家、商家、管理员共用账户实体。

非空候选键：(id)；(username)。

函数依赖：上述每个 K → 本表其余属性。在当前业务语义下，未识别额外非超键决定非主属性的函数依赖；复合键属性依赖整个键。 email、phone 可空的唯一约束只保证非空值唯一，不作为全表候选键；id、username 才是非空候选键。

|字段|声明类型|非空/主键|默认值|含义|
|---|---|---|---|---|
|`id`|TEXT|PK 1；NOT NULL|无|本表记录编号|
|`username`|TEXT|NOT NULL|无|登录用户名|
|`email`|TEXT|可空|无|绑定邮箱，可未填写|
|`phone`|TEXT|可空|无|联系电话|
|`nickname`|TEXT|NOT NULL|无|公开昵称|
|`password_hash`|TEXT|NOT NULL|无|密码校验摘要，非明文密码|
|`avatar_url`|TEXT|可空|无|头像资源地址|
|`status`|TEXT|NOT NULL|'ACTIVE'|业务状态，取值及限制见下方 DDL|
|`created_at`|TEXT|NOT NULL|无|创建时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`updated_at`|TEXT|NOT NULL|无|最后更新时间；服务端业务通常保存 UTC ISO 8601 字符串|
|`version`|INTEGER|NOT NULL|1|乐观并发或同步版本号|

外键：无声明外键。

唯一索引：sqlite_autoindex_users_4；sqlite_autoindex_users_3；sqlite_autoindex_users_2；sqlite_autoindex_users_1。

<details><summary>当前建表语句</summary>

```sql
CREATE TABLE users (
  id TEXT NOT NULL PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  nickname TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  avatar_url TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISABLED')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1
);
```

</details>

## 兼容视图及维护边界

compat_orders、compat_order_items、compat_after_sales、compat_refunds、compat_ratings、compat_product_revisions、compat_community_posts 只向旧 API/同步合同投影。业务写入使用物理表及事务子表，不能将视图字段当作第二份事实写入。精确实现见 [兼容投影实现](../server/src/normalization/projections.ts)。

源表、目标表、主键、金额、事件、有序重复项、未知通知时间、幂等回执及自增高水位由副本基线测试核对；正式切换结果和覆盖边界见验收记录；旧字典作为历史版本保留。

