# 商品详情与背景设置 Implementation Plan

> **For agentic workers:** Use executing-plans for inline implementation; retain existing uncommitted category changes. No implementation delegation. Request one independent final review.

**Goal:** 实施已确认的 A 方案，补齐 20 本虚构图书资料、可取消背景选择及多端维护与离线显示。

**Architecture:** 图书资料属于商品版本；服务端通过同一字段映射维护和读取，公开详情仅取已发布版本。Android 独立解析资料及弹层，交易 Product 模型不变。本地 JSON 与公开内置增量资料保存相同字段。

**Tech Stack:** Android Java/XML/Material、Express/TypeScript/Zod/SQLite、React/TypeScript。

**Spec:** ../specs/2026-10-17-book-details-appearance-design.md（用户已确认 A）。

## Global Constraints

- 保留已有分类改动、媒体、用户、订单、库存、价格及真实评价；数据库先备份。
- 不重置离线数据；不覆盖待同步写入或人工修改的资料；不把虚构资料作为真实出版事实。
- 采用现有配色、原生 Java/XML 和主题；最低 API 26，交互触点至少 48dp。
- 本次制作与本地 APK 验证，不因先前上传任务而自动再次发布。

## Review Focus

1. 老客户端省略字段时保留；显式空值可以清空；坏日期和过长目录被拒绝。
2. 草稿与已发布版本分离；商家编辑、管理端修改和审核不得丢失图书资料。
3. 老设备离线升级不能覆盖待同步内容、已确认更新或既有交易数据。
4. 弹层长文/长目录、屏幕重建和字号放大不得截断或遮住关闭/购买操作。
5. 推荐排除当前书、重复及不可见商品；空评价不造评分。

## Tasks

### 1. 商品资料及虚构内容

Files: server/migrations/009_book_details.sql; server/src/services/book-details.ts; server/assets/fictional-books.json; server/test/book-details.test.ts; server/scripts/fill-book-details.ts.

- [x] RED: 新迁移、20 本资料唯一性、字段验证、保留旧值、显式清空及幂等填充测试。
- [x] 实现字段映射 bookColumns、bookInput、readBookDetails、saveBookDetails，严格日期/目录长度。
- [x] 编写每本独立简介、目录、作者、出版社、日期和编辑推荐；填充仅定位匹配的演示商品、目标版本。
- [x] GREEN: 定向 node 测试；保留修改前字段与填充摘要供复核。

### 2. API、审核与网页维护

Files: server/src/services/{product-data,products,shop-data}.ts; server/src/modules/{shop,admin}.ts; html/src/components/ProductDetails.tsx.

- [x] RED: 公开详情/商家保存/管理维护/审核/同步快照字段保持及推荐规则。
- [x] 保存使用事务与统一映射；省略新字段时继承原值；公开元数据取发布版本。
- [x] 网页详情与编辑表单补全资料；目录以换行文本编辑，传输为数组。
- [x] GREEN: 服务端全套、网页 lint/build。

### 3. Android 背景选择

Files: SettingsActivity.java; WallpaperStore.java; activity_settings.xml; BookDetailsTest.java.

- [x] RED: 六入口可见、单击选择/同项再次取消、重启和浅深色保持。
- [x] 独立头像按钮；三列两行；SOLID 与默认未选择分开，默认旧枚举兼容。
- [x] GREEN: Android 仪器测试及实际截图。

### 4. Android 详情、弹层、商家编辑与离线升级

Files: BookDetails.java; BookDetailsSheet.java; BookMetadataEditor.java; ProductDetailActivity.java; activity_product_detail.xml; AddProductActivity.java; ManageProductActivity.java; CatalogRepository.java; LocalBootstrap.java; MerchantRepository.java; assets/book-details/fictional-v1.json.

- [x] RED: 详情元数据、无试听、展开简介、目录/作者弹层、重建、推荐与商家字段保存。
- [x] 独立资料解析及可收起表单；保留封面放大、收藏、购物车、购买及库存逻辑。
- [x] 目录/作者主题化顶部 28dp 圆角弹层，滚动、安全区、关闭、返回、状态恢复。
- [x] 给现有演示记录补齐缺失字段；pending 写入和非空资料不被旧内置资料覆盖；新安装与离线推荐可用。
- [x] GREEN: assembleDebug/assembleDebugAndroidTest/lintDebug、定向仪器测试、浅深色与小屏截图。

### 5. 实际数据与交付

- [x] 备份实际 SQLite 并输出更新清单；事务填充 20 本；对比受保护表/字段和 quick_check。
- [x] 更新本地演示资料；验证真实服务与离线升级；保留既有媒体及版本安装包。
- [x] 本地新版本 1.1.3 / 30；检查 APK 内部版本、签名、SHA-256，保存到独立 output 目录。
- [x] 独立代码审查，修正明确问题后执行相关回归；记录真实验证范围与限制。

## Execution ledger

- 2026-10-17: 用户确认 A 方案及虚构资料内容授权。直接在当前会话逐项实施，保留原工作区改动，不重建项目或重新初始化数据库。

- Ruling: 公开虚构资料单独存入 assets/book-details/fictional-v1.json；保留 bootstrap/ 整目录忽略规则，避免意外纳入已有账号或社区导出数据。

- 2026-10-17: 实施及最终交付复核完成。真实库先备份再填充 20 本，受保护数据一致。完整 npm test、lint/build、Android 构建、原有 51 项及图书/商家专项通过；独立审查提出的小屏头部已修复。详细证据与限制见 docs/verification/2026-10-17-book-details.md。
- Ruling: 商家新增、编辑与送审均纳入资料维护。用户再次明确商家端可修改后，补充真实页面保存/送审按钮测试，并将资料分区独立放在价格库存与图片之间。
- Ruling: 小屏截图另发现购买按钮文字截断，加入单行及文字宽度断言后修复；保持购买和购物车逻辑不变。

- Final verification: 最终 APK 17 项通过，另行通过 320dp/1.3 字号购买文字完整性和深色弹层检查。交付文件为 output/android-1.1.3/final/huimeng-1.1.3.apk，签名与 SHA-256 已重新核对；未提交或上传 GitHub。
