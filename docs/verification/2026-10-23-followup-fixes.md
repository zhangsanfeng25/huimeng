# 2026-10-23 三端回归问题修复

## 本轮完成范围

1. 为历史 `/public/books/p0001.png` 至 `p0020.png` 补齐 20 张 400×520 PNG 默认封面，总计 413,232 字节。图片由现有 Android `BookCoverDrawable` 按默认书名导出，沿用原有示意封面样式，并非出版物原版照片。服务端先读取 `UPLOAD_DIR/books`，不存在时读取内置资源；不覆盖商家上传，也不更改业务数据库。
2. `html/src/App.tsx` 为反馈、售后两个 `ServicePage` 使用不同 React key。跨模块切换重建页面状态，清空搜索、状态筛选并回到第一页；在同模块打开、关闭详情时保留筛选。
3. 售后详情接口返回 `can_apply_again`：只有已结束的售后、已付款订单、无进行中申请、尚有剩余可售后数量及金额时才为 true。Android 据此显示再次申请入口；全额退款后隐藏。直接进入无剩余额度的申请页时显示说明，不再展示空提交表单。提交时原有服务端金额、数量与权限校验仍执行。

## 本次验证

- 修改前复现：服务端新测试因封面资源缺失、再次申请资格缺失而失败；Android 的全额退款隐藏入口和无额度表单检查均失败；浏览器复现跨模块保留搜索词和处理状态。
- 修改后完整根目录 `npm test` 通过：服务端 **81/81**，另包含启动准备、日志、实时 SSE、备份恢复导出、控制中心布局及响应性回归。
- HTTP 实际服务验证 20 个默认封面 URL 返回 200、PNG 类型及字节内容正确；商家同路径上传优先；未知封面仍返回 404。以上已纳入 `server/scripts/test-realtime.mjs`。
- 浏览器实际检查 20 张商品封面全部解码为 400×520；反馈→售后、售后→反馈均清空搜索及状态，列表恢复；反馈筛选后查看、关闭详情保留原筛选。
- Android 定向 **7/7**：部分商品金额提交、演示找回密码、部分/全额退款状态、全额退款隐藏入口、部分退款保留入口、无额度禁止空表单、原封面像素基线。
- Android 真实联网 **1/1**：从隔离服务读取此前手机提交的订单、平台反馈、商家回复和已完成退款；截图确认整单全额退款详情底部没有再次申请按钮。上述 8 项为本轮重跑结果，不将上轮 112 项当作本次全量重跑。
- 根目录 `npm run build`、`npm run lint` 通过；默认 Android `assembleDebug`、`lintDebug --offline` 通过。
- 独立只读代码审查未发现本轮修复的阻断问题；该审查未另外重跑测试。
- 实际业务数据库测试前后 SHA-256 均为 `574F53D9D31474F89946163D4F10A15C03F8E9B39A942F07D441A6D051BFF237`。联网写入只发生在隔离 QA 数据库。

## 本地 APK

- 路径：`D:\bishe\huimeng\output\followup-fixes-20261023\final\huimeng-1.1.5-followup-local.apk`
- 正常包名 `com.huimeng.bookshop`；versionName `1.1.5`、versionCode `32`；Debug，minSdk 26、targetSdk 35。
- SHA-256：`E14E65B4F34F9EE7A87C0D19394929DB06304BF054A92B02DE6CF3BB06CB934E`。
- APK 签名校验通过；已恢复默认服务地址 `http://10.0.2.2:3000/api/v1/`。本地修复包与先前包版本号相同，本轮未递增发布版本，未上传 GitHub，历史安装包未覆盖。
- 网站和服务端也有改动，需与本轮源码一起使用；只安装 APK 而仍运行旧服务端，无法获得新的再次申请资格字段与网站修复。

## 证据与限制

证据目录：`D:\bishe\huimeng\output\followup-fixes-20261023`。

- `server-red.log`、`android-red.log`、`web-filter-red.png`：修复前复现。
- `full-tests.log`、`server-green.log`、`android-green.log`、`android-live-outcome.log`：修复后结果。
- `web-filter-green.json`、`web-filter-green.png`、`web-covers-green.json`、`web-cover-preview.png`：网页结果。
- `full-refund-no-repeat.png`、`partial-refund-repeat.png`、`no-refundable-items.png`、`live-full-refund-completed.png`：模拟器结果。
- `apk-metadata.txt`、`apk-signature.txt`、`apk-sha256.json`：APK 核验。
- 初次 Android 绿灯批次有一个封面测试方法名填写错误，6 项业务测试通过后因不存在的方法失败；纠正方法名后同批次 7 项全部通过。原始尝试日志保留为 `android-first-green-attempt.log`，没有当作应用缺陷或成功批次。

本轮没有接入真实支付退款、没有测试实体手机，未处理范围外的 Android 静态警告；上轮网页原生退款确认弹窗的自动化限制也不在本轮修复范围内。
