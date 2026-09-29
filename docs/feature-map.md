# 顺手取 v1.7.0 功能地图

| 功能 | 用户入口 | 实现入口 |
| --- | --- | --- |
| 单条自动归站录入 | 首页录入按钮 | MainActivity、CodeRules、ParcelStore |
| 手动指定站点录入 | 右上角 → 手动录入 | FeaturesActivity |
| 批量文本、截图、短信 | 录入按钮旁上箭头 | FeaturesActivity、BatchParser、ScreenshotRecognizer、SmsInboxReader、SmsCodeExtractor |
| 其他应用文字/图片分享 | 分享目标顺手取 | ShareActivity、ImportPayload |
| 站点增删、格式、图标、路线排序 | 右上角 → 站点设置 | MainActivity、StationIcons、ParcelStore |
| 分组、分段数字排序、前导零 | 首页清单 | CodeRules、ParcelStore |
| 单件取件、长按编辑 | 左滑/方框、长按取件码 | MainActivity、SwipeCard、FeaturesActivity |
| 全部取件 | 首页/小部件一键取出 | MainActivity、PickupWidget、ParcelStore |
| 大字取件、逐条完成/跳过、常亮 | 站点标题 | FeaturesActivity |
| 七天历史、恢复、清空 | 右上角 → 已取件 | FeaturesActivity、ParcelStore |
| 字号、行距、放置天数 | 右上角 → 更多设置 | FeaturesActivity、CodeAppearance、ParcelAge |
| 小部件及系统添加预览 | 更多设置 → 添加到桌面 | PickupWidget、WidgetSupport |
| 首次/升级五页引导、再次查看 | 首次启动/更多设置 | Onboarding、GuideContent、FeaturesActivity |
| 暖色配色及像素样式 | 所有应用页面 | AppStyle、colors.xml、styles.xml |

小部件从顶部显示最多 6 条，缩小时减少显示行，取件后自动补位。短信扫描由用户点击并授权触发，只读近 72 小时、最多 500 条收件箱；过滤待取及最近 7 天已取码，核对保存前不会写入。

数据库为 SQLite v5。数据仅在本机保存，没有账号、云同步、备份、定时通知、剪贴板自动识别、剪贴板主动读取或后台扫描。应用不申请网络、相机或全盘存储权限。
