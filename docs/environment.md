# 环境与版本记录

记录日期：2026-09-21（UTC）

| 项目 | 实际版本/状态 |
| --- | --- |
| 操作系统 | Linux 受管开发环境 |
| Node.js | 24.19.0 |
| npm | 11.9.0 |
| Git | 2.51.1 |
| Java | OpenJDK 17.0.20 |
| React | 19.1.1 |
| Vite | 6.1.0 |
| TypeScript | 网页 5.9.2；服务端 5.9.2 |
| Express | 5.1.0 |
| better-sqlite3 | 12.4.1 |
| SQLite 模式 | WAL、外键开启、busy timeout 5 秒 |
| Android Gradle Plugin | 8.9.1（配置值，未在本环境完成 SDK 构建） |
| Gradle Wrapper | 8.11.1（Wrapper 文件已提供） |
| Android compile/target SDK | 35（配置值，本环境未安装 Android SDK） |
| Android minSdk | 26 |

网页与服务端已在本环境实际构建。Android SDK/模拟器不可用，Gradle 发行包的 Java 下载也被当前网络策略阻止，因此 APK、模拟器和真机项保持“未执行”。
