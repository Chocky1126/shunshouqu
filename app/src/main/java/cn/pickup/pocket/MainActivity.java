package cn.pickup.pocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
  private static final int BG = AppStyle.BG,
      INK = AppStyle.INK,
      MUTED = AppStyle.MUTED,
      BLUE = AppStyle.ACCENT;
  private static final int[] COLORS = {BLUE, BLUE, BLUE};
  private static final int[] TINTS = {AppStyle.TINT, AppStyle.TINT, AppStyle.TINT};
  private ParcelStore store;
  private LinearLayout groups;
  private TextView total, overviewNote;
  private Button completeAll;
  private ScrollView scroll;
  private List<Station> stations;
  private AlertDialog activeDialog;
  private android.widget.PopupWindow homePopup;
  private String activeDialogKind;
  private EditText draftCode;
  private EditText draftStationName, draftStationFormats;
  private String draftStationIcon;
  private int editingStationId = -1;
  private final Set<Integer> expandedEmpty = new HashSet<>();
  private AlertDialog deleteConfirmation;
  private final Handler handler = new Handler(Looper.getMainLooper());
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LegacyCleanup.run(this);
    store = new ParcelStore(this);
    buildScreen();
    render();
    if (state != null) {
      if ("add".equals(state.getString("dialog"))) showAdd(state.getString("draftCode", ""));
      else if ("settings".equals(state.getString("dialog"))) showSettings();
      else if ("stationEditor".equals(state.getString("dialog"))) {
        showStationEditor(
            state.getInt("stationId", -1),
            state.getString("draftName"),
            state.getString("draftFormats"),
            state.getString("draftIcon"));
      }
    }
    if (state == null && Onboarding.shouldShow(this)) {
      startActivity(new Intent(this, FeaturesActivity.class).putExtra("mode", "guide"));
    }
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    v.setIncludeFontPadding(false);
    return v;
  }

  private Button button(String value, int color, int background) {
    return AppStyle.button(this, value, color, background);
  }

  private void space(LinearLayout parent, int height) {
    View v = new View(this);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  private void buildScreen() {
    boolean compact = getResources().getConfiguration().screenHeightDp < 440;
    LinearLayout root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      getWindow().getInsetsController().setSystemBarsAppearance(
          android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
          android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
      root.setOnApplyWindowInsetsListener((view, insets) -> {
        android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
        view.setPadding(i.left, i.top, i.right, i.bottom); return insets;
      });
    }
    LinearLayout header = row();
    header.setPadding(dp(26), dp(compact ? 2 : 16), dp(26), dp(compact ? 2 : 8));
    android.widget.ImageView logo = new android.widget.ImageView(this);
    logo.setImageResource(R.drawable.pixel_parcel);
    logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    header.addView(logo, new LinearLayout.LayoutParams(dp(compact ? 36 : 48), dp(compact ? 36 : 48)));
    TextView title = text("顺手取", compact ? 24 : 32, INK, true);
    title.setTypeface(AppStyle.pixel(this));
    title.getPaint().setFakeBoldText(true);
    title.setPadding(dp(10), 0, 0, 0);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    Button menu = button("", INK, AppStyle.TINT);
    menu.setId(R.id.home_menu);
    menu.setContentDescription("打开更多功能");
    AppStyle.icon(menu, R.drawable.ic_more, INK, false);
    menu.setPadding(dp(12),0,dp(12),0);
    menu.setOnClickListener(v -> showMenu(menu));
    header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);
    LinearLayout summary = row();
    summary.setPadding(dp(30),0,dp(30),dp(compact ? 2 : 14));
    total = text("0", compact ? 12 : 15, MUTED, false);
    total.setId(R.id.total_count);
    summary.addView(total);
    overviewNote = text(" 件待取",compact ? 12 : 15,MUTED,false);
    summary.addView(overviewNote, new LinearLayout.LayoutParams(0, -2, 1));
    completeAll = button("一键取出", INK, AppStyle.TINT);
    completeAll.setId(R.id.home_complete_all);
    completeAll.setTextSize(13);
    completeAll.setMinimumHeight(dp(36));
    completeAll.setOnClickListener(v -> {
      try {
        int count = store.completeAll();
        if (count > 0) Toast.makeText(this, "已取出 " + count + " 件，可在已取件中恢复", Toast.LENGTH_SHORT).show();
        render();
      } catch (RuntimeException error) {
        showError("一键取出失败，待取清单未更改。", null);
      }
    });
    summary.addView(completeAll);
    root.addView(summary);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout content = column();
    content.setPadding(dp(26), dp(8), dp(26), dp(8));
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    groups = column(); content.addView(groups);
    LinearLayout footer = column();
    footer.setPadding(dp(26),dp(compact ? 0 : 4),dp(26),dp(compact ? 4 : 12));
    TextView hint = text("左滑标记已取 · 长按编辑",12,MUTED,false);
    hint.setGravity(Gravity.CENTER); hint.setPadding(0,dp(6),0,dp(12));
    if (!compact) footer.addView(hint);
    Button add = button("录入取件码",Color.WHITE,compact ? BLUE : 0xFFD76824);
    add.setId(R.id.add_code); add.setTextSize(compact ? 16 : 20);

    add.setOnClickListener(v -> showAdd());
    add.setOnLongClickListener(v -> { openFeature("batch", -1, -1); return true; });
    add.setContentDescription("录入取件码，长按批量录入");
    add.setTooltipText("长按批量录入");
    add.setMinimumHeight(dp(compact ? 50 : 60));
    footer.addView(add,new LinearLayout.LayoutParams(-1,-2));
    root.addView(footer);
    AppStyle.enter(content);
  }

  private void showMenu(View anchor) {
    LinearLayout panel = column();
    panel.setPadding(dp(8),dp(8),dp(8),dp(8));
    ScrollView menuScroll = new ScrollView(this);
    menuScroll.setVerticalScrollBarEnabled(false);
    menuScroll.addView(panel);
    int menuHeight = Math.min(dp(316), getResources().getDisplayMetrics().heightPixels - dp(80));
    android.widget.PopupWindow popup = new android.widget.PopupWindow(menuScroll,
        Math.min(dp(238),getResources().getDisplayMetrics().widthPixels-dp(36)), menuHeight,true);
    popup.setBackgroundDrawable(AppStyle.surface(this,AppStyle.PAPER,6,true));
    homePopup = popup;
    popup.setOutsideTouchable(true); popup.setElevation(dp(3));
    String[] labels = {"粘贴录入","截图识别","扫描近三天短信","已取件","站点设置","更多设置"};
    int[] icons = {R.drawable.ic_content_paste,R.drawable.ic_image_search,R.drawable.ic_sms,R.drawable.ic_history,R.drawable.ic_settings,R.drawable.ic_tune};
    for (int i=0;i<labels.length;i++) {
      final int action=i;
      Button item=button(labels[i],INK,Color.TRANSPARENT);
      item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
      AppStyle.icon(item,icons[i],INK,false);
      if (i==4) item.setId(R.id.station_settings);
      item.setOnClickListener(v -> {
        popup.dismiss();
        if (action==0) pasteFromClipboard();
        else if (action==1) openFeature("ocr",-1,-1);
        else if (action==2) openFeature("sms",-1,-1);
        else if (action==3) openFeature("history",-1,-1);
        else if (action==4) showSettings();
        else openFeature("more",-1,-1);
      });
      panel.addView(item,new LinearLayout.LayoutParams(-1,-2));
    }
    popup.showAsDropDown(anchor,0,dp(6),Gravity.END);
  }

  private void render() {
    try {
      List<Parcel> parcels = store.all();
      stations = store.stations();
      total.setText(String.valueOf(parcels.size()));
      total.setContentDescription(parcels.size() + " 件待取快递");
      overviewNote.setText(" 件待取 · " + stations.size() + " 个站点");
      completeAll.setVisibility(parcels.isEmpty() ? View.GONE : View.VISIBLE);
      int previousY = scroll.getScrollY();
      groups.removeAllViews();
      for (int index = 0; index < stations.size(); index++) {
        Station station = stations.get(index);
        ArrayList<Parcel> items = new ArrayList<>();
        for (Parcel parcel : parcels) if (parcel.stationId == station.id) items.add(parcel);
        items.sort((a, b) -> CodeRules.compareCodes(a.code, b.code));
        addStation(station, index, items);
      }
      if (stations.isEmpty()) {
        overviewNote.setText(" 件待取 · 先添加站点");
        TextView empty = text("还没有取件站点", 18, INK, true);
        empty.setPadding(dp(16), dp(24), dp(16), dp(12));
        groups.addView(empty);
        TextView help = text("添加常去的站点和取件码格式，之后就能自动归类。", 14, MUTED, false);
        help.setPadding(dp(16), 0, dp(16), dp(20));
        groups.addView(help);
        Button create = button("新增站点", BLUE, TINTS[0]);
        create.setOnClickListener(v -> showStationEditor(-1, null, null, null));
        groups.addView(create);
      }
      scroll.post(() -> scroll.scrollTo(0, previousY));
    } catch (RuntimeException error) {
      showError("无法读取本地记录，请重试。", () -> render());
    }
  }

  private void addStation(Station station, int index, List<Parcel> items) {
    int color = COLORS[Math.floorMod(station.id, COLORS.length)];
    int tint = TINTS[Math.floorMod(station.id, TINTS.length)];
    LinearLayout block = column();
    block.setPadding(dp(2), dp(2), dp(2), dp(6));
    block.setBackground(AppStyle.surface(this, AppStyle.PAPER, 6, true));
    LinearLayout heading = row();
    heading.setPadding(dp(12),dp(5),dp(10),dp(5));
    android.widget.ImageView stationIcon = new android.widget.ImageView(this);
    int iconIndex = StationIcons.indexOf(station.iconKey);
    stationIcon.setImageResource(StationIcons.RESOURCES[iconIndex]);
    stationIcon.setContentDescription("站点图标 " + StationIcons.LABELS[iconIndex]);
    heading.addView(stationIcon,new LinearLayout.LayoutParams(dp(36),dp(36)));
    TextView name = text(station.name,18,INK,true);
    name.setPadding(dp(10),0,dp(6),0);
    heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    heading.addView(text(items.size()+" 件",13,MUTED,false));
    android.widget.ImageView chevron=new android.widget.ImageView(this);
    chevron.setImageResource(R.drawable.ic_chevron); chevron.setPadding(dp(8),0,0,0);
    heading.addView(chevron,new LinearLayout.LayoutParams(dp(28),dp(24)));
    block.addView(heading);
    heading.setMinimumHeight(dp(50));
    heading.setBackground(new android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(0x1833291E),
        AppStyle.surface(this,AppStyle.TINT,6,false),
        AppStyle.surface(this,Color.WHITE,6,false)));
    heading.setContentDescription(
        items.isEmpty() ? "展开空站点 " + station.name : "开始取件 " + station.name);
    heading.setOnClickListener(
        v -> {
          if (items.isEmpty()) {
            if (!expandedEmpty.add(station.id)) expandedEmpty.remove(station.id);
            render();
          } else openFeature("pickup", station.id, -1);
        });
    if (items.isEmpty() && !expandedEmpty.contains(station.id)) {
      groups.addView(block, new LinearLayout.LayoutParams(-1, -2));
      space(groups, 18);
      return;
    }
    space(block, 1);
    if (items.isEmpty()) {
      TextView empty = text("这里还没有待取件", 13, MUTED, false);
      empty.setPadding(dp(14), dp(18), dp(14), dp(18));
      empty.setBackground(shape(0x88FFFFFF, 13));
      block.addView(empty);
    } else
      for (int i = 0; i < items.size(); i++) {
        Parcel parcel = items.get(i);
        LinearLayout front = row();
        front.setBackgroundColor(AppStyle.PAPER);
        CodeAppearance appearance = CodeAppearance.read(this);
        front.setPadding(dp(16), dp(appearance.rowPadding()), dp(10), dp(appearance.rowPadding()));
        LinearLayout label = column();
        TextView code = text(parcel.code, 28, INK, true);
        code.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        appearance.apply(code);
        label.addView(code, new LinearLayout.LayoutParams(-1, -2));
        boolean overdue = ParcelAge.days(parcel.createdAt) >= store.reminderDays();
        if (overdue) label.addView(text(ParcelAge.label(parcel.createdAt)+" · 记得取件",11,BLUE,true));
        code.setOnLongClickListener(
            v -> {
              openFeature("edit", parcel.stationId, parcel.id);
              return true;
            });
        label.setOnLongClickListener(
            v -> {
              openFeature("edit", parcel.stationId, parcel.id);
              return true;
            });
        front.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = button("", INK, Color.TRANSPARENT);
        AppStyle.icon(done,R.drawable.ic_checkbox,INK,false);
        done.setTextSize(12);
        done.setPadding(dp(10), 0, dp(10), 0);
        done.setContentDescription("删除取件码 " + parcel.code);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(48),dp(48));
        doneParams.setMarginEnd(CodeAppearance.characterWidth(code));
        front.addView(done,doneParams);
        SwipeCard card = new SwipeCard(this, front, () -> removeParcel(parcel));
        card.setContentDescription("取件码 " + parcel.code + "，向左滑动删除");
        done.setOnClickListener(v -> removeParcel(parcel));
        block.addView(card);
        if (i < items.size() - 1) {
          View divider = new View(this);
          divider.setBackgroundColor(AppStyle.LINE);
          LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
          dividerParams.setMarginStart(dp(12));
          dividerParams.setMarginEnd(dp(12));
          block.addView(divider, dividerParams);
        }
      }
    groups.addView(block, new LinearLayout.LayoutParams(-1, -2));
    space(groups, 18);
  }

  private EditText input(String hint) {
    EditText edit = new EditText(this);
    edit.setSingleLine(true);
    edit.setTextSize(20);
    edit.setTextColor(INK);
    edit.setHintTextColor(MUTED);
    edit.setHint(hint);
    edit.setPadding(dp(14), dp(14), dp(14), dp(14));
    edit.setBackground(shape(0xFFF2F5FA, 12));
    AppStyle.input(edit);
    return edit;
  }

  private LinearLayout dialogBody() {
    LinearLayout b = column();
    b.setPadding(dp(22), dp(10), dp(22), dp(8));
    return b;
  }

  private void showAdd() {
    showAdd(null);
  }

  private void showAdd(String initialCode) {
    if (stations == null) {
      render();
      return;
    }
    if (stations.isEmpty()) {
      showSettings();
      return;
    }
    LinearLayout body = dialogBody();
    body.addView(text("输入取件码，自动找到对应站点", 13, MUTED, false));
    space(body, 18);
    EditText edit = input("例如 1-2-3456");
    edit.setId(R.id.code_input);
    // Keep the text key listener so paste retains every character for validation.
    edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    edit.setRawInputType(InputType.TYPE_CLASS_PHONE);
    body.addView(edit);
    space(body, 12);
    TextView status = text("会按你设置的格式识别站点", 13, MUTED, false);
    status.setId(R.id.input_status);
    body.addView(status);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("录入取件码")
            .setView(body)
            .setNegativeButton("取消", null)
            .setNeutralButton("手动选站点", (d, w) -> openFeature("edit", -1, -1))
            .setPositiveButton("保存取件码", null)
            .create();
    edit.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            try {
              Station matched = store.stationFor(s.toString());
              status.setText(matched == null ? "会按你设置的格式识别站点" : "将存入 · " + matched.name);
              status.setTextColor(
                  matched == null ? MUTED : COLORS[Math.floorMod(matched.id, COLORS.length)]);
            } catch (RuntimeException error) {
              status.setText("暂时无法读取站点，请重试");
              status.setTextColor(BLUE);
            }
          }

          public void afterTextChanged(Editable e) {}
        });
    dialog.setOnShowListener(
        d -> {
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    try {
                      if (store.stationFor(edit.getText().toString()) == null) {
                        status.setText("格式不匹配，请检查取件码或在站点设置中修改格式");
                        status.setTextColor(BLUE);
                        return;
                      }
                      store.add(edit.getText().toString());
                      dialog.dismiss();
                      render();
                    } catch (SQLiteConstraintException error) {
                      status.setText("此取件码已在待取列表中");
                      status.setTextColor(BLUE);
                    } catch (RuntimeException error) {
                      status.setText("保存失败，请重试。记录尚未添加。");
                      status.setTextColor(BLUE);
                    }
                  });
          edit.requestFocus();
          dialog
              .getWindow()
              .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
    activeDialog = dialog;
    activeDialogKind = "add";
    draftCode = edit;
    draftStationName = null;
    draftStationFormats = null;
    dialog.setOnDismissListener(
        d -> {
          if (activeDialog == dialog) {
            activeDialog = null;
            draftCode = null;
          }
        });
    if (initialCode != null) {
      edit.setText(initialCode);
      edit.setSelection(edit.length());
    }
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void showSettings() {
    final List<Station> current;
    try {
      current = store.stations();
    } catch (RuntimeException error) {
      showError("无法读取站点，请重试。", null);
      return;
    }
    LinearLayout body = dialogBody();
    body.addView(text("管理取件站点和自动识别格式\n长按站点拖动，或用箭头调整顺序", 13, MUTED, false));
    if (current.isEmpty()) {
      space(body, 22);
      body.addView(text("还没有站点，点击下方新增站点。", 14, MUTED, false));
    }
    ScrollView viewport = new ScrollView(this);
    viewport.addView(body);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("站点设置")
            .setView(viewport)
            .setNegativeButton("完成", null)
            .setPositiveButton("新增站点", null)
            .create();
    final int[] pendingDrop = {-1, -1};
    for (Station station : current) {
      space(body, 12);
      LinearLayout card = row();
      card.setPadding(dp(12), dp(12), dp(8), dp(12));
      card.setBackground(shape(TINTS[Math.floorMod(station.id, TINTS.length)], 14));
      int iconIndex = StationIcons.indexOf(station.iconKey);
      android.widget.ImageView icon = new android.widget.ImageView(this);
      icon.setImageResource(StationIcons.RESOURCES[iconIndex]);
      icon.setContentDescription("站点图标 " + StationIcons.LABELS[iconIndex]);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
      iconParams.setMarginEnd(dp(10));
      card.addView(icon, iconParams);
      LinearLayout info = column();
      info.addView(text(station.name, 17, INK, true));
      space(info, 6);
      info.addView(text(station.formats.replace("\n", " / "), 12, MUTED, false));
      card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
      Button edit = button("编辑", INK, Color.WHITE);
      edit.setContentDescription("编辑站点 " + station.name);
      edit.setOnClickListener(
          v -> {
            dialog.dismiss();
            showStationEditor(station.id, null, null, null);
          });
      card.addView(edit);
      LinearLayout stationBlock = column();
      stationBlock.addView(card);
      LinearLayout moves = row();
      Button up = button("↑ 上移", MUTED, BG);
      up.setTextSize(12);
      up.setContentDescription("上移站点 " + station.name);
      Button down = button("↓ 下移", MUTED, BG);
      down.setTextSize(12);
      down.setContentDescription("下移站点 " + station.name);
      int position = current.indexOf(station);
      up.setEnabled(position > 0);
      down.setEnabled(position < current.size() - 1);
      up.setOnClickListener(v -> moveStation(current, station.id, position - 1, dialog));
      down.setOnClickListener(v -> moveStation(current, station.id, position + 1, dialog));
      moves.addView(up, new LinearLayout.LayoutParams(0, -2, 1));
      moves.addView(down, new LinearLayout.LayoutParams(0, -2, 1));
      stationBlock.addView(moves);
      body.addView(stationBlock);
      View.OnLongClickListener drag =
          v ->
              v.startDragAndDrop(
                  ClipData.newPlainText("站点排序", ""),
                  new View.DragShadowBuilder(card),
                  Integer.valueOf(station.id),
                  0);
      card.setOnLongClickListener(drag);
      info.setOnLongClickListener(drag);
      stationBlock.setOnDragListener(
          (v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
              if (pendingDrop[0] >= 0 && event.getResult()) {
                int sourceId = pendingDrop[0], target = pendingDrop[1];
                pendingDrop[0] = -1;
                // Keep the source window alive until Android has finished the drag session.
                handler.post(
                    () -> {
                      if (dialog.isShowing()) moveStation(current, sourceId, target, dialog);
                    });
              } else pendingDrop[0] = -1;
              return true;
            }
            if (!(event.getLocalState() instanceof Integer)) return false;
            if (event.getAction() == DragEvent.ACTION_DROP) {
              pendingDrop[0] = (Integer) event.getLocalState();
              pendingDrop[1] = position;
            }
            return true;
          });
    }
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      dialog.dismiss();
                      showStationEditor(-1, null, null, null);
                    }));
    activeDialog = dialog;
    activeDialogKind = "settings";
    draftCode = null;
    draftStationName = null;
    draftStationFormats = null;
    dialog.setOnDismissListener(
        d -> {
          if (activeDialog == dialog) activeDialog = null;
        });
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void moveStation(List<Station> stations, int sourceId, int target, AlertDialog dialog) {
    ArrayList<Integer> ids = new ArrayList<>();
    for (Station station : stations) ids.add(station.id);
    if (target < 0 || target >= ids.size() || !ids.remove(Integer.valueOf(sourceId))) return;
    ids.add(target, sourceId);
    try {
      store.reorderStations(ids);
      dialog.dismiss();
      render();
      showSettings();
    } catch (RuntimeException e) {
      showError("调整顺序失败，请重试。", null);
    }
  }

  private void openFeature(String mode, int station, long parcel) {
    String code =
        "edit".equals(mode) && parcel < 0 && draftCode != null
            ? draftCode.getText().toString()
            : null;
    if (activeDialog != null) activeDialog.dismiss();
    startActivity(
        new Intent(this, FeaturesActivity.class)
            .putExtra("mode", mode)
            .putExtra("station", station)
            .putExtra("parcel", parcel)
            .putExtra("code", code));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (store != null && groups != null) render();
  }

  private void pasteFromClipboard() {
    ClipboardImport.Result result = ClipboardImport.read(this, store);
    startActivity(
        new Intent(this, FeaturesActivity.class)
            .putExtra("mode", "batch")
            .putExtra("source", result.codes)
            .putExtra("clipboardNote", result.message));
  }

  private void showStationEditor(
      int stationId, String initialName, String initialFormats, String initialIcon) {
    Station station = null;
    if (stationId >= 0) {
      try {
        for (Station value : store.stations()) if (value.id == stationId) station = value;
      } catch (RuntimeException error) {
        showError("无法读取站点，请重试。", null);
        return;
      }
      if (station == null) {
        showError("站点已不存在，请重新打开站点设置。", null);
        return;
      }
    }
    LinearLayout body = dialogBody();
    body.addView(text("站点图标", 13, INK, true));
    space(body, 8);
    String[] selectedIcon = {
      initialIcon != null ? initialIcon : station == null ? "shop" : station.iconKey
    };
    StationIcons.checked(selectedIcon[0]);
    android.widget.GridLayout iconGrid = new android.widget.GridLayout(this);
    iconGrid.setColumnCount(4);
    iconGrid.setRowCount(2);
    ArrayList<android.widget.ImageButton> iconButtons = new ArrayList<>();
    for (int i = 0; i < StationIcons.KEYS.length; i++) {
      final int iconIndex = i;
      LinearLayout choice = column();
      choice.setGravity(Gravity.CENTER);
      choice.setPadding(dp(3), dp(3), dp(3), dp(5));
      android.widget.ImageButton iconButton = new android.widget.ImageButton(this);
      iconButton.setImageResource(StationIcons.RESOURCES[i]);
      iconButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
      iconButton.setPadding(dp(8), dp(8), dp(8), dp(8));
      iconButtons.add(iconButton);
      iconButton.setOnClickListener(
          v -> {
            selectedIcon[0] = StationIcons.KEYS[iconIndex];
            draftStationIcon = selectedIcon[0];
            styleIconChoices(iconButtons, selectedIcon[0]);
          });
      choice.addView(iconButton, new LinearLayout.LayoutParams(dp(54), dp(54)));
      TextView iconLabel = text(StationIcons.LABELS[i], 11, MUTED, false);
      iconLabel.setGravity(Gravity.CENTER);
      choice.addView(iconLabel, new LinearLayout.LayoutParams(-1, -2));
      android.widget.GridLayout.LayoutParams cell =
          new android.widget.GridLayout.LayoutParams(
              android.widget.GridLayout.spec(i / 4),
              android.widget.GridLayout.spec(i % 4, 1f));
      cell.width = 0;
      cell.height = -2;
      iconGrid.addView(choice, cell);
    }
    styleIconChoices(iconButtons, selectedIcon[0]);
    body.addView(iconGrid, new LinearLayout.LayoutParams(-1, -2));
    space(body, 18);
    body.addView(text("站点名称", 13, INK, true));
    space(body, 8);
    EditText name = input("例如 小区东门");
    name.setId(R.id.station_name);
    name.setTextSize(17);
    name.setText(initialName != null ? initialName : station == null ? "" : station.name);
    body.addView(name);
    space(body, 18);
    body.addView(text("取件码格式", 13, INK, true));
    space(body, 8);
    EditText formats = input("例如 x-x-xxx\nxx-x-xxx");
    formats.setId(R.id.station_formats);
    formats.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    formats.setSingleLine(false);
    formats.setMinLines(2);
    formats.setMaxLines(5);
    formats.setTextSize(17);
    formats.setTypeface(Typeface.MONOSPACE);
    formats.setGravity(Gravity.TOP | Gravity.START);
    formats.setText(
        initialFormats != null ? initialFormats : station == null ? "" : station.formats);
    body.addView(formats);
    space(body, 10);
    body.addView(text("一个 x 表示一位数字，- 原样匹配。\n同一站点多种格式，每行填一种。", 12, MUTED, false));
    if (station != null) {
      space(body, 8);
      body.addView(text("修改格式仅影响之后录入的取件码，已有记录保留在本站点。", 12, MUTED, false));
    }
    space(body, 12);
    TextView errorText = text("", 13, BLUE, false);
    errorText.setId(R.id.station_error);
    body.addView(errorText);
    ScrollView viewport = new ScrollView(this);
    viewport.addView(body);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this)
            .setTitle(station == null ? "新增站点" : "编辑站点")
            .setView(viewport)
            .setNegativeButton(
                "取消",
                (d, w) -> {
                  d.dismiss();
                  showSettings();
                })
            .setPositiveButton("保存站点", null);
    if (station != null) builder.setNeutralButton("删除站点", null);
    AlertDialog dialog = builder.create();
    dialog.setOnShowListener(
        d -> {
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    try {
                      if (stationId < 0)
                        store.addStation(
                            name.getText().toString(),
                            formats.getText().toString(),
                            selectedIcon[0]);
                      else
                        store.updateStation(
                            stationId,
                            name.getText().toString(),
                            formats.getText().toString(),
                            selectedIcon[0]);
                      dialog.dismiss();
                      render();
                      showSettings();
                    } catch (IllegalArgumentException error) {
                      errorText.setText(error.getMessage());
                    } catch (RuntimeException error) {
                      errorText.setText("保存失败，请重试。原有设置未更改。");
                    }
                  });
          if (stationId >= 0)
            dialog
                .getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(
                    v -> {
                      try {
                        for (Parcel item : store.all())
                          if (item.stationId == stationId) {
                            errorText.setText("本站点还有待取件，请先取完再删除站点。");
                            return;
                          }
                        deleteConfirmation =
                            new AlertDialog.Builder(this)
                                .setTitle("删除站点")
                                .setMessage("确定删除这个站点吗？删除后可重新添加。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton(
                                    "确认删除",
                                    (confirmation, w) -> {
                                      try {
                                        store.deleteStation(stationId);
                                        dialog.dismiss();
                                        render();
                                        showSettings();
                                      } catch (IllegalArgumentException error) {
                                        errorText.setText(error.getMessage());
                                      } catch (RuntimeException error) {
                                        errorText.setText("删除站点失败，请重试。");
                                      }
                                    })
                                .create();
                        deleteConfirmation.setOnDismissListener(
                            confirmation -> deleteConfirmation = null);
                        deleteConfirmation.show();
                        AppStyle.dialog(deleteConfirmation);
                      } catch (RuntimeException error) {
                        errorText.setText("无法读取取件记录，请重试。");
                      }
                    });
        });
    editingStationId = stationId;
    draftStationName = name;
    draftStationFormats = formats;
    draftStationIcon = selectedIcon[0];
    activeDialog = dialog;
    activeDialogKind = "stationEditor";
    draftCode = null;
    dialog.setOnCancelListener(
        d -> {
          d.dismiss();
          showSettings();
        });
    dialog.setOnDismissListener(
        d -> {
          if (activeDialog == dialog) {
            activeDialog = null;
            draftStationName = null;
            draftStationFormats = null;
            draftStationIcon = null;
          }
        });
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void styleIconChoices(
      List<android.widget.ImageButton> buttons, String selectedIcon) {
    for (int i = 0; i < buttons.size(); i++) {
      boolean selected = StationIcons.KEYS[i].equals(selectedIcon);
      android.widget.ImageButton button = buttons.get(i);
      button.setBackground(
          AppStyle.surface(this, selected ? AppStyle.TINT : AppStyle.PAPER, 6, true));
      button.setContentDescription(
          (selected ? "已选择图标 " : "选择图标 ") + StationIcons.LABELS[i]);
      button.setSelected(selected);
    }
  }

  private void removeParcel(Parcel parcel) {
    try {
      store.delete(parcel.id);
      groups.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
      render();
      groups.announceForAccessibility("已取 " + parcel.code + "，可到已取件记录恢复");
    } catch (RuntimeException error) {
      render();
      showError("删除失败，请重试。", null);
    }
  }

  private void showError(String message, Runnable retry) {
    if (isFinishing() || isDestroyed()) return;
    AlertDialog.Builder b =
        new AlertDialog.Builder(this)
            .setTitle("操作提示")
            .setMessage(message)
            .setNegativeButton("关闭", null);
    if (retry != null) b.setPositiveButton("重试", (d, w) -> retry.run());
    b.show();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (activeDialog != null && activeDialog.isShowing()) {
      if ("add".equals(activeDialogKind) && draftCode != null) {
        state.putString("dialog", "add");
        state.putString("draftCode", draftCode.getText().toString());
      } else if ("stationEditor".equals(activeDialogKind) && draftStationName != null) {
        state.putString("dialog", "stationEditor");
        state.putInt("stationId", editingStationId);
        state.putString("draftName", draftStationName.getText().toString());
        state.putString("draftFormats", draftStationFormats.getText().toString());
        state.putString("draftIcon", draftStationIcon);
      } else if ("settings".equals(activeDialogKind)) state.putString("dialog", "settings");
    }
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (homePopup != null) homePopup.dismiss();
    if (deleteConfirmation != null) deleteConfirmation.dismiss();
    if (activeDialog != null) activeDialog.dismiss();
    store.close();
    super.onDestroy();
  }
}
