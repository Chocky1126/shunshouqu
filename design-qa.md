# v1.7.7 轻巧像素菜单对照

**Findings**

未发现剩余可操作的 P0/P1/P2 问题。按用户所选第一款样式实现奶油底、细棕色像素边框、浅色分隔线和指向入口的小尖角；按实际可见边缘修正左右留白。字体及图标偏轻、偏小的问题已在第二轮修复。

**比较目标与证据**

- Source visual truth：`../../outputs/candidates/v1.7.7/menu-design-selected.png`，1374×1146，来自最近一次三款方案中第一个实际显示结果；用户选择样式 1，并要求修正视觉上偏右。修订概念图 `menu-design-refinement.png` 亦为 1374×1146，但最终左右留白以用户要求和原生像素检测为准。
- Implementation screenshot：同目录 `Shunshouqu-v1.7.7-normal-right-menu.png`、`Shunshouqu-v1.7.7-normal-left-menu.png`；1080×2340，density 2.75，约 393×851dp；Android 原生界面，无 CSS 视口。
- State：首页待取码 001234，分别打开右上角与左下角弹出菜单，所有入口完整显示。
- Full-view comparison：在内置浏览器同屏查看所选设计板与原生主页菜单截图，另查看小屏、150% 字号和横屏原生完整截图；主页底部布局保持此前接受版本。
- Focused comparison：`../../outputs/candidates/v1.7.7/menu-comparison.html`，在内置浏览器同屏展示两个菜单的参考/原生局部。两轮均查看组合截图，第二轮字重与图标修改后重新捕获并重新加载对照页。原图按相同 170px 菜单宽度裁取显示，保持各自比例；参考是无设备密度的组件设计板，不能假定其像素等于 dp。原生菜单至少 48dp 点击高度，字号增大时行高增长。
- Visible alignment evidence：`visible-spacing-checks.json`；图标/文字实际深色像素而非控件矩形的左右留白，四种屏幕条件共 28 行，最大差 2 像素。旧 v1.7.6 实测部分项目差 10 像素，图标透明边距为来源。
- Responsive evidence：同目录 `Shunshouqu-v1.7.7-small-{right,left}-menu.png`（360×640dp）、`large-text-{right,left}-menu.png`（150% 字号）和 `landscape-{right,left}-menu.png`（640×360dp，实际 1920×1080 像素，density 3）。菜单内容无截断，弹出浮层可正常关闭。

**五项检查**

- Fonts and typography：沿用 Android 中文字体与 DEFAULT/BOLD，15sp 标签；字重与所选方案接近，150% 字号完整显示。所有菜单标签均为四个汉字。
- Spacing and layout rhythm：23dp 图标，12dp 图标文字间距，16dp 基础行内边距；按图标透明边距和字体尾部留白计算光学偏移，保持整组可见内容居中。基础行高 48dp，分隔线 1dp，边框 1.5dp，尖角空间 6dp，阴影 3dp。
- Colors and tokens：复用 PAPER、INK、LINE 与既有橙色/奶油主题，原生纯色背景保留可读性；生成图的光照纹理不作为 UI 资源。
- Image quality and asset fidelity：复用现有原生矢量图标，无新增栅格资产、占位图或品牌改动；图标缩放与像素边框清晰。
- Copy and content：右上角为“手动录入、已取快递、站点设置、更多设置”；左下角为“批量录入、截图识别、扫描短信”；引导及使用文档导航名称同步更新。

**比较记录**

1. 第一轮同屏对照发现 [P2] 原生菜单字重偏轻，图标略小；保持结果 blocked。
2. 恢复既有粗体、23dp 图标与 12dp 间距后重新构建，重新捕获 8 张原生菜单截图并查看第二轮同屏对照。字体/图标问题已修复，可见左右留白再次检测，最大差 2 像素，无剩余 P0/P1/P2。

**Implementation Checklist**

- 两个菜单样式、入口文字与可见居中已实现。
- 13 项菜单/使用引导回归测试通过；Debug 构建及 Lint 通过（0 错误，16 项既有警告）。
- 数据库 v5、包名和覆盖安装签名沿用，待打包验证 Release。

**Follow-up Polish**

ColorOS 16 真机截图与系统字体由用户覆盖安装后确认；模拟器证据不代表已完成 OPPO 实机验证。

final result: passed
