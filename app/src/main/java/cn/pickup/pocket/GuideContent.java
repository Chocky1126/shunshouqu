package cn.pickup.pocket;

/** Static copy for the version-two guide. Kept separate from rendering and preference state. */
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
        "设置站点名称、图标和取件码格式。",
        new String[] {"格式怎么写\n一个 x 代表一位数字，例如 x-x-xxx；多种格式每行一种。", "站点顺序\n可按实际取件路线调整。"},
        ""),
    new Page(
        R.drawable.pixel_locker,
        "多种方式都能录入",
        "支持单条、批量、分享、截图和近三天短信扫描。",
        new String[] {"单条 / 批量\n长按首页录入按钮进入批量录入。", "分享 / 截图\n多图一次最多选择 10 张。"},
        "短信扫描需授权；已有和已取的码会跳过，核对后才保存。"),
    new Page(
        R.drawable.pixel_gate,
        "到站后快速取件",
        "点击站点进入大字模式，也可以直接在首页完成。",
        new String[] {"向左滑动\n快速标记已取。", "一键取出\n首页或桌面小部件可将全部待取标记为已取。", "长按取件码\n修改号码或所属站点。"},
        ""),
    new Page(
        R.drawable.pixel_parcel,
        "取错也能恢复",
        "已取件记录保留 7 天，可恢复到待取清单。",
        new String[] {
          "清空要谨慎\n清空已取件会永久删除历史记录。",
          "桌面小部件\n添加前可预览，点右侧方框可逐件标记已取。",
          "以后再看\n更多设置 → 使用引导。"
        },
        "")
  };

  static final int COUNT = PAGES.length;

  private GuideContent() {}

  static Page page(int index) {
    if (index < 0 || index >= COUNT) throw new IllegalArgumentException("引导页编号无效");
    return PAGES[index];
  }
}
