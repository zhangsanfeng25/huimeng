# 1.0.36 行为保持优化与发布计划

目标：优化现有 Android、网页和 API 内部实现，保持当前界面、操作流程、接口返回、权限即时查询和实时刷新时序；提交源码并发布独立 1.0.36 APK。

架构和技术：Java/XML Android、React/TypeScript/Vite、Express/better-sqlite3、PowerShell 控制中心，沿用全部现有技术和设计。需求依据：用户要求全力优化但保持现有界面及操作逻辑，并授权 Git 和 APK 版本更新。

约束：不改布局、CSS、协议、数据库结构或轮询间隔；不缓存权限或业务响应；不修改真实业务数据；保留已有未提交界面修复和历史 APK/Release。性能改善只报告可验证的重复计算或分配次数，不编造耗时比例。

1. Android：LocalCartStore.quantity 直接扫描 JSON；BookCoverDrawable 复用固定绘制对象及文字；CommentTime 和 ApiClient 复用格式器/资产源；删除 HomeFragment 无调用的分类弹窗；SearchActivity 统一私有状态读取 helper。保留 null、空值、重复 ID、默认数量及状态恢复语义。
2. API：http.ts、order-notifications.ts、order-lifecycle.ts、community.ts 复用固定 SQL 的惰性 Statement；shop.ts 将事务内重复 prepare 移出循环。保持 SQL、查询执行、事务、库存账本、通知、广播和错误顺序。
3. 网页：ManagementPage/CommunityPage 每次筛选只转一次关键词；DashboardPage 复用金额格式器并使 memo 依赖稳定；SyncStatus 使用稳定订阅函数。保持 JSX、选项顺序、筛选含义与刷新时序。
4. 控制中心：仅修复 test-launcher-logs.ps1 过期 fixture，日期改为相对当天，继续覆盖旧日志格式与时间戳，不改变生产日志规则。
5. 验证：npm lint/build/test；Android assembleDebug/lintDebug 与相关仪器测试；关键绘制结果比较；浏览器检查网页筛选、工作台和同步状态。对失败定位，明确区分环境中断与功能错误。
6. 独立代码审查：核对优化前后行为、潜在 Statement 初始化/数据库关闭差异、状态恢复、列表顺序和订阅清理。修复重要问题后发布。
7. 发布：versionName 1.0.36、versionCode 26；核对 APK 内部版本、签名、SHA-256，另存 output/android-1.0.36；更新 README、BUILD_NOTES、CHANGELOG 和独立发布说明。暂存审查排除业务数据库、上传图片和密钥；提交/推送 main 与新标签 android-v1.0.36；先上传附件，再发布正式 Latest Release，保留全部历史版本。

审查重点：当前 UI 修复不回退；商家审批后旧会话可立即切换；订单四节点消息和幂等；点赞跨页一致；原有三天日志过滤；声明真实测试范围。
