# Android 本地优先与联网同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 当前助手顺序实施；未经用户另行要求不委派子代理。

**Goal:** APP 首次无服务也能进入并完成本地业务操作，网络账号验证后可恢复、去重地同步到共享服务。

**Architecture:** 页面读写专属 SQLite 业务仓储；本地更新与持久化队列同事务，原始网络传输层只负责连接服务器。服务器白名单动作通过共享业务函数执行，与操作回执同事务提交；前台同步协调器处理附件依赖、账号绑定、重试、冲突和快照拉取。

**Tech Stack:** Java 17、Android Views/XML、SQLiteOpenHelper、Volley、现有 Android instrumentation runner、Express/TypeScript、better-sqlite3、node:test。

**Spec:** `docs/superpowers/specs/2026-10-14-local-first-and-startup-design.md`（2026-10-14 用户已同意继续；本计划待审核）。独立控制中心计划见 `2026-10-14-launcher-startup.md`，先执行该计划再进入本计划。

## Global Constraints

- 保持 Java/XML、Volley、React/Vite、Express/SQLite 技术栈和现有视觉体系。
- 首次安装无服务也能创建本地身份进入；买家和商家均有本地工作空间。
- 本地实体更新和队列写入必须同事务；写入失败不显示保存成功。金额用整数分。
- 网络注册、密码修改和找回必须在线完成，不把密码放进同步队列。
- 空间绑定后固定属于该服务器账号与服务地址；切换账号进入独立空间。
- 默认只在应用前台自动同步，退出后队列保留，下次进入自动恢复。
- 本地示例订单只在本地演示，不上传到真实服务器。
- 服务端实际确认后才能显示支付成功。真实支付接入不在范围内。
- 测试使用临时数据库与隔离附件目录，不用真实业务库造测试数据。
- 保留全部历史 APK、Release 和标签；本次未要求上传，不自动推送或发布。

## Review Focus

1. 服务器已提交、响应丢失后重试以及重复图片上传不能产生重复数据：任务 4、5、10。
2. 旧账号网络回调晚于切换账号返回，不得写入新空间或上传新账号内容：任务 2、10。
3. 拉取分页中断和另一端删除，不能把不完整列表当成删除，也不能冲掉待上传编辑：任务 4、10。
4. 手机磁盘写满、升级中断、裁剪临时文件丢失，不能显示保存成功或清库恢复：任务 1、5。
5. 离线价格快照过期且网络重连，不能静默按新金额支付或替用户推进订单：任务 9。

## 文件和公共接口地图

新文件均位于现有模块内部，不更换依赖体系。以下缩写仅用于本文：

- `A` = `android-app/app/src/main/java/com/huimeng/bookshop`；`AT` = `android-app/app/src/androidTest/java/com/huimeng/bookshop`。
- `A/core/local/LocalDatabase.java`：schema/迁移/事务；`LocalSpaceStore.java`：身份与空间；`LocalRepository.java`：异步本地业务门面；`LocalOperation.java`：不可变操作；`LocalMediaStore.java`：附件生命周期；`SampleCatalog.java`：自带可识别示例。
- `A/core/local/{CatalogRepository,BuyerRepository,CommunityRepository,MerchantRepository,OrderRepository}.java`：分业务查询、校验、状态投影与入队，避免单文件实现全业务。
- `A/core/sync/{SyncTransport,SyncCoordinator,SnapshotImporter,SyncStatusStore}.java`：原始 HTTP、单空间上传执行器、快照合并、可观察状态。
- `A/ui/SyncStatusActivity.java`、`android-app/app/src/main/res/layout/activity_sync_status.xml`：待同步记录与冲突详情。
- `server/src/sync/{types,operations,receipts,snapshots,media}.ts`：协议、动作分派、回执、可见资源快照、附件恢复。
- `server/src/services/{buyer,community,merchant,orders,profile}.ts`：从现有模块按本次动作需要提取的业务函数；原路由保持 HTTP 行为。
- `server/src/modules/sync.ts`、`server/migrations/008_local_first_sync.sql`：鉴权路由与持久结构。
- `AT/core/{LocalDatabaseTest,LocalIdentityTest,LocalBusinessTest,LocalMediaTest,LocalSyncTest}.java`、`AT/ui/LocalFirstFlowTest.java`：存储、业务、同步、UI 验证；测试只使用唯一命名空间和专属测试库。
- `server/test/{sync-protocol,sync-media,sync-business,sync-orders}.test.ts`：临时 DB/上传目录与随机端口的集成测试。

