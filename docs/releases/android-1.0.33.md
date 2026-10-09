# 绘梦书城 Android 1.0.33

安装包：`huimeng-1.0.33.apk`。Android `versionName = 1.0.33`，`versionCode = 23`，包名 `com.huimeng.bookshop`。最低 Android 8.0（API 26）。

[下载 huimeng-1.0.33.apk](https://github.com/zhangsanfeng25/huimeng/releases/download/android-v1.0.33/huimeng-1.0.33.apk)（约 29 MB）。私有仓库需要登录有访问权限的 GitHub 账号。

此版作为用户实际使用的正式发行版发布，并设为 GitHub Latest。发行版标记调整不改变 APK 文件、内部版本、Debug 签名或原有验证范围。

## 本版本改动

- 新增独立搜索页和分类页：支持最近八条搜索历史、清空历史、综合/最新/价格排序、分页、空态与失败重试；分类选择可进入对应商品结果。
- 新增商品全部评价页：只展示公开且未删除的评价，支持分页与失败重试。
- 完善商家商品图片管理：可选择最多九张图片，逐张裁剪上传，预览、移除和设置封面；编辑文字保留历史媒体，页面重建时保留图片状态。
- 新增用户申请成为商家流程：提交店铺资料、查看审核进度、显示驳回原因并重新提交；网页管理端可审核申请。
- 审核结果实时同步：审核通过后原 App 会话无需重新登录即可切换商家身份；店铺停用后撤销原会话的商品管理权限。
- 修复搜索失败后旧分页结果残留，以及上传期间页面重建导致图片状态丢失的问题。

## 安装与使用

下载本页附件 `huimeng-1.0.33.apk`。此包为 Android Debug 签名的本地演示包，API 地址为 `http://10.0.2.2:3000/api/v1/`，用于同一电脑上的 Android 模拟器。

服务源码更新后，通过现有 Windows 控制中心关闭并重新打开服务，加载更新的网页和 API，并执行增量迁移 `007_merchant_application`。

实体手机需要配置电脑局域网地址，并使用 `-PapiBaseUrl=http://电脑局域网IP:3000/api/v1/` 重新构建；本附件未针对实体手机重新配置或验收。

## 验证范围与已知限制

- 本次发布（2026-10-09）复核了 APK 内部版本、Debug 构建标记和签名有效性；SHA-256 与原构建记录一致。附件沿用 2026-10-04 的现有安装包，没有重新构建 APK。
- 2026-10-04 的构建记录：`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过，lint 为 0 errors、479 warnings；API 34 模拟器 29 项回归测试通过。此项为历史记录，非本次重新设备验收。
- 2026-10-09 源码基线检查：网页及 API 构建、类型检查通过，40 项服务端测试通过；完整 `npm test` 随后在控制中心日志筛选检查处失败（`DEBUG must include errors with their preceding timestamp`），后续检查未执行。
- 最低 API 26 和实体手机未设备实测。支付、物流、短信为本地模拟。

## 附件校验

- 文件大小：30,465,162 字节。
- SHA-256：`0A37175B59F6C7E6B06B0F155E9FCA1F7E9495BCEB6862327B60FDBF8AE97C3D`。
- 签名证书：Android Debug；证书 SHA-256 为 `db6074b499e20dd34340f5df241a1ebc905733ba9b076f9909ab3cfadfdc371e`。

后续每个 APK 使用独立版本号、Release、改动说明及附件，不替换或删除本版本 APK。
