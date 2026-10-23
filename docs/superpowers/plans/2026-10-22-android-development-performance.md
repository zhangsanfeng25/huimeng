# Android 工程打开与构建优化

范围：仅 android-app 的开发配置和可验证的重复计算；保留业务行为、数据、资源、接口和版本号。本次未要求上传或发布。

## 调查与基线
- Java 业务源码 105 个文件，合计约 0.44 MiB；资源约 24.59 MiB，离线 assets 约 14.49 MiB，app/build 约 188.04 MiB。
- IDE 2026-10-22 日志：打开 android-app，模型缓存命中，无需同步；第三方 Qoder 插件请求超时。不能由此推断全部启动延迟来自该插件。
- 同一用户级依赖缓存，离线 help：首次新 daemon 38.862s，复用 daemon 见后续 profile。空工作区缓存缺少 Foojay 插件，不能作为源码性能基线。

## 执行步骤
1. 去除当前未声明 Java toolchain 的工程中不需要的 Foojay resolver 插件；保留现有 JBR 21 daemon criteria 及已生成的下载地址，避免切换到不兼容的 IDE 内置 JBR 25。
2. 开启本地构建缓存、配置缓存；保持原有 2 GiB 堆与依赖版本。用两次 help 和实际 assemble/lint 检查缓存兼容性。配置缓存加速重复任务，不承诺加速 IDE 模型同步。
3. LocalDatabase.saveConfirmed/acknowledge 中相同 JSONObject 在同一同步代码段仅序列化一次，保持 SQL、事务、待同步操作和账号空间隔离不变。运行原有数据库/冲突/同步回归。
4. 验证 APK、测试 APK、lint，保存独立候选包和测量记录；说明 IDE 实际重启和完整 UI 回归的验证边界。

## 检查重点
- 冷启动与热 daemon 的时间分别报告，不能互相当作优化前后结果。
- 缺少依赖的 --offline 失败不能归咎于业务源码。
- JDK criteria 必须仍使用 21；不删除 .gradle，不修改全局 IDE 设置或插件。
- 保存快照不能覆盖待上传的本地修改；测试保留事务回滚与多账号隔离。

执行方式：用户已授权直接优化，由当前会话就地完成少量可回退修改，不另开工作树、不提交或发布。
