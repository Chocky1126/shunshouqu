# v1.7.8 菜单层次与帮助区域验证

用户提供的 ColorOS 16 三张截图显示：两个菜单边框较站点卡片细，缺少可辨识的浮层阴影；使用引导按钮紧接桌面小部件，容易误认为该功能的专属帮助。

## 调整

- 菜单移除独立的 1.5dp 描边，直接使用 PixelSurface 默认粗细，与站点卡片一致；尖角使用同一描边参数。
- menuSurface 明确提供不透明矩形阴影轮廓，PopupWindow elevation 从 3dp 增至 10dp。保留原菜单宽度、内容居中和点击区域。
- 更多设置末尾新增“帮助”标题和一句整应用引导说明，按钮与之前接受的橙色样式一致。

## 当前证据

证据位于 `../../outputs/candidates/v1.7.8/`。

- `instrumentation.txt`：13 项菜单及引导测试通过。
- `menu-layout-checks.json`、`visible-spacing-checks.json`：标准、小屏（360×640dp）、150% 字号、横屏（640×360dp），共 28 行可见左右留白差 ≤ 2px。
- `Shunshouqu-v1.7.8-{normal,small,large-text,landscape}-{right,left}-menu.png`：原生菜单完整显示，阴影可辨识。
- `shadow-border-checks.json`：与 v1.7.7 同位置截图比较，旧菜单外背景为纯背景色，新菜单外有逐渐衰减的阴影。菜单和卡片共用描边参数，不同屏幕位置的完全不透明像素覆盖差为 1px，源于栅格化位置。
- `Shunshouqu-v1.7.8-*-settings-help.png`：独立帮助区域及其说明可见；对应 XML 和入口检查记录。
- Debug 构建和 Lint 通过（0 错误，16 项既有警告）。

截图核对未发现本次范围内的剩余显示问题。引导和菜单导航回归已通过；尚未在 OPPO 硬件上验证阴影。完成后交付沿用原签名的覆盖安装包，不代表 GitHub 已发布。
