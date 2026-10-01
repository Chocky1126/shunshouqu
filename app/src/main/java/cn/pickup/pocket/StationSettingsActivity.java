package cn.pickup.pocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Local station management. Drafts are never written until the user saves. */
public final class StationSettingsActivity extends Activity {
  private ParcelStore store;
  private LinearLayout body, formatRows;
  private ScrollView scroll;
  private EditText name, example, testCode;
  private TextView error, testResult, generatorResult;
  private Button save, addFormat;
  private final ArrayList<RuleRow> rules = new ArrayList<>();
  private final ArrayList<ImageButton> iconButtons = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private AlertDialog confirmation;
  private List<Station> otherStations;
  private boolean editing, sorting;
  private int stationId = -1, pendingSource = -1, pendingTarget = -1;
  private String iconKey = "shop";

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new ParcelStore(this);
    sorting = state != null && state.getBoolean("sorting");
    try {
      if (state != null && state.getBoolean("editing")) {
        stationId = state.getInt("stationId", -1);
        iconKey = state.getString("icon", "shop");
        showEditor(state.getString("name", ""), state.getStringArrayList("rules"),
            state.getString("example", ""), state.getString("test", ""));
      } else if (state == null && getIntent().getBooleanExtra("new_station", false)) openEditor(null);
      else showList();
      if (state != null) scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll", 0)));
    } catch (RuntimeException failure) { fail("无法读取站点，请返回后重试。"); }
  }

  private int dp(float n) { return AppStyle.dp(this, n); }
  private LinearLayout column() {
    LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
  }
  private LinearLayout row() {
    LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view;
  }
  private TextView text(String value, int size, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value); view.setTextSize(size); view.setTextColor(AppStyle.INK);
    view.setIncludeFontPadding(false);
    if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return view;
  }
  private TextView note(String value) {
    TextView view = text(value, 13, false); view.setTextColor(AppStyle.MUTED);
    view.setLineSpacing(dp(3), 1); return view;
  }
  private void add(View view, int margin) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(margin); body.addView(view, params);
  }
  private Button button(String value, boolean primary, Runnable action) {
    Button view = AppStyle.button(this, value, primary ? Color.WHITE : AppStyle.INK,
        primary ? AppStyle.PRIMARY : AppStyle.PAPER);
    view.setOnClickListener(v -> action.run()); return view;
  }
  private EditText input(String hint) {
    EditText view = new EditText(this); view.setHint(hint); view.setTextSize(17);
    view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    view.setSingleLine(true); view.setFilters(new InputFilter[] {new InputFilter.LengthFilter(64)});
    AppStyle.input(view); view.setSaveEnabled(false); return view;
  }
  private void watch(EditText view, Runnable action) {
    view.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
      public void afterTextChanged(Editable value) {}
    });
  }
  private LinearLayout shell(String title) {
    LinearLayout root = column(); root.setBackgroundColor(AppStyle.BG); setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      root.setOnApplyWindowInsetsListener((view, insets) -> {
        android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
            | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
        view.setPadding(i.left, i.top, i.right, i.bottom); return insets;
      });
      root.requestApplyInsets();
    } else root.setFitsSystemWindows(true);
    LinearLayout header = row(); header.setPadding(dp(16), dp(8), dp(16), dp(8));
    Button back = button("", false, this::onBackPressed);
    AppStyle.icon(back, R.drawable.ic_arrow_back, AppStyle.INK, false);
    back.setPadding(dp(13), dp(12), dp(13), dp(12));
    back.setContentDescription(editing ? "返回站点设置" : "返回取件清单");
    header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(50)));
    TextView heading = text(title, 21, true); heading.setPadding(dp(12), 0, dp(8), 0);
    header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    if (!editing) {
      Button sort = button(sorting ? "完成排序" : "调整顺序", false, () -> {
        sorting = !sorting; showList();
      });
      sort.setTextSize(13); sort.setPadding(dp(9), dp(10), dp(9), dp(10));
      header.addView(sort, new LinearLayout.LayoutParams(-2, -2));
    }
    root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    scroll = new ScrollView(this); scroll.setId(R.id.station_settings_scroll);
    scroll.setFillViewport(true);
    body = column(); body.setPadding(dp(22), dp(8), dp(22), dp(24));
    scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    return root;
  }
  private Button footer(LinearLayout root, String title, Runnable action) {
    LinearLayout footer = column(); footer.setId(R.id.station_settings_footer);
    footer.setPadding(dp(22), dp(8), dp(22), dp(18));
    Button view = button(title, true, action); footer.addView(view, new LinearLayout.LayoutParams(-1, -2));
    root.addView(footer, new LinearLayout.LayoutParams(-1, -2)); return view;
  }

  private void showList() {
    editing = false;
    LinearLayout root = shell("站点设置");
    try {
      List<Station> stations = store.stations();
      Map<Integer, Integer> counts = new HashMap<>();
      for (Parcel parcel : store.all()) counts.put(parcel.stationId, counts.getOrDefault(parcel.stationId, 0) + 1);
      add(note(sorting ? "长按手柄拖动，或点箭头调整顺序。" : stations.size() + " 个站点 · 按取件路线排列"), 4);
      if (stations.isEmpty()) add(note("添加常去的站点，录入后自动归类。"), 24);
      for (int i = 0; i < stations.size(); i++) {
        Station station = stations.get(i); final int position = i;
        LinearLayout card = column(); card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(AppStyle.surface(this, AppStyle.PAPER, 6, true));
        LinearLayout heading = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(StationIcons.RESOURCES[StationIcons.indexOf(station.iconKey)]);
        icon.setContentDescription("站点图标 " + StationIcons.LABELS[StationIcons.indexOf(station.iconKey)]);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMarginEnd(dp(10)); heading.addView(icon, iconParams);
        LinearLayout info = column(); info.addView(text(station.name, 18, true));
        TextView count = note(counts.getOrDefault(station.id, 0) + " 件待取");
        count.setPadding(0, dp(5), 0, 0); info.addView(count);
        heading.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView handle = text(sorting ? "≡" : "›", 26, false);
        handle.setGravity(Gravity.CENTER); handle.setMinHeight(dp(48));
        heading.addView(handle, new LinearLayout.LayoutParams(dp(40), -2));
        card.addView(heading);
        TextView formats = note(station.formats.replace("\n", " / "));
        formats.setTypeface(Typeface.MONOSPACE); formats.setPadding(0, dp(12), 0, 0);
        card.addView(formats);
        if (!sorting) {
          card.setContentDescription("编辑站点 " + station.name);
          card.setOnClickListener(v -> openEditor(station));
        } else {
          handle.setContentDescription("拖动站点 " + station.name);
          View.OnLongClickListener drag = v -> v.startDragAndDrop(
              ClipData.newPlainText("站点排序", ""), new View.DragShadowBuilder(card), station.id, 0);
          handle.setOnLongClickListener(drag); card.setOnLongClickListener(drag);
          LinearLayout arrows = row(); arrows.setPadding(0, dp(12), 0, 0);
          Button up = button("↑ 上移", false, () -> move(stations, station.id, position - 1));
          Button down = button("↓ 下移", false, () -> move(stations, station.id, position + 1));
          up.setContentDescription("上移站点 " + station.name); up.setEnabled(position > 0);
          down.setContentDescription("下移站点 " + station.name); down.setEnabled(position < stations.size() - 1);
          LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.setMarginEnd(dp(8));
          arrows.addView(up, left); arrows.addView(down, new LinearLayout.LayoutParams(0, -2, 1));
          card.addView(arrows);
          card.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
              card.setBackground(AppStyle.surface(this, AppStyle.PAPER, 6, true));
              if (pendingSource >= 0 && event.getResult()) {
                int source = pendingSource, target = pendingTarget; pendingSource = -1;
                handler.post(() -> { if (!isFinishing() && !isDestroyed()) move(stations, source, target); });
              } else pendingSource = -1;
              return true;
            }
            if (!(event.getLocalState() instanceof Integer)) return false;
            if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED)
              card.setBackground(AppStyle.surface(this, AppStyle.TINT, 6, true));
            if (event.getAction() == DragEvent.ACTION_DRAG_EXITED)
              card.setBackground(AppStyle.surface(this, AppStyle.PAPER, 6, true));
            if (event.getAction() == DragEvent.ACTION_DROP) {
              pendingSource = (Integer) event.getLocalState(); pendingTarget = position;
            }
            return true;
          });
        }
        add(card, 14);
      }
      footer(root, "新增站点", () -> openEditor(null)).setVisibility(sorting ? View.GONE : View.VISIBLE);
    } catch (RuntimeException failure) { add(note("读取失败，请返回后重试。"), 20); }
  }
  private void move(List<Station> stations, int source, int target) {
    ArrayList<Integer> ids = new ArrayList<>(); for (Station s : stations) ids.add(s.id);
    if (target < 0 || target >= ids.size() || !ids.remove(Integer.valueOf(source))) return;
    ids.add(target, source); int y = scroll.getScrollY();
    try { store.reorderStations(ids); showList(); scroll.post(() -> scroll.scrollTo(0, y)); }
    catch (RuntimeException failure) { fail("调整顺序失败，请重试。"); }
  }

  private void openEditor(Station station) {
    hideKeyboard(); stationId = station == null ? -1 : station.id;
    iconKey = station == null ? "shop" : station.iconKey;
    ArrayList<String> values = new ArrayList<>(Arrays.asList(station == null ? new String[]{""} : station.formats.split("\n")));
    showEditor(station == null ? "" : station.name, values, "", "");
  }
  private void showEditor(String stationName, ArrayList<String> values, String exampleValue, String testValue) {
    save = null; error = null; testResult = null; addFormat = null;
    editing = true; rules.clear(); iconButtons.clear(); otherStations = store.stations();
    LinearLayout root = shell(stationId < 0 ? "新增站点" : "编辑站点");
    add(text("站点名称", 15, true), 4);
    name = input("例如 小区东门"); name.setId(R.id.station_name); name.setText(stationName); add(name, 8);
    add(text("站点图标", 15, true), 20);
    GridLayout icons = new GridLayout(this); icons.setColumnCount(4);
    for (int i = 0; i < StationIcons.KEYS.length; i++) {
      final int index = i; LinearLayout choice = column(); choice.setGravity(Gravity.CENTER);
      choice.setPadding(dp(2), dp(4), dp(2), dp(4));
      ImageButton icon = new ImageButton(this);
      icon.setImageResource(StationIcons.RESOURCES[i]); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      icon.setPadding(dp(8), dp(8), dp(8), dp(8)); iconButtons.add(icon);
      icon.setOnClickListener(v -> { iconKey = StationIcons.KEYS[index]; styleIcons(); });
      choice.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
      TextView label = note(StationIcons.LABELS[i]); label.setTextSize(11); label.setGravity(Gravity.CENTER_HORIZONTAL); label.setPadding(0, dp(4), 0, 0);
      choice.addView(label);
      GridLayout.LayoutParams cell = new GridLayout.LayoutParams(GridLayout.spec(i / 4), GridLayout.spec(i % 4, 1f));
      cell.width = 0; icons.addView(choice, cell);
    }
    styleIcons(); add(icons, 8);
    add(text("用示例生成格式", 15, true), 20);
    add(note("填一个完整取件码，自动把数字换成 x。"), 6);
    example = input("输入取件码，例如 12-2-542"); example.setId(R.id.station_example); example.setText(exampleValue); add(example, 8);
    add(button("生成格式", false, this::generate), 8);
    generatorResult = note(""); generatorResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(generatorResult, 8);
    add(text("取件码格式", 15, true), 16);
    add(note("一个 x 代表一位数字；每行一种，最多 10 种。"), 6);
    formatRows = column(); add(formatRows, 0);
    if (values == null) values = new ArrayList<>(Collections.singletonList(""));
    for (String value : values) appendRule(value);
    addFormat = button("添加格式", false, () -> {
      if (rules.size() >= 10) return;
      appendRule(""); validate();
      rules.get(rules.size() - 1).input.requestFocus();
    });
    add(addFormat, 10);
    error = note(""); error.setId(R.id.station_error); error.setTextColor(AppStyle.ACCENT);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(error, 10);
    add(text("试一试能否匹配", 15, true), 16);
    testCode = input("输入取件码测试匹配"); testCode.setId(R.id.station_test_code); testCode.setText(testValue); add(testCode, 8);
    testResult = note(""); testResult.setId(R.id.station_test_result);
    testResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(testResult, 8);
    if (stationId >= 0) {
      add(note("修改格式只影响之后的录入，本站点已有取件码保留。"), 20);
      add(button("删除站点", false, this::delete), 12);
    }
    save = footer(root, "保存站点", this::save);
    watch(name, this::validate); watch(testCode, this::validate); validate();
  }
  private void styleIcons() {
    for (int i = 0; i < iconButtons.size(); i++) {
      boolean chosen = StationIcons.KEYS[i].equals(iconKey); ImageButton icon = iconButtons.get(i);
      icon.setSelected(chosen); icon.setBackground(AppStyle.surface(this, chosen ? AppStyle.TINT : AppStyle.PAPER, 6, true));
      icon.setContentDescription((chosen ? "已选择图标 " : "选择图标 ") + StationIcons.LABELS[i]);
    }
  }
  private final class RuleRow {
    LinearLayout container; EditText input; TextView preview; Button remove;
  }
  private void appendRule(String value) {
    RuleRow rule = new RuleRow(); rule.container = column();
    rule.container.setPadding(dp(10), dp(12), dp(10), dp(12));
    rule.container.setBackground(AppStyle.surface(this, AppStyle.TINT, 6, false));
    LinearLayout line = row(); rule.input = input("例如 x-x-xxx"); rule.input.setText(value);
    rule.input.setTypeface(Typeface.MONOSPACE); rule.input.setPadding(dp(10), dp(12), dp(10), dp(12));
    line.addView(rule.input, new LinearLayout.LayoutParams(0, -2, 1));
    rule.remove = button("×", false, () -> {
      formatRows.removeView(rule.container); rules.remove(rule); relabelRules(); validate();
    });
    rule.remove.setTextSize(22);
    LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(48), -2); removeParams.setMarginStart(dp(8));
    line.addView(rule.remove, removeParams); rule.container.addView(line);
    rule.preview = note(""); rule.preview.setPadding(dp(2), dp(8), 0, 0); rule.container.addView(rule.preview);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10);
    formatRows.addView(rule.container, params); rules.add(rule); relabelRules(); watch(rule.input, this::validate);
  }
  private void relabelRules() {
    for (int i = 0; i < rules.size(); i++) {
      RuleRow rule = rules.get(i); rule.input.setId(i == 0 ? R.id.station_formats : View.NO_ID);
      rule.input.setContentDescription("取件码格式 " + (i + 1)); rule.remove.setContentDescription("删除格式 " + (i + 1));
    }
  }
  private String validatedFormats() {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (int i = 0; i < rules.size(); i++) {
      String rule = CodeRules.normalizeFormats(rules.get(i).input.getText().toString());
      if (rule.contains("\n")) throw new IllegalArgumentException("格式 " + (i + 1) + " 请只填写一种，其他格式点“添加格式”。");
      if (!result.add(rule)) throw new IllegalArgumentException("格式 " + (i + 1) + " 与上面的格式重复，请删除或修改。");
      for (Station s : otherStations)
        if (s.id != stationId && Arrays.asList(s.formats.split("\n")).contains(rule))
          throw new IllegalArgumentException("格式 " + rule + " 已用于「" + s.name + "」");
    }
    return CodeRules.normalizeFormats(String.join("\n", result));
  }
  private void validate() {
    if (save == null || error == null || testResult == null) return;
    for (RuleRow row : rules) {
      try { row.preview.setText("示例 " + CodeRules.exampleForFormat(row.input.getText().toString())); }
      catch (IllegalArgumentException invalid) { row.preview.setText("填写 x 和分隔用的 -"); }
    }
    addFormat.setEnabled(rules.size() < 10);
    String formats = null;
    try { formats = validatedFormats(); error.setText(""); }
    catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
    String stationName = CodeRules.trimWhitespace(name.getText().toString());
    int length = stationName.codePointCount(0, stationName.length());
    save.setEnabled(formats != null && length > 0 && length <= 30);
    if (length > 30) error.setText("站点名称最多 30 个字符");
    String code = testCode.getText().toString();
    if (code.isEmpty()) testResult.setText("只测试匹配，不会录入取件码。");
    else if (formats == null) testResult.setText("请先修正上面的格式");
    else testResult.setText(CodeRules.matches(code, formats) ? "✓ 匹配本站点" : "不匹配本站点，请检查数字位数和分隔符");
  }
  private void generate() {
    try {
      String generated = CodeRules.formatFromExample(example.getText().toString());
      for (RuleRow row : rules) {
        if (CodeRules.normalize(row.input.getText().toString()).equalsIgnoreCase(generated)) {
          generatorResult.setText("已有格式 " + generated); return;
        }
      }
      RuleRow empty = null;
      for (RuleRow row : rules) if (row.input.getText().toString().trim().isEmpty()) { empty = row; break; }
      if (empty != null) empty.input.setText(generated);
      else if (rules.size() < 10) appendRule(generated);
      else { generatorResult.setText("最多 10 种格式，请先删除不需要的格式。"); return; }
      generatorResult.setText("已生成 " + generated); validate(); hideKeyboard();
    } catch (IllegalArgumentException invalid) { generatorResult.setText(invalid.getMessage()); }
  }
  private void save() {
    try {
      String formats = validatedFormats();
      if (stationId < 0) store.addStation(name.getText().toString(), formats, iconKey);
      else store.updateStation(stationId, name.getText().toString(), formats, iconKey);
      hideKeyboard(); showList(); Toast.makeText(this, "站点已保存", Toast.LENGTH_SHORT).show();
    } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
    catch (RuntimeException failure) { error.setText("保存失败，请重试。原有设置未更改。"); }
  }
  private void delete() {
    try {
      for (Parcel p : store.all()) if (p.stationId == stationId) {
        error.setText("本站点还有待取件，请先取完再删除站点。"); scroll.smoothScrollTo(0, error.getTop()); return;
      }
      confirmation = new AlertDialog.Builder(this).setTitle("删除站点")
          .setMessage("确定删除这个站点吗？已有的已取记录仍会保留。")
          .setNegativeButton("取消", null).setPositiveButton("确认删除", (d, w) -> {
            try { store.deleteStation(stationId); hideKeyboard(); showList(); }
            catch (RuntimeException failure) { fail("删除失败，请重试。"); }
          }).create();
      confirmation.show(); AppStyle.dialog(confirmation);
    } catch (RuntimeException failure) { fail("无法读取取件记录，请重试。"); }
  }
  private void hideKeyboard() {
    View focused = getCurrentFocus();
    if (focused != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(), 0);
  }
  private void fail(String message) {
    confirmation = new AlertDialog.Builder(this).setTitle("操作提示").setMessage(message).setPositiveButton("知道了", null).create();
    confirmation.show(); AppStyle.dialog(confirmation);
  }
  @Override public void onBackPressed() {
    if (editing) { hideKeyboard(); showList(); }
    else if (sorting) { sorting = false; showList(); }
    else super.onBackPressed();
  }
  @Override protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state); state.putBoolean("editing", editing); state.putBoolean("sorting", sorting);
    state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    if (editing) {
      state.putInt("stationId", stationId); state.putString("icon", iconKey);
      state.putString("name", name.getText().toString()); state.putString("example", example.getText().toString());
      state.putString("test", testCode.getText().toString());
      ArrayList<String> values = new ArrayList<>(); for (RuleRow row : rules) values.add(row.input.getText().toString());
      state.putStringArrayList("rules", values);
    }
  }
  @Override protected void onDestroy() {
    handler.removeCallbacksAndMessages(null); if (confirmation != null) confirmation.dismiss();
    store.close(); super.onDestroy();
  }
}
