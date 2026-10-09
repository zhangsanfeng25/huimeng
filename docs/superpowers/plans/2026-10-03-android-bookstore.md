# Android Bookstore Implementation Plan

> 执行方式：当前会话直接实施；用户已确认组合预览并要求继续，不启动子代理、不重复请求同一设计批准。

**Goal:** 将确认的 A3 首页落地，并修复关键购买流程及核查页面入口。
**Architecture:** 复用 Java/XML 组件与共享 Express API；新增筛选逻辑和交易接口，保持旧网站 API 兼容。使用隔离临时数据库进行接口测试。
**Tech Stack:** Java 17, Android Views/Material 3, Express/TypeScript, SQLite.
**Spec:** ../specs/2026-10-03-android-bookstore-design.md

## Global Constraints
- 不更换技术栈、不覆盖原素材、不写业务数据库、不安装额外依赖、不自动公开发布。
- 修改范围和失败检查写入 output/android-bookstore-20261003/验收报告.md。
- 本目录不是 Git 仓库，因此原地保存；不创建工作树或提交。

## Review Focus
- 筛选与排序组合不能忽略关键词；无数据和服务失败应可恢复。
- 加购不能突破库存；返回购物车应读取服务端权威数据。
- 修改地址不能复制地址；他人的地址不可修改或删除。
- 直接购买不能清除其他已选购物车商品。
- 取消和过期关闭不能重复释放库存；多店结算组需要整体取消。

## Tasks
- [x] 1. 先编写目录筛选/价格排序/购物车集成测试并观察失败；实现 shop.ts 与目录筛选辅助；验证通过。
- [x] 2. 先编写地址/取消订单权限和库存测试并观察失败；实现对应 API 与 Android 交易页面；验证通过。
- [x] 3. 实现确认后的 fragment_home.xml、HomeFragment、ProductAdapter；保留海报并接通入口；增加首页 UI 行为测试。
- [x] 4. 修正商品评价、逐件评价与无实现入口，核查其余页面；保存明确的已完成/后续清单。另修复无身份验证的密码找回。
- [x] 5. 构建服务端/Android，执行项目测试与 lint；实际检查截图并交付 APK 与报告。
