# 顺手取功能地图

本文梳理 v1.6.11 的全部用户功能。

## 站点

- 新增、编辑和删除站点。主要入口：`MainActivity`、`ParcelStore`。
- 设置站点名称、取件码格式和 8 种像素图标。主要入口：`MainActivity`、`StationIcons`。
- 一个 `x` 表示一位数字，多种格式每行一种。主要入口：`CodeRules`、`MainActivity`。
- 长按拖动或上下移动站点，设置实际取件路线。主要入口：`MainActivity`、`ParcelStore`。
- 有待取件的站点不能删除。主要入口：`MainActivity`、`ParcelStore`。

## 录入

- 首页按钮单击录入一条，长按进入批量录入。主要入口：`MainActivity`、`FeaturesActivity`。
- 格式无法自动匹配时手动选择站点。主要入口：`MainActivity`、`FeaturesActivity`。
- 接收其他应用分享的文字或图片。主要入口：`ShareActivity`、`ImportPayload`。
- 粘贴短信或多条号码，识别后统一核对。主要入口：`FeaturesActivity`、`BatchParser`。
- 打开主页时检查剪贴板，也可手动读取；只提示，不自动保存。主要入口：`ClipboardImport`、`MainActivity`。
- 一次选择最多 10 张图片，在本机识别文字。主要入口：`FeaturesActivity`、`ScreenshotRecognizer`、`ImportPayload`。
- 重复待取码会标记并跳过，修改原文后需重新识别。主要入口：`FeaturesActivity`、`ParcelStore`。

## 首页与排序

- 取件码按站点分组，站点按用户设定顺序显示。主要入口：`MainActivity`、`ParcelStore`。
- 同一站点内按每段数字升序排列并保留前导零。主要入口：`CodeRules`、`ParcelStore`。
- 空站点默认折叠，可手动展开。主要入口：`MainActivity`。
- 长按取件码可修改号码和所属站点。主要入口：`MainActivity`、`FeaturesActivity`。
- 点右侧方框或向左滑动可标记已取。主要入口：`MainActivity`、`SwipeCard`。
- 显示录入时间或放置天数，超过阈值后突出显示。主要入口：`MainActivity`、`ParcelAge`。

## 取件与历史

- 点击有待取件的站点进入大字取件模式。主要入口：`MainActivity`、`FeaturesActivity`。
- 支持逐条标记已取、跳过和保持屏幕常亮。主要入口：`FeaturesActivity`。
- 已取件记录保留 7 天，可恢复到待取清单。主要入口：`FeaturesActivity`、`ParcelStore`。
- 原站点已删除时可恢复到其他现有站点。主要入口：`FeaturesActivity`、`ParcelStore`。
- 可清空全部已取件历史；清空不可恢复，不影响待取清单。主要入口：`FeaturesActivity`、`ParcelStore`。

## 桌面小部件

- 4×4 标准 Android 小部件从顶部显示最多 6 个待取件码；缩小时减少可见行并提示剩余件数。每行依次显示取件码、站点和已取方框，顺序与首页一致。主要入口：`PickupWidget`。
- 可在桌面直接完成取件，记录进入 7 天历史；应用内数据变化后自动刷新。主要入口：`PickupWidget`、`ParcelStore`。
- 可从更多设置请求添加，不支持自动添加时显示普通 Android 小部件的手动步骤。主要入口：`FeaturesActivity`、`WidgetSupport`。

## 显示、帮助与数据

- 首页取件码字号提供小、标准、大、特大四档。主要入口：`FeaturesActivity`、`CodeAppearance`。
- 行距提供紧凑、标准、宽松三档。主要入口：`FeaturesActivity`、`CodeAppearance`。
- 放置天数阈值为 1–30 天，只在应用内显示，不发送通知。主要入口：`FeaturesActivity`、`ParcelStore`。
- 五页新版引导自动显示一次，并可从更多设置再次查看。主要入口：`MainActivity`、`FeaturesActivity`、`Onboarding`、`GuideContent`。
- 数据只保存在本机 SQLite，没有账号或云同步。主要入口：`ParcelStore`。
- 不申请短信、网络、相机或全盘存储权限；图片通过系统文件选择器读取。主要入口：`AndroidManifest.xml`、`FeaturesActivity`。

## 明确不包含

- 本地备份、每日通知和后台定时任务。
- OPPO 平台专有“全部卡片”；本版提供标准 Android 桌面小部件。
- 账号、云同步、短信读取、相机权限和网络上传。
