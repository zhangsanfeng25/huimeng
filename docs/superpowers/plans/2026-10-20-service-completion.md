# 三端服务闭环 Implementation Plan

> **For agentic workers:** Use executing-plans in this session; preserve existing changes. User explicitly requested implementation after approving the in-chat design.

**Goal:** 打通购买同步并提供完整分类、售后、聊天、反馈。
**Architecture:** 服务端业务状态为准，新增专用模块和表；网页独立服务管理页；Android 复用页面组件和认证网络请求。
**Tech Stack:** Java/XML, React/TypeScript, Express, better-sqlite3.
**Spec:** docs/superpowers/specs/2026-10-20-service-completion-design.md

## Global Constraints
不清空数据、不发布、不提交其他已有变更。整单全额、演示退款、现有视觉体系。

## Review Focus
账号切换缓存隔离；售后与发货并发；重试重复写入；本地未认证操作不可伪装成功；多店铺订单隔离。

### Task 1: 复现同步链路
- [x] 检查运行设备、服务、绑定身份、队列、回执、事件和网页版本。
- [x] 新增失败回归并修复根因，记录证据。

### Task 2: 数据与业务 API
Files: server/migrations/010_customer_services.sql, server/src/services/customer-services.ts, server/src/modules/customer-services.ts, server/src/main.ts, server/test/customer-services.test.ts
Interfaces: /service/after-sales, /service/conversations, /service/feedback; /service/admin/after-sales and /service/admin/feedback.
- [x] 先写临时库测试、确认失败。
- [x] 实现权限、状态机、幂等、通知、增量迁移。
- [x] 测试拒绝越权、重复退款及发货冲突。

### Task 3: 网页管理
Files: html/src/pages/ServicePage.tsx, html/src/App.tsx, html/src/components/AppShell.tsx.
- [x] 增加售后和反馈页面、状态筛选、详情时间线与审批按钮。
- [x] 保留分类页，检查实际路由与三端更新。
- [x] build/lint 及浏览器交互验证。

### Task 4: Android 服务页面
Files: ServiceActivity.java, ServiceClient.java, profile/order/product/message entry points.
- [x] 新增售后、会话、聊天、反馈页面及入口。
- [x] 显式认证网络和按账号缓存/草稿，支持上下文关联、未读和刷新。
- [x] 仪器测试与窄屏/深色检查。

### Task 5: 联调交付
- [x] 控制服务启动、模拟器购买、管理员自动刷新。
- [x] 完成三端售后、聊天、反馈测试，检查原有流程。
- [x] 构建 1.1.4 APK，签名/版本/哈希，数据库查询说明和验证记录。
