# 默认绘本示意封面

这 20 张 PNG 由 Android 现有 `BookCoverDrawable` 按默认绘本书名绘制，
通过 `DefaultCoverExportTest#testExportTwentyDefaultCovers` 在隔离 QA 包导出。
尺寸为 400 × 520，总体沿用 APP 已有封面样式；不是出版物原版封面照片。

服务端为历史 `/public/books/p0001.png` 至 `p0020.png` URL 提供兜底。
同路径的 `UPLOAD_DIR/books` 商家上传文件优先，不覆盖用户上传和业务数据库。
导出不包含账号资料、私人记录或用户上传图片。
