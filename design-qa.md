# v1.7.5 首页底部操作区对照

**Findings**

未发现需要修复的 P0/P1/P2 问题。橙色录入区与箭头共用像素边框，以细线分隔；一键取出保持浅色次按钮。两个按钮同高、主次比例与选定方案一致。

**比较目标与证据**

- Source visual truth：`../../outputs/candidates/v1.7.5/Shunshouqu-v1.7.5-design-reference.png`，用户选择的第一个生成结果，1479×1064 像素，设计为 390×280 的局部操作区。
- Implementation screenshot：`../../outputs/candidates/v1.7.5/Shunshouqu-v1.7.5-normal-home.png`，1080×2340 像素，Android 模拟器原生截图，density 2.75，对应约 393×851dp。Android 界面无 CSS 视口。
- State：首页有一个待取码 `001234`，菜单关闭，按钮可用；只改底部，不改变列表布局。
- Full-view comparison：同时查看设计图与原生主页截图，检查按钮分组、主次层次、列表与固定底部的关系。设计图仅展示局部，原生页面的中间留白随列表与可用高度变化，未据此重排主页。
- Focused region comparison：`../../outputs/candidates/v1.7.5/footer-comparison.html`。在内置浏览器将两个原始图片按 390px 宽度、原宽高比显示，裁取按钮区域并对齐顶部，已查看同屏对照截图。仅用于证据展示，未修改原图。
- Density normalization：参考图显示比例 390/1479；原生图显示比例 390/1080。对照页面使用 CSS 裁取局部，与产品实现无关。
- Responsive evidence：同目录 `small-home.png`、`large-text-home.png`、`landscape-home.png`（均带 `Shunshouqu-v1.7.5-` 前缀）及相应 entry-menu 图片；分别为 360×640dp、150% 字号、640×360dp。所有操作区均在屏幕内，文字无换行裁切。

**五项检查**

- Fonts and typography：沿用 Android 中文字体与粗体按钮，常规字号为 16sp/15sp，横屏紧凑为 14sp/13sp；主操作白字、次操作深色字。大字号仍完整显示。
- Spacing and layout rhythm：边距沿用主页 26dp；两操作区宽度约 5:3，间距 10dp；通常高度 56dp，横屏 50dp；分隔线 1×28dp。录入文字与箭头没有独立外框。
- Colors and tokens：沿用 PRIMARY、TINT、INK 和奶油底色。源图的生成光照纹理不作为 UI 资源，应用继续使用既定纯色主题。
- Image and asset fidelity：复用现有站点像素图标与上箭头，没有替换品牌资源；选定设计图仅作为对照参考。
- Copy and content：保留“录入取件码”“一键取出”“左滑标记已取 · 长按编辑”，以及三种录入菜单项目。

**比较记录**

首次原生界面对照无 P0/P1/P2 差异；未因视觉检查进行额外设计迭代。

**Implementation Checklist**

- 已完成单一录入外框、内部分隔线、按钮同高及主次比例。
- 5 项原有设备测试通过：单条录入、菜单入口、页面导航和全部取出。
- Lint 0 个错误，调试构建通过。
- 本次设备检查使用专用 Android 15 模拟器，未声称已在 ColorOS 实机复验。

final result: passed