公共类型（由任务 1/3 建立，后续不得另起名称）：

- Java `LocalOperation`：`operationId,spaceId,resource,entityId,action,payload,baseVersion,dependencies,state,attempts,nextAttemptAt`；时间用毫秒 long，版本用 long，JSON 用 JSONObject/JSONArray。
- Java `LocalRepository.query(String spaceId,String resource,JSONObject filter,Consumer<JSONObject> ok,Consumer<String> fail):void`；`mutate(String spaceId,String action,JSONObject payload,Consumer<JSONObject> ok,Consumer<String> fail):void`。所有 IO 后台执行，回调主线程；回调结果显式含本地 ID 和同步状态。
- Java `SyncTransport.send(LocalOperation op,Consumer<JSONObject> ok,Consumer<TransportError> fail):void`；`TransportError` 具备 `status:int,retryAfterMs:long,message:String`，不依赖错误文案分类。
- TS `SyncEnvelope={protocolVersion:1,clientId:string,operationId:string,action:SyncAction,entityId?:string,baseVersion?:number,payload:Record<string,unknown>,payloadHash:string}`。
- TS `SyncResult={operationId:string,entityId?:string,version?:number,data:unknown,replayed:boolean}`；共享写函数 `applyOperation(actor:Actor,op:SyncEnvelope):{result:SyncResult,events:Array<{resource:string,action:string}>}`，同步且不自行广播；`server/src/sync/types.ts` 导出 `type Actor=NonNullable<Express.Request['actor']>`，复用现有请求类型。
- `SyncAction` 白名单：`address.save/delete`、`favorite.set`、`cart.set/remove`、`profile.save/avatar`、`post.create`、`comment.create`、`post.like`、`notice.read/delete`、`merchant.apply/profile`、`product.save/submit/offline`、`checkout.create/pay`、`order.cancel/confirm/ship/advance`、`rating.create`。不提供任意方法/URL 的代理执行。
- 载荷按 JSON 规范化序列化计算 SHA-256（对象键排序、数组顺序保留、禁止非有限数、UTF-8），服务器自行重算并核对；Java/TS 共用固定测试向量。入队内容不含密码/token。

## 开始实施前

- [ ] 读取批准的设计和两份计划；使用 using-git-worktrees 按环境检查并建立/复用隔离工作区；所有后续路径以实际工作区为根。分支默认 `HuGo/local-first-sync`，不搬动用户原目录。
- [ ] 使用 executing-plans、test-driven-development 和 verification-before-completion 的实际规则；按需要读取数据库/API/Android 实现技能。不得因计划文本直接跳过其适用前置步骤。
- [ ] 记录工具链、源码基线和原 APK 路径；保护既有业务数据。先解决可修复的 ADB 用户目录配置问题，使用工作区内专属目录，不读取或替换其他凭据。

## 执行命令约定

服务端定向：根目录 `node --import tsx --test server/test/<name>.test.ts`；遵循现有测试在设置 DATABASE_PATH/UPLOAD_DIR 后才动态导入数据库模块的模式。

Android：在 `android-app` 下 `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`。连接设备后的单类测试：`./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.huimeng.bookshop.core.<Class>`；UI 类改用 `.ui.<Class>`。不能将缺设备的运行失败当成功。

每个任务红灯阶段仅运行对应测试，先确认断言因目标行为缺失而失败；绿灯后扩大到受影响的原有测试。各任务末尾 `git diff --check`、核对并只提交所列文件，提交不包含业务数据和产物。

### Task 1: 持久化库、操作模型和升级迁移

**Files:** 创建 `A/core/local/{LocalDatabase,LocalOperation,LocalRepository,LocalSpaceStore,SampleCatalog}.java`、`AT/core/LocalDatabaseTest.java`；修改 `A/core/LocalCartStore.java`（仅迁移适配）。

**Interfaces:** 建立上述模型和仓储门面；`LocalDatabase(Context context,String databaseName)` 支持专属测试库；`LocalSpaceStore.ensureActiveSpace():String`、`bind(String spaceId,String origin,String userId):String`、`activeSpaceId():String`；绑定需外部明确确认，函数不执行网络登录。

