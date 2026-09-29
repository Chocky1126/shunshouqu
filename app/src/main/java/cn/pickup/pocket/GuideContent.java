package cn.pickup.pocket;

/** Concise v1.7 guide, using the final entry points and data retention rules. */
final class GuideContent {
  static final class Page {
    final int image;
    final String title;
    final String lead;
    final String[] items;
    final String note;

    Page(int image, String title, String lead, String[] items, String note) {
      this.image = image;
      this.title = title;
      this.lead = lead;
      this.items = items.clone();
      this.note = note;
    }
  }

  private static final Page[] PAGES = {
    new Page(
        R.drawable.pixel_parcel,
        "取件码，只留在手机里",
        "顺手取离线运行，没有账号，也不会上传取件码。",
        new String[] {"自动归站\n按预设格式归入对应站点。", "自动排序\n按每段数字升序排列，并保留前导零。"},
        ""),
    new Page(
        R.drawable.pixel_shop,
        "先设置常用站点",
        "右上角菜单 → 站点设置。",
        new String[] {"格式怎么写\n一个 x 代表一位数字，例如 x-x-xxx；多种格式每行一种。", "按路线排列\n选择名称和图标，长按拖动或用箭头调整站点顺序。"},
        ""),
    new Page(
        R.drawable.pixel_locker,
        "多种方式都能录入",
        "点录入按钮添加一条；点旁边的上箭头选择批量、截图或短信。",
        new String[] {"核对后保存\n批量和识别结果都由你勾选确认。", "手动录入\n右上角菜单可指定站点，不受预设格式限制。", "分享录入\n短信文字或图片可以分享给顺手取。"},
        "短信需授权，只扫描近 72 小时，跳过已有和最近 7 天已取的码；多图最多 10 张。"),
    new Page(
        R.drawable.pixel_gate,
        "到站后快速取件",
        "左滑取件码或点右侧方框，就能标记已取。",
        new String[] {"一键取出\n首页或小部件可取出全部待取件，不限当前站点。", "大字取件\n点站点标题逐件查看，可保持屏幕常亮。", "长按取件码\n修改号码或所属站点。"},
        ""),
    new Page(
        R.drawable.pixel_parcel,
        "取错也能恢复",
        "已取件记录保留 7 天，可恢复到待取清单。",
        new String[] {
          "恢复记录\n右上角菜单 → 已取件，选择恢复到待取。",
          "桌面小部件\n更多设置中添加；最多显示 6 条，缩小时提示剩余数量。",
          "显示与帮助\n更多设置可调字号、行距、放置天数，也能重看引导。"
        },
        "清空已取件会永久删除历史记录。")
  };

  static final int COUNT = PAGES.length;

  private GuideContent() {}

  static Page page(int index) {
    if (index < 0 || index >= COUNT) throw new IllegalArgumentException("引导页编号无效");
    return PAGES[index];
  }
}
