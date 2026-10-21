# 商品详情与背景设置验证（2026-10-17）

## 实施范围

按用户确认的 A 方案，保留现有紫色、深蓝文字、壁纸、原生 Java/XML 和交易流程。补全纯色背景及再次点击取消；详情去除试听按钮，加入出版资料、可展开简介、目录和作者圆角弹层、编辑推荐、真实读者评价与相似商品。

商家新增和编辑商品均有独立的“图书资料（选填）”分区，位于价格/库存之后、封面图片之前。文字作者、绘图作者、出版社、日期、两位作者简介、目录和编辑推荐随草稿保存并进入审核版本；网页查看、维护与审核共用这些资料。省略字段保留旧值，显式空字符串或空目录可以清空。日期严格校验，目录最多 80 项，每项最多 180 字。

原有 1.1.2 分类结果页及工作区已有改动保留。本次为本地交付，不创建 Git 提交、远程推送或 GitHub Release。

## 数据保护与实际填充

- 备份：`output/book-details-20261017/before-book-details-2026-10-17T14-49-15-612Z.db`。
- 更新清单：`output/book-details-20261017/fill-result-2026-10-17T14-49-15-612Z.json`，20 本已发布演示商品完成填充。
- 最终复核：`output/book-details-20261017/final-data-check.json`。与备份逐表比较，排除获准增加的资料字段、目标简介和同步所需版本/时间后，受保护表及字段完全相同；SQLite `quick_check = ok`。
- 每本为独立虚构简介、6 项目录及匹配题材的编辑推荐。作者、插画者、出版社与日期均为用户批准的虚构资料，界面有来源说明。真实评价未增加或修改。
- 服务端填充幂等，已有人工资料不覆盖。Android 一次性补齐仅定位书名和 ID 匹配、缺少资料且没有待同步商品写入的记录，不重新导入账户、订单或历史快照。
- 公开虚构资料分别位于 `server/assets/fictional-books.json` 和 `android-app/app/src/main/assets/book-details/fictional-v1.json`，两者 SHA-256 相同；原 `assets/bootstrap/` 忽略规则保持。数据库、用户上传和账号快照不进入源码。

## 构建、API 与网页

| 检查 | 结果及记录 |
|---|---|
| 根目录 lint/build | 通过：`output/book-details-delivery-lint.log`、`output/book-details-delivery-build.log` |
| 完整 npm test | 通过：`output/book-details-delivery-full-test.log`；65 项服务端测试、编译后启动、日志、实时同步、维护备份恢复、控制中心布局及响应性均完成 |
| 新图书业务测试 | 验证 20 本完整资料、严格字段验证、继承/清空、发布版本隔离、商家修改→送审→管理员批准→公开读取、同步快照和推荐过滤 |
| Android assemble/lint | `output/book-details-small-final-build.log`；Debug 与测试 APK 构建、lintDebug 通过，0 errors / 505 warnings，未清理既有非阻断提示 |
| 实际网页验收 | 复用本地已登录的 Edge `localhost:3000/products`。查看 P0001 新资料、展开编辑、核对预填内容并取消；未保存测试修改。控制台无新 error；截图 `output/book-details-20261017/web-book-editor.png` |

启动首次回归发现 seed 的第二种编译目录为 `dist/src/services`，资源定位未覆盖。修复后同时支持源码、主应用输出和 seed 输出；启动缓存纳入 `server/assets`，资料变更与重复启动保留业务修改均已测试。

## 设备验证及审查

- 设备：既有 `Medium_Phone`，Android API 34，保留安装数据并覆盖安装；没有清空应用或模拟器。
- 原有 51 项回归通过：`output/book-details-preservation-regression.log`。覆盖首页、分类、轮播、登录提示、个人资料与点赞、消息、地址、商家、图片、购物车、离线业务及同步。
- 专项 12 项通过：`output/book-details-merchant-acceptance.log`。包括实际新增/保存/提交审核按钮携带图书资料、表单草稿恢复、背景选择/取消、目录/作者弹层与重建、推荐及离线保护。
- 最终 APK 17 项通过：`output/book-details-final-17-tests.log`。覆盖全部图书/商家专项、离线升级、商家分区可见性，以及受影响的图片重建、返回编辑刷新、直接结算和既有商品预览。
- 独立只读审查发现头图区缺少小屏纵向适配。对应测试先失败（`book-details-responsive-red.log`），修复后通过（`book-details-eight-green.log`）；封面和信息在窄屏或大字体下顺序排列。
- 真实 P0001 预览先因测试绕过正常启动准备而失败；改用 App 正常的 `LocalRepository.prepare` 后读取既有数据通过，记录 `output/book-details-real-record-green.log`。没有为通过测试重置本地数据。
- 深色详情/长文弹层及背景取消两项通过：`output/book-details-dark-check.log`，测试前后恢复原外观设置。
- 小屏使用 840×1800、420dpi（320dp 宽）、字号 1.3。截图发现购物车文字换行截断，补充完整文字断言并修正购买按钮内边距。断言失败证据见 `output/book-details-small-button-red.log` 及设备 AndroidRuntime；断言移至测试线程后避免让断言中断 App 主线程。
- 修复后最终小屏真实商品检查通过：`output/book-details-small-button-green.log`，包含两枚购买按钮单行及文字宽度断言，封面保持竖版比例。最终深色弹层复查通过：`output/book-details-final-dark-preview.log`；这两项与 17 项专项分别执行。
- 原尺寸 1080×2400、字号 1.0 已记录并恢复，未改变密度；记录位于 `output/book-details-20261017/display-before-small.txt` 与 `display-after-small.txt`。

## 截图与限制

截图均来自运行中的 Android 页面或实际管理网页。`book-live-*` 为既有 P0001 和当前绿野花园壁纸；`book-small-*` 为同一商品的小屏/放大字体；`book-details-theme*` 是深色长文测试资料；`merchant-form-test.png` 使用隔离商家测试资料。

本地截图总览：`output/book-details-20261017/preview.html`。最终 APK：`output/android-1.1.3/final/huimeng-1.1.3.apk`，版本、签名及 SHA-256 记录均位于同目录；详细校验值见 `docs/releases/android-1.1.3.md`。本轮较早候选 APK 保留，交付以 `final/` 文件为准。

目录/作者弹层采用顶部 28dp 圆角、最多约 85% 屏高，支持关闭、滑动和状态恢复。相似推荐最多 4 本，优先同分类及相同年龄范围，排除本书、重复、下架和无库存商品。年龄范围按现有字符串相等匹配，不声称计算年龄区间距离。

未实测 API 26 或实体手机。网页既有部分商品原图不可访问，保留原缺图提示及 App 现有书封占位，未擅自替换原素材。APK 为 Debug 模拟器演示包，API 地址 `http://10.0.2.2:3000/api/v1/`；实体手机需要对应可访问的服务配置。
