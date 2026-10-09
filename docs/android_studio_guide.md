# Android Studio 打开与运行说明

## 一、正确打开工程

1. 将下载的压缩包完整解压，路径尽量不要包含中文、空格或特殊符号，例如 `D:\projects\huimeng`。
2. 启动 Android Studio，选择 **Open**。
3. 只选择 `huimeng\android-app` 文件夹，不要选择 `huimeng`、`html` 或 `app` 子文件夹。
4. 等待 Gradle 同步完成。首次同步需要下载项目指定的 Gradle、Android Gradle Plugin 和 Android 依赖。
5. 如果提示缺少 SDK，请安装 Android SDK 35，并确保使用 JDK 17。

## 二、先启动服务端

Android App 的登录、商品、订单、社区等数据来自 Node.js 服务端。Windows 下只双击 `html/网站启动.bat`，在控制中心点击“打开服务”。首次运行会安装依赖、迁移数据库并写入演示数据；网站与 App 共用 `server/data/huimeng.db`。不要另外启动根目录服务。

服务端默认地址：`http://127.0.0.1:3000`  
后台网页默认地址：`http://127.0.0.1:3000`

## 三、运行 Android App

- Android 模拟器会使用 `http://10.0.2.2:3000/api/v1/` 访问电脑服务端，现有配置可直接使用。
- 真机测试时，需要将 `android-app/app/build.gradle.kts` 中的 `API_BASE_URL` 改为电脑局域网 IPv4，例如 `http://192.168.1.10:3000/api/v1/`。
- 真机联调还应把 `server/.env` 中的 `HOST` 改为 `0.0.0.0`，并允许防火墙放行 3000 端口。

演示账号：

| 身份 | 账号 | 密码 |
| --- | --- | --- |
| 买家 | `xingxing` | `demo12345` |
| 商家 | `merchant_a` | `demo12345` |
| 后台管理员 | `admin` | `huimeng123` |

## 四、生成 APK

Android Studio 菜单选择 **Build → Build APK(s)**，或在 `android-app` 目录运行：

```powershell
.\gradlew.bat assembleDebug
```

生成位置：

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
```

正式发布前应通过 **Build → Generate Signed App Bundle or APK** 配置自己的签名，并将服务端切换为 HTTPS 地址。

## 五、主要代码位置

- 页面逻辑：`android-app/app/src/main/java/com/huimeng/bookshop/ui`
- 网络与本地配置：`android-app/app/src/main/java/com/huimeng/bookshop/core`
- XML 页面：`android-app/app/src/main/res/layout`
- 图标与壁纸：`android-app/app/src/main/res/drawable*`
- 服务端接口：`server/src/modules`
- SQLite 迁移：`server/migrations`
- 管理后台：`html/src`
