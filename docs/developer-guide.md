# 开发与实现

## 构建与验证

需要 JDK 17、SDK Platform 35 / Build Tools 35.0.0；Gradle 8.11.1 Wrapper、AGP 8.9.2 已配置。Android Studio 打开本目录，或配置本机 `ANDROID_HOME` 后执行：

```sh
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease
```

Windows 使用 `gradlew.bat`。首次构建需要联网下载依赖，应用运行不需要网络。APK 位于 `app/build/outputs/apk/release/app-release.apk`。

交付 APK 使用既有调试证书签名，构建类型为不可调试 release，以支持覆盖旧交付包。其他电脑默认调试证书不同，重新构建的包可能不能覆盖已安装版本；源码不包含签名私钥，应用商店发布应另行配置正式签名。

```sh
./gradlew connectedDebugAndroidTest
```

设备测试会清空本应用的测试数据，仅应在专用模拟器执行，不能在存有真实取件码的手机上运行。测试涵盖迁移、排序、批量解析、短信过滤、图片识别、分享、编辑、历史、大字取件、菜单、显示设置、引导和小部件。v1.8.0 正式版验证证据及范围见 [交付说明](../DELIVERY.md)，v1.8.0 站点设置更新见 [更新说明](releases/v1.8.0.md)。

## 资源和实现

像素插画由 Image Gen 为本应用生成；图标采用 [Pixelarticons](https://github.com/halfmage/pixelarticons)（MIT），标题使用 [Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font) 字形子集（SIL OFL 1.1）。授权文本见 `licenses/`，随 APK 放入 `assets/licenses/`。取件码使用清晰的常规字体。

实现入口见 [功能地图](feature-map.md)。截图识别参考 [ML Kit 文档](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)，桌面添加预览参考 [Android AppWidgetManager](https://developer.android.com/reference/android/appwidget/AppWidgetManager#EXTRA_APPWIDGET_PREVIEW)。
