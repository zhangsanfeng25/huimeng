# 界面补充与商家申请 Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 完成三个 Android 界面模块和商家申请到后台审核实时授权闭环。
**Architecture:** 增量扩展现有共享 API、数据库迁移与 SSE。App 保持 Java/XML，网页保持 React 现有组件。
**Tech Stack:** Java 17、Android Material 3 XML、Volley、React/TypeScript、Express、SQLite。
**Spec:** docs/superpowers/specs/2026-10-04-interface-completion-design.md

## Global Constraints
- 沿用现有格式、配色、圆角与 Java/XML、React 技术栈。
- 保留业务数据库与上传素材，增量迁移；验证使用临时数据库。
- 图片最多九张；待审核商品不能编辑；服务端决定商家权限。

## Review Focus
- 重复提交、驳回后重新申请：不能重复创建店铺，旧会话能获得通过后的权限。
- 停用或撤销：已登录商家不能继续操作商品。
- 商品只修改文字：保留历史图片、音频与版本数据。
- 后台与 App 断网、前后台切换：重连并刷新，不在后台持续轮询。
- 搜索、空评价、缺图：完整空态、失败重试、账户隔离。

### Task 1: 共享 API 与权限
**Files:** server/migrations/007_merchant_application.sql; server/src/modules/merchant-applications.ts; server/src/modules/shop.ts; server/src/modules/admin.ts; server/src/modules/auth.ts; server/src/core/http.ts; server/test/interface-completion.test.ts
**Interfaces:** /me/merchant-application GET/POST; /auth/switch-role POST; /me/events GET; /catalog/products/:id/ratings GET; merchant product detailImages、coverUrl。
- [x] 先添加申请、重复提交、通过后旧会话、驳回重提、停用、图片归属与保留、公开评价分页测试，运行确认失败。
- [x] 实现迁移、申请/审核事务、实时事件、动态权限与商品图文、评价接口。
- [x] 运行 server 测试与构建，确认通过。

### Task 2: Android 界面与同步
**Files:** 新增 SearchActivity、CategoriesActivity、RatingsActivity、MerchantApplicationActivity、AccountSync、ProductImageEditor；修改 RoleSwitchActivity、商品编辑页、HomeFragment、ProductDetailActivity、SessionStore 与 Manifest。
**Interfaces:** 使用 Task 1 API；复用 CropImageActivity、ProductAdapter、当前主题。
- [x] 添加实际页面导航、申请按钮与历史/空态的 Android 仪器测试，先运行失败。
- [x] 完成页面、图文编辑和前台 SSE/断线同步。
- [x] 构建 APK，运行模拟器界面和交互验证。

### Task 3: 网页审核与交付
**Files:** html/src/components/MerchantApplicationReview.tsx; html/src/pages/ManagementPage.tsx; docs/interface-completion-20261004.md; output/interface-completion-20261004。
**Interfaces:** /merchants/:id/details、/merchants/:id PUT；realtime('merchants')。
- [x] 实现申请资料与通过/驳回按钮，复用原模态框与样式。
- [x] 使用临时数据库测试网页自动出现申请、审核后旧 App 会话切换及商品图片同步。
- [x] npm run build、npm run lint、npm test；Android 构建与 UI 测试；保存 APK 和截图。
- [x] 独立审查关键权限、图文保留、重连与页面状态，修正实际问题，记录交付。