- [ ] Step 1: 写 `testMutationAndQueueAreAtomic`（强制队列插入失败后实体不存在）、`testReopenPreservesPending`、`testSpaceAndOriginIsolation`、`testLegacyCartMigrationOnce`（重复迁移不累加）、`testUpgradeFailureKeepsOldData`、`testSampleNeverEnqueues`；每项明确数据库行数和原值断言。
- [ ] Step 2: 定向运行 LocalDatabaseTest，记录缺库/接口的预期失败。
- [ ] Step 3: 实现设计第 6 节五张表、索引、外键、事务和 schema 版本 1；队列状态固定 `PENDING/IN_FLIGHT/WAITING_AUTH/BLOCKED/CONFLICT/ACKED`；重启 IN_FLIGHT 回 PENDING，保留 UUID/载荷。迁移旧购物车只在成功事务后写迁移标记，失败不清库。
- [ ] Step 4: 定向重跑通过并检查后台 IO；模拟存储异常断言失败回调不产生成功事件。构建测试 APK。
- [ ] Step 5: 提交 `feat: add transactional local store and persistent outbox`。

### Task 2: 本地身份、免服务进入和账号关联

**Files:** 修改 `A/core/SessionStore.java`、`A/ui/{WelcomeActivity,MainActivity,LoginActivity,RoleSwitchActivity,SettingsActivity}.java`、`A/core/ApiClient.java`；创建 `AT/core/LocalIdentityTest.java`；按所改 binding 更新对应已有布局。

**Interfaces:** 消费 LocalSpaceStore；SessionStore 新增 `hasLocalSpace():boolean`、`clearNetworkSession():void`，保留已有 token API 行为供原调用迁移。进入判断用本地空间，不伪造 token；网络错误传输交由任务 10，先提供实际错误状态结构。

- [ ] Step 1: 写 `testFreshOfflineLaunchOpensMain`、`testNoRememberTokenRetainsLocalSpace`、`testUnauthorizedKeepsPending`、`testBindingRequiresConfirmedServerIdentity`、`testLateCallbackCannotChangeActiveSpace`；断言进入主页、不发生强制三秒等待、不把本地商家当服务器权限、不清空队列。
- [ ] Step 2: 执行 LocalIdentityTest，确认旧 welcome/token gate 导致预期失败。
- [ ] Step 3: 本地启动先建空间即进入；登录/注册移为网络关联入口；登录成功展示账号确认关联，取消不上传。已绑定其他账号不可重新绑定，切换至对应空间；退出网络登录不删除本地数据。401 保留页面并标记需登录，移除所有全局强制登出副作用。
- [ ] Step 4: 重跑测试及原登录/角色相关 instrumentation；检查网络账号切换、退出、未记住登录、商家被撤权与页面重建，不恢复未经授权的在线能力。
- [ ] Step 5: 提交 `feat: decouple local access from network authentication`。

### Task 3: 服务端原子回执、统一版本和共享动作入口

**Files:** 创建 `server/migrations/008_local_first_sync.sql`、`server/src/sync/{types,operations,receipts}.ts`、`server/src/modules/sync.ts`、`server/test/sync-protocol.test.ts`；修改 `server/src/{main,types.d}.ts` 及有写入的现有模块。

**Interfaces:** 新 `POST /api/v1/sync/operations` 消费 SyncEnvelope；`executeOnce(actor,op):SyncResult`（receipts.ts）负责事务、载荷核对、回执和提交后事件。任务 6–9 逐步注册白名单动作，在对应动作实现前返回明确 unsupported，不虚构成功。

- [ ] Step 1: `testAtomicReceipt` 断言失败时业务行数和回执均不变；`testReplayAfterResponseLoss` 断言同 UUID 重试只写一次；`testPayloadHashConflict`、`testCrossUserReceiptIsolated`、`testCanonicalHashVectors`、`testPermissionsBeforeReplay` 断言禁用用户/撤权不能靠旧回执取得其他权限内容。
- [ ] Step 2: 运行 sync-protocol.test.ts，确认路由/回执缺失失败。
- [ ] Step 3: 建立回执唯一约束 `(user_id,operation_id)`，存规范化载荷 hash、客户端 ID、响应和状态；同步域错误返回机器码和中文原因。地址/用户/商家/商品/通知状态等缺版本的实体加 version，相关网页与 APP 写入口均递增；订单复用已有 version。触发器仅用于机械递增，业务状态仍由共享函数校验。业务函数不广播，由外层成功提交后广播。
- [ ] Step 4: 重跑定向测试和现有服务端套件，重复迁移验证兼容；对未知动作、协议版本、超大载荷、假用户 ID、跨角色请求有确定拒绝结果。
- [ ] Step 5: 提交 `feat: add authenticated atomic sync receipts and versions`。

