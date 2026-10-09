# 源码上传与版本管理

## 首次纳入 Git 的基线

- 仓库：`https://github.com/zhangsanfeng25/huimeng`，私有仓库。
- 默认分支：`main`。
- Android：`versionName = "1.0.33"`，`versionCode = 23`。
- 根项目、管理网页和 API 的 `package.json` 版本均为 `1.0.0`；与 Android 分开管理。
- 初始化版本管理不改变应用版本，不表示已发布新 APK。

2026-10-09 初始化检查：`npm run lint` 与 `npm run build` 通过；`npm test` 的 40 项服务端测试通过，随后 `scripts/test-launcher-logs.ps1` 报错 `DEBUG must include errors with their preceding timestamp`，完整流程因此停止，后续检查未执行。此基线不能标记为全套测试通过；本次没有重新构建或验证 Android APK。

## 上传范围

提交 Android、网页、API 源码，启动脚本、文档、共享素材、Gradle wrapper 和根目录 `package-lock.json`。

`.gitignore` 排除数据库、备份、用户上传图片、本地 `.env`、SDK 路径、签名密钥、依赖缓存、构建产物和 `output/` 历史交付目录。这些文件仍保留在本机。`server/.env.example` 保留在源码中，供新环境配置。

源码仓库不包含现有业务数据。迁移到另一台电脑时，如需保留账户、订单和用户图片，应另外使用控制中心“备份数据库”生成完整备份，再通过“恢复数据库”导入；不要用 `git pull` 同步业务数据库。

新环境按 `README.md` 安装依赖、初始化数据库和启动服务。首次启动前可复制 `server/.env.example` 为 `server/.env`，检查监听地址及数据库路径。

## 日常提交和同步

在项目根目录执行。首次提交前设置本仓库的真实提交者姓名与邮箱；仓库地址及提交者信息不在本文中预设。

```powershell
git status --short
git diff
npm run lint
npm run build
npm test
git add .
git diff --cached --stat
git diff --cached
git commit -m "fix: 描述本次实际修改"
git push origin main
```

远程仓库地址确认并完成首次推送后，上述推送命令才可使用。建议只暂存本次修改涉及的路径；使用 `git add .` 时必须检查暂存内容。

开始下一次修改前，先确认工作区无未提交改动，再执行 `git pull --ff-only`。有本地改动时先提交或保存；出现分叉时检查提交记录并处理，不使用强制推送覆盖远程历史。

## 标记正式版本

用户已要求：后续上传或覆盖项目版本时，同步递增 Android 的 `versionCode` 和 `versionName`，重新构建并上传对应 APK，不删除以前的 APK。每次新版本另存安装包、创建独立 Release，在该版本附件所在页面写明实际改动。

从 `1.0.33` 开始作为用户实际使用的正式发行版管理，GitHub 设置 `prerelease = false`，新版本设为 Latest。用户已授权直接执行后续版本上传；APK 的 Debug/Release 构建类型、验证范围和已知问题继续如实写入说明。最新正式版入口为 [releases/latest](https://github.com/zhangsanfeng25/huimeng/releases/latest)。

同步更新 `android-app/BUILD_NOTES.md`、`docs/CHANGELOG.md`、`docs/releases/android-版本号.md` 和 README 中的 APK 信息。

修复通常递增补丁号，新增兼容功能递增次版本号，破坏性变化递增主版本号。例如下一次 Android 修复版可使用 `1.0.34` 和 `versionCode = 24`，这只是规则示例。

正式发布前完成相关测试、构建与实际流程检查，提交全部发布修改，再添加带说明的标签：

```powershell
git tag -a android-v1.0.34 -m "Android 1.0.34：填写已验证的更新说明"
git push origin android-v1.0.34
```

以上标签是示例，不能在版本仍为 `1.0.33` 时直接执行。网页和 API 可分别采用 `web-v版本号`、`api-v版本号` 标签；更新各自 `package.json` 后同步更新根目录锁文件。

APK 等交付物通过仓库的 Release 附件或指定交付渠道上传，不纳入源码提交。发布附件前检查 APK 实际版本、签名和运行结果，注明 Debug/Release 及验证范围。

每份发布说明列出相对上一版的新增、修复或调整，以及版本号、构建类型、适用环境、验证记录、未解决问题和 SHA-256。先完成并核对 APK 附件，再发布 Release；发布后核对附件名称、大小和下载入口。历史 APK、Release 和标签保留，不覆盖附件或强制移动旧标签。

当前安装包：GitHub [Android 1.0.33 Release](https://github.com/zhangsanfeng25/huimeng/releases/tag/android-v1.0.33)，改动说明见 [android-1.0.33.md](releases/android-1.0.33.md)。此包为 Debug 模拟器演示包。
