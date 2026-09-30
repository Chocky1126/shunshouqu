# v1.7.10 布局验证

批量录入页的保存按钮使用与 v1.7.9 已取件页清空按钮相同的 `fixedPrimary` 底部布局。按钮独立于 ScrollView，边距、颜色和像素边框统一。首页移除底部操作提示文字；已有操作保持不变。

证据目录：`../../outputs/candidates/v1.7.10/`。

- `instrumentation.txt`：9 项原生设备回归通过，包括 20 条候选列表滚动到最末、取消一个候选后仅保存其余 19 个；原文修改校验、重复过滤、分享与多图识别保存；已有票据布局与首页录入菜单回归。
- `batch-layout-checks.json`：393×851dp 标准、360×640dp 小屏、150% 系统字号、640×360dp 横屏，保存按钮滚动前后坐标一致，最末候选可取消勾选。无识别结果时不显示保存按钮；首页提示文字不存在。
- `Shunshouqu-v1.7.10-{normal,small,large-text,landscape}-batch.png` 与对应 `-scrolled.png`、XML：专用 Android 15 模拟器真实页面。已检查按钮文字与边框完整、最末候选可见，只有中间内容滚动。
- `Shunshouqu-v1.7.10-home.png`：移除提示后的首页，两个原有底部操作按钮保留。
- `validation-build.txt`：构建通过，Release Lint 0 错误、16 项既有警告。

本次核对未发现剩余显示问题。未连接 OPPO 硬件，ColorOS 16 视觉效果仍待覆盖安装确认。正式发布验证与安装说明见 `DELIVERY.md`。