### Task 4: 可见资源快照与本地目录读取

**Files:** 创建 `server/src/sync/snapshots.ts`、`A/core/sync/SnapshotImporter.java`、`A/core/local/CatalogRepository.java`；修改 sync 路由、`A/ui/{HomeFragment,SearchActivity,CategoriesActivity,ProductDetailActivity,RatingsActivity}.java`；扩展 sync-protocol 与 LocalBusinessTest。

**Interfaces:** `GET /sync/snapshots/:resource?offset=<n>&limit=<1..100>&snapshotId=<id>`，响应 `{snapshotId,items,nextOffset,complete}`；服务器为每个账号/资源物化一致快照到带 15 分钟过期时间的临时持久记录，后页只读取该快照，过期返回 409 要求重启下载。`SnapshotImporter.applyPage(spaceId,resource,page):void` 暂存分页，仅 complete 后原子发布；公开 catalog 无关联也可通过原有目录接口获取，但写入指定空间。

资源名固定：`categories/products/productRatings/favorites/cart/addresses/profile/posts/comments/likes/notifications/merchantApplication/merchantProducts/buyerOrders/merchantOrders/ownRatings`。posts/comments 仅含当前可见数据；商品详情附 gallery/audio/AR 元数据，订单详情附行项与历史，避免只缓存列表却无法打开详情。新增快照记录表与查询索引追加进迁移 008（未发布前），不另建无任务定义的表。

- [ ] Step 1: 创建 `AT/core/LocalBusinessTest.java` 后添加 `testSnapshotVisibilityAndPaging` 验证他人地址/订单不可见、分页中途新增/删除不会漏/重复；`testIncompleteSnapshotDoesNotDelete`、`testPendingEditSurvivesPull`；目录离线搜索断言仅匹配本地文本、分类和可见商品，示例显式标记。
- [ ] Step 2: 运行服务端定向测试与 LocalBusinessTest，确认分页/本地读行为缺失失败。
- [ ] Step 3: 资源白名单覆盖设计第 5 节查询，快照采用现有可见性规则且保留版本。实现确认快照与本地未上传投影分离；本地查询驱动 UI，刷新仅触发同步，不让 spinner 等网络才结束。无下载媒体明确空态。
- [ ] Step 4: 重跑测试，检查完整快照后隐藏/删除才生效，快照过期/断点重启不改变待上传数据；本地示例不参与真实库存。
- [ ] Step 5: 提交 `feat: render local catalog and import scoped snapshots`。

### Task 5: 图片私有保存、去重上传与恢复

**Files:** 创建 `A/core/local/LocalMediaStore.java`、`server/src/sync/media.ts`、`AT/core/LocalMediaTest.java`、`server/test/sync-media.test.ts`；修改 sync 路由、`A/ui/{ProductImageState,ProductImageEditor,PublishPostActivity,EditAccountActivity}.java`、`A/core/ApiClient.java`、AndroidManifest；创建 Android 备份排除规则 XML。

**Interfaces:** `LocalMediaStore.saveJpeg(spaceId,byte[]):String` 返回附件本地 UUID；`resolveUri(spaceId,mediaId):Uri`；`POST /sync/media` 接 JPEG，headers 含操作 UUID、SHA-256 和用途；回复 `{operationId,url,sha256,replayed}`。调用前必须验证当前空间网络账号与用途权限。

