package cn.pickup.pocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
  private final Set<Integer> expandedEmpty = new HashSet<>();
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    LegacyCleanup.run(this);
    WidgetSupport.publishPreview(this);
    store = new ParcelStore(this);
    buildScreen();
    render();
    if (state != null) {
      if ("add".equals(state.getString("dialog"))) showAdd(state.getString("draftCode", ""));
    }
    if (state == null && Onboarding.shouldShow(this)) {
      startActivity(new Intent(this, FeaturesActivity.class).putExtra("mode", "guide"));
    }
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
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
    LinearLayout actions = row();
    LinearLayout entry = row();
    int actionHeight = dp(compact ? 50 : 56);
    entry.setBackground(AppStyle.surface(this,AppStyle.PRIMARY,6,true));
    Button add = button("录入取件码",Color.WHITE,Color.TRANSPARENT);
    add.setId(R.id.add_code); add.setTextSize(compact ? 14 : 16);
    add.setPadding(dp(8), 0, dp(8), 0);
    add.setOnClickListener(v -> showAdd());
    add.setContentDescription("录入取件码");
    add.setMinimumHeight(actionHeight);
    entry.addView(add,new LinearLayout.LayoutParams(0,-1,1));
    View divider = new View(this);
    divider.setBackgroundColor(0x66FFF6DB);
    entry.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(28)));
    Button options = button("▴", Color.WHITE, Color.TRANSPARENT);
    options.setId(R.id.add_code_options);
    options.setTextSize(22);
    options.setPadding(0,0,0,0);
    options.setContentDescription("展开更多录入方式");
    options.setMinimumHeight(actionHeight);
    options.setOnClickListener(v -> showEntryOptions(options));
    LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(dp(44),-1);
    entry.addView(options,optionParams);
    actions.addView(entry,new LinearLayout.LayoutParams(0,actionHeight,5));
    completeAll = button("一键取出", INK, AppStyle.TINT);
    completeAll.setId(R.id.home_complete_all);
    completeAll.setTextSize(compact ? 13 : 15);
    completeAll.setPadding(dp(8),0,dp(8),0);
    completeAll.setMinimumHeight(actionHeight);
    completeAll.setOnClickListener(v -> {
      try {
        int count = store.completeAll();
        if (count > 0) Toast.makeText(this, "已取出 " + count + " 件，可在已取件中恢复", Toast.LENGTH_SHORT).show();
        render();
      } catch (RuntimeException error) {
        showError("一键取出失败，待取清单未更改。", null);
      }
    });
    LinearLayout.LayoutParams completeParams = new LinearLayout.LayoutParams(0,actionHeight,3);
    completeParams.setMarginStart(dp(10));
    actions.addView(completeAll,completeParams);
    footer.addView(actions,new LinearLayout.LayoutParams(-1,-2));
    root.addView(footer);
    AppStyle.enter(content);
  }

  private void showEntryOptions(View anchor) {
    LinearLayout panel = column();
    panel.setPadding(dp(8),dp(8),dp(8),dp(14));
    String[] labels = {"批量录入", "截图识别", "扫描短信"};
    String[] modes = {"batch", "ocr", "sms"};
    int[] icons = {R.drawable.ic_content_paste,R.drawable.ic_image_search,R.drawable.ic_sms};
    for (int i=0;i<labels.length;i++) {
      String mode = modes[i];
      LinearLayout item = menuItem(labels[i], icons[i]);
      item.setMinimumHeight(dp(48));
      item.setOnClickListener(v -> {
        if (homePopup != null) homePopup.dismiss();
        openFeature(mode,-1,-1);
      });
      addMenuItem(panel, item, i);
    }
    int popupWidth = menuWidth(panel);
    int popupHeight = panel.getMeasuredHeight();
    android.widget.PopupWindow popup = new android.widget.PopupWindow(panel,popupWidth,popupHeight,true);
    popup.setOutsideTouchable(true);
    popup.setElevation(dp(10));
    homePopup = popup;
    int[] location = new int[2];
    anchor.getLocationOnScreen(location);
    int x = Math.max(dp(8),Math.min(location[0]+anchor.getWidth()-popupWidth,
        getResources().getDisplayMetrics().widthPixels-popupWidth-dp(8)));
    popup.setBackgroundDrawable(AppStyle.menuSurface(this, location[0]+anchor.getWidth()/2-x, false));
    int y = Math.max(dp(8),location[1]-popupHeight-dp(8));
    popup.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,x,y);
  }

  private LinearLayout menuItem(String label, int iconResource) {
    // Center visible ink, not the transparent margins inside an icon drawable.
    LinearLayout item = row();
    item.setGravity(Gravity.CENTER);
    item.setPadding(dp(16), dp(12), dp(16), dp(12));
    item.setMinimumHeight(dp(48));
    item.setBackground(AppStyle.ripple(this, Color.TRANSPARENT, 6));
    item.setFocusable(true);
    android.widget.ImageView icon = new android.widget.ImageView(this);
    icon.setImageResource(iconResource);
    icon.setColorFilter(INK);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
    labelParams.setMarginStart(dp(12));
    TextView labelView = text(label, 15, INK, true);
    item.addView(labelView, labelParams);
    int opticalOffset = menuOpticalOffset(icon.getDrawable(), labelView);
    item.setPadding(dp(16)-opticalOffset, dp(12), dp(16)+opticalOffset, dp(12));
    return item;
  }

  private int menuOpticalOffset(android.graphics.drawable.Drawable icon, TextView label) {
    int size = dp(23);
    android.graphics.Bitmap probe = android.graphics.Bitmap.createBitmap(size, size,
        android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Rect original = new android.graphics.Rect(icon.getBounds());
    icon.setBounds(0, 0, size, size);
    icon.draw(new android.graphics.Canvas(probe));
    icon.setBounds(original);
    int visibleStart = size;
    for (int x=0; x<size && visibleStart==size; x++) {
      for (int y=0; y<size; y++) {
        if (Color.alpha(probe.getPixel(x,y)) >= 128) { visibleStart=x; break; }
      }
    }
    probe.recycle();
    android.graphics.Rect textInk = new android.graphics.Rect();
    String value = label.getText().toString();
    label.getPaint().getTextBounds(value, 0, value.length(), textInk);
    int textWidth = (int) Math.ceil(android.text.Layout.getDesiredWidth(value, label.getPaint()));
    int trailingSpace = Math.max(0, textWidth-textInk.right);
    return visibleStart==size ? 0 : Math.round((visibleStart-trailingSpace)/2f);
  }

  private void addMenuItem(LinearLayout panel, LinearLayout item, int index) {
    if (index > 0) {
      View divider = new View(this);
      divider.setBackgroundColor(AppStyle.LINE);
      LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
      dividerParams.setMarginStart(dp(8));
      dividerParams.setMarginEnd(dp(8));
      panel.addView(divider, dividerParams);
    }
    panel.addView(item,new LinearLayout.LayoutParams(-1,-2));
  }

  private int menuWidth(LinearLayout panel) {
    panel.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    return Math.min(panel.getMeasuredWidth(),
        getResources().getDisplayMetrics().widthPixels - dp(36));
  }

  private void showMenu(View anchor) {
    LinearLayout panel = column();
    panel.setPadding(dp(8),dp(14),dp(8),dp(8));
    ScrollView menuScroll = new ScrollView(this);
    menuScroll.setVerticalScrollBarEnabled(false);
    menuScroll.addView(panel);
    android.widget.PopupWindow popup = new android.widget.PopupWindow(menuScroll,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);
    popup.setBackgroundDrawable(AppStyle.surface(this,AppStyle.PAPER,6,true));
    homePopup = popup;
    popup.setOutsideTouchable(true); popup.setElevation(dp(10));
    String[] labels = {"手动录入","已取快递","站点设置","更多设置"};
    int[] icons = {R.drawable.ic_add,R.drawable.ic_history,R.drawable.ic_settings,R.drawable.ic_tune};
    for (int i=0;i<labels.length;i++) {
      final int action=i;
      LinearLayout item = menuItem(labels[i], icons[i]);
      if (i==2) item.setId(R.id.station_settings);
      item.setOnClickListener(v -> {
        popup.dismiss();
        if (action==0) openFeature("edit",-1,-1);
        else if (action==1) openFeature("history",-1,-1);
        else if (action==2) showSettings();
        else openFeature("more",-1,-1);
      });
      addMenuItem(panel, item, i);
    }
    int width = menuWidth(panel);
    popup.setBackgroundDrawable(AppStyle.menuSurface(this, width-anchor.getWidth()/2, true));
    panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    popup.setWidth(width);
    popup.setHeight(Math.min(panel.getMeasuredHeight(),
        getResources().getDisplayMetrics().heightPixels - dp(80)));
    popup.showAsDropDown(anchor,0,dp(6),Gravity.END);
  }

  private void render() {
    try {
      List<Parcel> parcels = store.all();
      stations = store.stations();
      total.setText(String.valueOf(parcels.size()));
      total.setContentDescription(parcels.size() + " 件待取快递");
      overviewNote.setText(" 件待取 · " + stations.size() + " 个站点");
      completeAll.setEnabled(!parcels.isEmpty());
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
        create.setOnClickListener(v -> startActivity(new Intent(this, StationSettingsActivity.class).putExtra("new_station", true)));
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
      empty.setBackground(AppStyle.surface(this,AppStyle.PAPER,13,false));
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
    if (activeDialog != null) activeDialog.dismiss();
    startActivity(new Intent(this, StationSettingsActivity.class));
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
    AlertDialog error = b.create();
    error.show();
    AppStyle.dialog(error);
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (activeDialog != null && activeDialog.isShowing()) {
      if ("add".equals(activeDialogKind) && draftCode != null) {
        state.putString("dialog", "add");
        state.putString("draftCode", draftCode.getText().toString());
      }
    }
  }

  @Override
  public void onDestroy() {
    if (homePopup != null) homePopup.dismiss();
    if (activeDialog != null) activeDialog.dismiss();
    store.close();
    super.onDestroy();
  }
}