- [ ] Step 1: `testPrivateCopySurvivesCropDeletion`、`testDiskFailureDoesNotSaveReference`、`testAccountIsolation`；服务器 `testMediaRetryReusesUrl`、`testChangedBytesRejectSameKey`、`testCrashAfterRenameRecovers`，断言文件数、用户归属和回执匹配；JPEG 限制保持 3 MB、有效起止标记和现有用途规则。
- [ ] Step 2: 运行 LocalMediaTest/sync-media.test.ts，确认当前依赖网络上传与临时文件行为失败。
- [ ] Step 3: 实现临时文件写入、校验、原子改名、引用登记与恢复；服务器使用可恢复的上传状态记录，重试核对文件 hash 和归属。UI 图片编辑保存改为本地 URI，不在成功上传前删除源副本；素材先于实体同步。
- [ ] Step 4: 重跑测试，分别验证数据库/文件中断状态；排除离线 DB、WAL/SHM、附件和队列凭据的自动备份与设备迁移，检查 API 26 与 API 31+ 规则。既有网上 URL/封面显示保持。
- [ ] Step 5: 提交 `feat: persist local media and recover deduplicated uploads`。

### Task 6: 买家资料、地址、收藏、购物车与通知

**Files:** 创建 `A/core/local/BuyerRepository.java`、`server/src/services/{buyer,profile}.ts`、`server/test/sync-business.test.ts`；修改 auth/shop/community 原路由与同步注册、`A/core/LocalCartStore.java`、`A/ui/{CartActivity,AddressesActivity,AddAddressActivity,EditAccountActivity,ProfileFragment,NotificationFragment,NotificationsActivity,NotificationDetailActivity,DataListActivity}.java`；扩展 LocalBusinessTest。

**Interfaces:** BuyerRepository 通过 LocalRepository 实现相应白名单动作和查询；服务端共享动作接收 actor/envelope，返回统一 SyncResult。密码与注册仍走在线 auth；昵称/用户名/头像仅队列可修改资料。

- [ ] Step 1: `testOfflineBuyerWritesSurviveReopen` 断言地址/默认地址/收藏/购物车/通知/昵称投影与待同步数；`testCartSetDoesNotIncrementOnReplay`、`testAddressVersionConflict`、`testNotificationVisibility`、`testNoPasswordsInOutbox`；原账户密码混合保存测试保持原子。
- [ ] Step 2: 执行两端定向测试确认缺失行为失败。
- [ ] Step 3: 提取和复用现有校验，按目标状态同步；默认地址更新同事务。页面不再先请求网络才能操作。旧购物车对外接口迁移到本地仓储，通知 bulk 操作拆成可追踪实体意向或同事务批量入队。
- [ ] Step 4: 定向绿灯并运行 account-update/default-avatars/bookstore-flows 相关回归；版本冲突保留两份数据，用户名占用不覆盖原账号。
- [ ] Step 5: 提交 `feat: enable offline buyer data and profile operations`。

### Task 7: 社区帖子、评论、点赞与个人内容

**Files:** 创建 `A/core/local/CommunityRepository.java`、`server/src/services/community.ts`；修改原 community 路由、同步注册、`A/ui/{CommunityFragment,CommunityDetailActivity,PublishPostActivity,UserProfileActivity}.java` 及引用媒体的 adapter；扩展 LocalBusinessTest/sync-business.test.ts。

**Interfaces:** 注册 `post.create/comment.create/post.like`；评论 payload 用本地/远端父 ID，出网前以已确认映射冻结成远端 ID；本地内容查询返回与现有 CommunityPost 兼容的数据及显式 syncState。

- [ ] Step 1: `testOfflinePostCommentDependencies` 断言附件/帖子/评论顺序；`testLikeCoalescesOnlyUnsent` 断言已发送操作不可修改；`testHiddenParentBlocksComment`、`testResponseLossNoDuplicatePost`；检查重复点赞与取消的最终目标状态。
- [ ] Step 2: 定向运行确认失败。
- [ ] Step 3: 从路由提取原图片归属、可见性和文本约束，接通本地列表/详情/个人内容；保存显示“已保存本地，待同步”。服务器 ID 回写同事务更新所有引用。
- [ ] Step 4: 重跑两端测试及 community-profile，图片、评论数、点赞和旧封面数据保持兼容；已隐藏父帖保留失败意向而不重建已删除内容。
- [ ] Step 5: 提交 `feat: support offline community authoring and reactions`。

### Task 8: 商家申请、资料和商品管理

**Files:** 创建 `A/core/local/MerchantRepository.java`、`server/src/services/merchant.ts`；修改 merchant-applications/merchant-products/shop 中相关路由与同步注册、`A/ui/{MerchantApplicationActivity,MerchantDashboardFragment,MerchantProductsFragment,MerchantProfileFragment,AddProductActivity,ManageProductActivity,SectionFragment}.java`；扩展两端业务测试。

**Interfaces:** 注册 `merchant.apply/profile`、`product.save/submit/offline`；本地商家准备权限独立于 SessionStore.canMerchant，服务器每次核对当前 APPROVED/OPEN。店铺资料若现有客户端入口未提供，沿用申请/店铺资料页面扩展编辑，不新建管理后台。

- [ ] Step 1: `testLocalMerchantWithoutServerRole` 断言可建商品而网络不能越权；`testRevokedMerchantBlocksUpload`、`testProductReviewVersionConflict`、`testApplicationResubmitRules`、`testCategoryDeletedBlocksProduct`；附件失效不留下半件商品。
- [ ] Step 2: 定向红灯运行并保存断言输出。
- [ ] Step 3: 本地持久化店铺申请、商品草稿和上下架/送审意向，联网上传复用原审核/分类/图片归属规则；分类无远端匹配时进入处理列表，不按名称自动造服务端分类。
- [ ] Step 4: 重跑定向测试和 interface-completion 及商家权限回归；本地送审不显示审核通过，其他商家商品不可修改。
- [ ] Step 5: 提交 `feat: add offline merchant workspace and queued publishing`。

### Task 9: 订单与支付意向的双状态

**Files:** 创建 `A/core/local/OrderRepository.java`、`server/src/services/orders.ts`、`server/test/sync-orders.test.ts`；修改 shop 订单/结算路由、同步注册、`A/ui/{CheckoutActivity,PaymentActivity,PaymentSuccessActivity,OrdersActivity,OrderDetailActivity,MerchantOrdersFragment,MerchantOrderDetailActivity,RatingActivity}.java`；扩展 LocalBusinessTest。

**Interfaces:** 注册 `checkout.create/pay`、`order.cancel/confirm/ship/advance`、`rating.create`；本地结果区分 `confirmedState` 与 `pendingAction`。结算操作含 `{items:[{productId,quantity,expectedPriceCents}],addressId,expectedTotalCents}`，服务端事务内重算并核对；付款意向依赖创建回执，必须绑定用户确认过的金额与版本。

- [ ] Step 1: `testOfflineCheckoutRetainsSnapshots`、`testPriceChangeRequiresConfirmation`、`testInsufficientStockNoPayment`、`testExpiredCheckoutDoesNotRegenerateAndPay`、`testResponseLossNoDuplicateCharge`、`testPendingShipDoesNotShowShipped`、`testSampleOrdersNeverUpload`；检查创建/付款/发货等副作用与回执同事务。
- [ ] Step 2: 运行 sync-orders.test.ts 和 LocalBusinessTest，确认失败原因属于缺离线/同步行为。
- [ ] Step 3: 复用现有订单状态机、支付幂等和通知事件；增加同步入口预期价格/金额/版本核对，不改变旧客户端请求契约。离线付款页面显示“付款意向已保存”，只有已确认结果进入支付成功页；本地示例订单隔离所有上传和真实库存。
- [ ] Step 4: 定向绿灯及 bookstore-flows/order-notifications/admin-management 回归；验证取消/收货/发货/物流/评价完整流程与状态冲突，不自动重建过期订单付款。
- [ ] Step 5: 提交 `feat: synchronize order intents with confirmed server states`。

### Task 10: 前台同步协调、重试、状态与冲突处理

**Files:** 创建 `A/core/sync/{SyncTransport,SyncCoordinator,SyncStatusStore}.java`、`A/ui/SyncStatusActivity.java`、对应布局、`AT/core/LocalSyncTest.java`；修改 HuiMengApp、AccountSync、ApiClient、MainActivity、SettingsActivity、ProfileFragment/MerchantProfileFragment 与 AndroidManifest。

**Interfaces:** `SyncCoordinator.start(spaceId):void`、`stop():void`、`requestSync():void`；依赖 LocalRepository/LocalMediaStore/SnapshotImporter/SyncTransport；`SyncStatusStore.observe(owner,Consumer<JSONObject>):void` 通知状态。冲突操作提供“保留服务器版本/以当前版本重新提交本地修改”，后者生成新 UUID；财务或状态不允许则保持阻塞。

- [ ] Step 1: `testRetrySchedule` 使用可注入时钟断言约 2/5/15/30/60 秒、上限五分钟并带抖动；`test429RetryAfter`、`testOneRunnerPerSpace`、`testRestartReplaysSamePayload`、`testLateResponseAfterSwitchIgnored`、`testPullCannotOverwriteUnsent`、`testConflictResolutionCreatesNewId`、`testUnauthenticatedNeverUploads`。
- [ ] Step 2: 运行 LocalSyncTest，确认协调与状态缺失失败。
- [ ] Step 3: 实现应用前台生命周期与网络回调，补 ACCESS_NETWORK_STATE；未绑定/未确认空间不上传，health 成功后 auth/me 核对固定 userId/origin。冻结载荷后发送；只确认已成功回执的操作，再拉资源快照并重放本地未确认操作。事件订阅只触发协调器刷新，去掉各隐藏页面重复五秒网络请求。
- [ ] Step 4: 实现状态入口与待同步/冲突详情、重试、丢弃确认；按错误码决定重试/登录/阻塞。运行定向测试并检查页面销毁、切换空间、后台/前台、断网重连、读写异常不崩溃。
- [ ] Step 5: 提交 `feat: coordinate foreground sync and actionable conflict states`。

### Task 11: 完整联调、安装包与验证报告

**Files:** 创建 `AT/ui/LocalFirstFlowTest.java`、`docs/verification/2026-10-14-local-first-sync.md`；修改 README、android-app/BUILD_NOTES.md、docs/CHANGELOG.md；版本与发布文件按实际交付条件更新。

**Interfaces:** 测试连接独立服务端进程和临时数据；启动、关闭和重开均仅操作测试进程。APK 输出 `output/local-first-<version>/huimeng-<version>.apk`，附 SHA-256、签名、内部版本和测试摘要。

- [ ] Step 1: 添加端到端用例，严格覆盖设计第 10 节八项验收；先在旧行为基线复现“关闭服务无法进入”。准备升级安装用旧 APK，不卸载保留数据；首次安装场景用独立测试设备/快照，不清除用户真实应用数据。
- [ ] Step 2: 运行 `npm test`、`npm run lint`、`npm run build` 与 Android assembleDebug/assembleDebugAndroidTest/lintDebug；设备可用时运行新增与受影响的原 instrumentation，并实际点击断网/重连/跨端流程。每次失败先定位，不削弱测试断言过关。
- [ ] Step 3: 生成并实际查看 APP 本地模式、同步状态、待确认订单及控制中心截图；通过可用 Chrome/Edge 连接器核对网页收到地址/帖子/商家商品/订单及权限状态。UI 有具体缺陷时修复后重查受影响部分，不引入新风格探索。
- [ ] Step 4: 完成本地源码审查与全范围检查；按 requesting-code-review 实际规则选择可用审查能力，不能未经授权派子代理。读取 finishing-a-development-branch 处理集成；保持分支可回退，不自动推送。报告每项通过/失败/未验证和启动实测值，不把脚本接口通过当成真机通过。
- [ ] Step 5: 构建最终安装包并独立保存，建议新增兼容功能版本 1.1.0 / versionCode 27；实施时重新核对当前版本后再递增，不能覆盖已存在版本。检查内部 applicationId/版本、签名、SHA-256 与源码提交对应关系；仅构建新版本不代表已上传。交付 APK、报告、实际限制和本地提交，历史包完整保留。用户要求发布时才按项目发布规则执行。

## 自审与实施门槛

- [ ] 设计第 4–8 节分别落实任务 1–10；第 5 节每行可对应业务测试与 UI，不把“接口支持”当成页面已经离线可用。
- [ ] Java/TS 操作名、协议类型与 hash 向量一致；新增页面注册、binding、权限和备份规则均检查；网络错误不能伪装成功。
- [ ] 两份计划审核后由当前助手按顺序实施；任何阶段发现设计冲突先提出具体差异，不擅自降低全业务覆盖范围。

当前为计划交付，所有复选框保持未执行；不在本轮运行产品测试或生成 APK。下一阶段从控制中心计划的 Task 1 开始。
