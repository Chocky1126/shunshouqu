package cn.pickup.pocket;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Focused secondary flows; the existing home remains a compact pickup list. */
public final class FeaturesActivity extends Activity {
  private static final int BG = AppStyle.BG,
      INK = AppStyle.INK,
      MUTED = AppStyle.MUTED,
      ACCENT = AppStyle.ACCENT;
  private static final int IMAGE = 20;
  private static final int READ_SMS = 21;
  private ParcelStore store;
  private LinearLayout body;
  private String mode, source = "", imageUri;
  private boolean preview, keepAwake, busyImage, busySms;
  private ArrayList<String> imageQueue = new ArrayList<>();
  private int imageIndex, imageFailures, guidePage;
  private int stationId = -1, editStation = -1;
  private long parcelId = -1, currentId = -1;
  private EditText sourceInput, codeInput;
  private Spinner stationInput;
  private NumberPicker reminderInput;
  private int reminderDraft = -1;
  private final Set<String> unchecked = new HashSet<>();
  private final Map<String, CheckBox> candidates = new LinkedHashMap<>();
  private String editDraft;
  private ScreenshotRecognizer recognizer;
  private AlertDialog dialog;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new ParcelStore(this);
    mode = getIntent().getStringExtra("mode");
    if (mode == null) mode = "more";
    boolean scanFromShortcut = state == null && "sms".equals(mode);
    if ("sms".equals(mode)) mode = "batch";
    stationId = getIntent().getIntExtra("station", -1);
    parcelId = getIntent().getLongExtra("parcel", -1);
    editDraft = getIntent().getStringExtra("code");
    keepAwake = getPreferences(MODE_PRIVATE).getBoolean("keepAwake", false);
    source = getIntent().getStringExtra("source");
    if (source == null || source.length() > ImportPayload.MAX_TEXT) source = "";
    preview = !source.trim().isEmpty();
    ArrayList<Uri> incomingImages = getIntent().getParcelableArrayListExtra("images");
    if (state != null) {
      reminderDraft = state.getInt("reminderDraft", -1);
      mode = state.getString("mode", mode);
      source = state.getString("source", "");
      preview = state.getBoolean("preview");
      imageUri = state.getString("imageUri");
      ArrayList<String> savedQueue = state.getStringArrayList("imageQueue");
      if (savedQueue != null) imageQueue = savedQueue;
      imageIndex = state.getInt("imageIndex", 0);
      imageFailures = state.getInt("imageFailures", 0);
      guidePage = state.getInt("guidePage", 0);
      busyImage = state.getBoolean("busyImage");
      currentId = state.getLong("currentId", -1);
      editDraft = state.getString("editDraft");
      editStation = state.getInt("editStation", -1);
      keepAwake = state.getBoolean("keepAwake", keepAwake);
      ArrayList<String> saved = state.getStringArrayList("unchecked");
      if (saved != null) unchecked.addAll(saved);
    }
    if ("ocr".equals(mode)) {
      mode = "batch";
      render();
      if (state == null) chooseImage();
    } else render();
    if (state == null && incomingImages != null && !incomingImages.isEmpty())
      startImages(incomingImages);
    else if (busyImage && !imageQueue.isEmpty()) recognizeNext();
    if (scanFromShortcut) requestSmsScan();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(6), 0, dp(6));
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(3), 1f);
    if (size >= 18) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  private void gap() {
    body.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
  }

  private Button action(String s, Runnable run) {
    Button b = AppStyle.button(this, s, INK, Color.WHITE);
    b.setOnClickListener(v -> run.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(8);
    body.addView(b, p);
    return b;
  }

  private Button primary(String text, Runnable run) {
    Button b = action(text, run);
    AppStyle.styleButton(b, Color.WHITE, AppStyle.PRIMARY);
    return b;
  }

  private EditText input(String hint, boolean multiline) {
    EditText e = new EditText(this);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setTextSize(19);
    e.setHint(hint);
    AppStyle.input(e);
    e.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
    e.setSingleLine(!multiline);
    if (multiline) {
      e.setMinLines(4);
      e.setMaxLines(8);
      e.setGravity(Gravity.TOP);
    }
    body.addView(e, new LinearLayout.LayoutParams(-1, -2));
    return e;
  }

  private LinearLayout shellRoot(String title) {
    sourceInput = null;
    codeInput = null;
    stationInput = null;
    reminderInput = null;
    candidates.clear();
    LinearLayout root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      root.setOnApplyWindowInsetsListener(
          (v, w) -> {
            android.graphics.Insets i =
                w.getInsets(
                    WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return w;
          });
    }
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(16), dp(8), dp(20), dp(8));
    Button back = AppStyle.button(this, "", INK, Color.WHITE);
    AppStyle.icon(back, R.drawable.ic_arrow_back, INK, false);
    back.setPadding(dp(13), dp(12), dp(13), dp(12));
    back.setContentDescription("返回取件清单");
    back.setOnClickListener(
        v -> {
          if ("guide".equals(mode)) Onboarding.complete(this);
          finish();
        });
    header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(50)));
    TextView heading = label(title, 21, INK);
    heading.setPadding(dp(14), 0, 0, 0);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(header);
    return root;
  }

  private void shell(String title) {
    LinearLayout root = shellRoot(title);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    body = column();
    body.setPadding(dp(22), dp(8), dp(22), dp(30));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    AppStyle.enter(body);
  }

  private void render() {
    try {
      switch (mode) {
        case "batch":
          showBatch();
          break;
        case "pickup":
          showPickup();
          break;
        case "history":
          showHistory();
          break;
        case "edit":
          showEdit();
          break;
        case "guide":
          showGuide();
          break;
        default:
          showMore();
      }
    } catch (RuntimeException e) {
      shell("操作提示");
      body.addView(label("读取失败，请返回后重试。", 16, ACCENT));
    }
  }

  private void message(String text) {
    if (isFinishing() || isDestroyed()) return;
    if (dialog != null) dialog.dismiss();
    dialog =
        new AlertDialog.Builder(this)
            .setTitle("操作提示")
            .setMessage(text)
            .setPositiveButton("知道了", null)
            .create();
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void showBatch() {
    shell("批量录入");
    body.addView(label("粘贴多条短信或取件码，识别后核对再保存。", 14, MUTED));
    sourceInput = input("粘贴短信全文，或每行一个取件码", true);
    sourceInput.setId(R.id.batch_source);
    sourceInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100000)});
    sourceInput.setText(source);
    sourceInput.setEnabled(!busyImage && !busySms);
    Button analyze =
        action(
            "识别取件码",
            () -> {
              source = sourceInput.getText().toString();
              unchecked.clear();
              preview = true;
              hideKeyboard();
              showBatch();
            });
    AppStyle.styleButton(analyze, Color.WHITE, AppStyle.PRIMARY);
    analyze.setEnabled(!busyImage && !busySms);
    Button sms =
        action(busySms ? "正在扫描近三天短信…" : "扫描近三天短信", this::requestSmsScan);
    sms.setEnabled(!busyImage && !busySms);
    body.addView(label("只读取最近 72 小时的短信收件箱；提取后仍需核对保存。", 12, MUTED));
    Button image =
        action(
            busyImage ? "正在识别第 " + (imageIndex + 1) + " / " + imageQueue.size() + " 张…" : "选择截图识别",
            this::chooseImage);
    image.setEnabled(!busyImage && !busySms);
    body.addView(label("支持一次选择最多 10 张，合并识别后核对保存。", 12, MUTED));
    if (imageFailures > 0)
      body.addView(label(imageFailures + " 张图片未能读取，其余结果已保留，可重新选择失败图片。", 13, ACCENT));
    if (busyImage && android.animation.ValueAnimator.areAnimatorsEnabled()) {
      ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      progress.setIndeterminate(true);
      body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
    }
    if (busySms) {
      body.addView(label("正在本机扫描短信，请稍候…", 14, MUTED));
      return;
    }
    body.addView(label("只提取符合站点格式的完整数字片段。识别可能有误，请核对；未匹配的码可在单条录入中手动选择站点。", 12, MUTED));
    if (!preview) return;
    final List<String> found;
    try {
      found = BatchParser.extract(source, store.stations());
    } catch (IllegalArgumentException e) {
      body.addView(label(e.getMessage(), 15, ACCENT));
      return;
    }
    gap();
    body.addView(label("核对识别结果 · " + found.size() + " 个", 18, INK));
    Set<String> existing = new HashSet<>();
    for (Parcel p : store.all()) existing.add(p.code);
    if (found.isEmpty()) {
      body.addView(label("没有找到匹配的取件码。可修改上方文字后重新识别，或到站点设置调整格式。", 15, MUTED));
      return;
    }
    for (String code : found) {
      Station station = store.stationFor(code);
      boolean duplicate = existing.contains(code);
      CheckBox c = new CheckBox(this);
      c.setText(
          code
              + "  ·  "
              + (station == null ? "未匹配" : station.name)
              + (duplicate ? "（已在待取列表）" : ""));
      c.setTextSize(17);
      c.setTextColor(duplicate ? MUTED : INK);
      c.setPadding(dp(10), dp(14), dp(12), dp(14));
      c.setBackground(AppStyle.surface(this, Color.WHITE, 14, false));
      c.setEnabled(!duplicate);
      c.setChecked(!duplicate && !unchecked.contains(code));
      c.setContentDescription("选择取件码 " + code);
      c.setOnCheckedChangeListener(
          (b, checked) -> {
            if (checked) unchecked.remove(code);
            else unchecked.add(code);
          });
      LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, -2);
      choiceParams.topMargin = dp(8);
      body.addView(c, choiceParams);
      candidates.put(code, c);
    }
    primary(
        "保存勾选的取件码",
        () -> {
          // Edited source must be re-analysed so the visible preview cannot silently become stale.
          if (!source.equals(sourceInput.getText().toString())) {
            message("文字已修改，请先点击识别取件码，再核对保存。");
            return;
          }
          ArrayList<String> chosen = new ArrayList<>();
          for (Map.Entry<String, CheckBox> e : candidates.entrySet())
            if (e.getValue().isChecked() && e.getValue().isEnabled()) chosen.add(e.getKey());
          if (chosen.isEmpty()) {
            message("请至少勾选一个尚未录入的取件码。");
            return;
          }
          try {
            store.addBatch(chosen);
            Toast.makeText(this, "已录入 " + chosen.size() + " 个取件码", Toast.LENGTH_SHORT).show();
            finish();
          } catch (RuntimeException e) {
            message("保存失败，可能有取件码已录入或站点格式已变化。此批次未保存，请重新识别。");
          }
        });
  }

  private void requestSmsScan() {
    if (busyImage || busySms) return;
    if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
      startSmsScan();
      return;
    }
    if (dialog != null) dialog.dismiss();
    dialog =
        new AlertDialog.Builder(this)
            .setTitle("扫描近三天短信")
            .setMessage(
                "Android 会请求短信读取权限。顺手取只在你点击扫描后查询最近 72 小时的收件箱，提取取件码供你核对；不会保存或上传短信全文。")
            .setNegativeButton("取消", null)
            .setPositiveButton(
                "继续", (d, which) -> requestPermissions(new String[] {Manifest.permission.READ_SMS}, READ_SMS))
            .create();
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void startSmsScan() {
    if (busySms) return;
    if (sourceInput != null) source = sourceInput.getText().toString();
    busySms = true;
    showBatch();
    List<Station> scanStations = store.stations();
    new Thread(
            () -> {
              try {
                SmsInboxReader.Result result =
                    SmsInboxReader.scan(getApplicationContext(), scanStations, System.currentTimeMillis());
                runOnUiThread(() -> finishSmsScan(result, null));
              } catch (RuntimeException error) {
                runOnUiThread(() -> finishSmsScan(null, error));
              }
            },
            "sms-import")
        .start();
  }

  private void finishSmsScan(SmsInboxReader.Result result, RuntimeException error) {
    if (isFinishing() || isDestroyed() || !"batch".equals(mode)) return;
    busySms = false;
    if (error != null) {
      showBatch();
      message("读取短信失败，可能受到系统或安装方式限制。仍可在短信应用中分享文字，或在批量录入中粘贴。");
      return;
    }
    if (result.codes.isEmpty()) {
      showBatch();
      message(
          "最近三天检查了 "
              + result.checked
              + " 条短信，"
              + (result.skipped > 0
                  ? "识别到的 " + result.skipped + " 个取件码都已在待取或已取件中。"
                  : "未找到匹配站点格式的取件码。")
              + (result.limited ? "仅检查了最新 500 条。" : "")
              + "可尝试分享或粘贴短信文字。");
      return;
    }
    String found = String.join("\n", result.codes);
    if (source.length() + found.length() + 1 > ImportPayload.MAX_TEXT) {
      showBatch();
      message("识别文字过长，请先清空批量录入内容后重试。");
      return;
    }
    source += (source.isEmpty() ? "" : "\n") + found;
    unchecked.clear();
    preview = true;
    showBatch();
    Toast.makeText(
            this,
            "已检查 " + result.checked + " 条短信，找到 " + result.codes.size() + " 个新取件码"
                + (result.skipped > 0 ? "，跳过 " + result.skipped + " 个已有或已取" : ""),
            Toast.LENGTH_LONG)
        .show();
    if (result.limited) message("最近三天短信较多，仅检查了最新 500 条。请核对结果，必要时使用分享或在批量录入中粘贴。");
  }

  private void hideKeyboard() {
    ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(body.getWindowToken(), 0);
  }

  private void chooseImage() {
    if (sourceInput != null) source = sourceInput.getText().toString();
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(i, IMAGE);
    } catch (RuntimeException e) {
      message("无法打开图片选择器，请粘贴短信文字。");
    }
  }

  private void startImages(List<Uri> uris) {
    if (uris.size() > ImportPayload.MAX_IMAGES) {
      message("每次最多识别 10 张图片，请分批选择。");
      return;
    }
    if (uris.isEmpty()) {
      message("没有收到图片，请重新选择。");
      return;
    }
    if (sourceInput != null) source = sourceInput.getText().toString();
    imageQueue.clear();
    for (Uri uri : uris) imageQueue.add(uri.toString());
    imageIndex = 0;
    imageFailures = 0;
    unchecked.clear();
    preview = false;
    busyImage = true;
    recognizeNext();
  }

  private void recognizeNext() {
    if (isFinishing() || isDestroyed()) return;
    if (imageIndex >= imageQueue.size()) {
      busyImage = false;
      imageUri = null;
      preview = true;
      showBatch();
      return;
    }
    busyImage = true;
    imageUri = imageQueue.get(imageIndex);
    showBatch();
    if (recognizer != null) recognizer.close();
    recognizer = new ScreenshotRecognizer();
    recognizer.read(
        this,
        Uri.parse(imageUri),
        text -> {
          if (isFinishing() || isDestroyed()) return;
          if (source.length() + text.length() + 1 > ImportPayload.MAX_TEXT) {
            busyImage = false;
            imageUri = null;
            preview = true;
            showBatch();
            message("合并文字超过 10 万字符，本张及后续图片未合并。已保留此前结果，请分批识别。");
            return;
          }
          if (text.trim().isEmpty()) imageFailures++;
          else source += (source.isEmpty() ? "" : "\n") + text;
          imageIndex++;
          recognizeNext();
        },
        error -> {
          if (isFinishing() || isDestroyed()) return;
          imageFailures++;
          imageIndex++;
          recognizeNext();
        });
  }

  private void showPickup() {
    List<Parcel> list = new ArrayList<>();
    for (Parcel p : store.all()) if (p.stationId == stationId) list.add(p);
    list.sort((a, b) -> CodeRules.compareCodes(a.code, b.code));
    String name = "取件站点";
    for (Station s : store.stations()) if (s.id == stationId) name = s.name;
    shell(name + " · 取件");
    if (list.isEmpty()) {
      currentId = -1;
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      TextView complete = label("本站已全部取完", 28, INK);
      complete.setGravity(Gravity.CENTER);
      complete.setPadding(dp(20), dp(40), dp(20), dp(40));
      complete.setBackground(AppStyle.surface(this, AppStyle.TINT, 26, false));
      AppStyle.icon(complete, R.drawable.ic_check_circle_outline, ACCENT, true);
      body.addView(complete);
      body.addView(label("记录已保存在“已取件”中，7 天内可恢复。", 15, MUTED));
      action(
          "查看已取件记录",
          () -> {
            mode = "history";
            render();
          });
      action("回到取件清单", this::finish);
      return;
    }
    int index = 0;
    for (int n = 0; n < list.size(); n++) if (list.get(n).id == currentId) index = n;
    Parcel item = list.get(index);
    currentId = item.id;
    final long nextId = list.get((index + 1) % list.size()).id;
    body.addView(
        label("还剩 " + list.size() + " 件 · 当前 " + (index + 1) + " / " + list.size(), 15, MUTED));
    gap();
    LinearLayout ticket = column();
    ticket.setPadding(dp(18), dp(20), dp(18), dp(24));
    ticket.setBackground(AppStyle.surface(this, Color.WHITE, 20, false));
    TextView ticketTitle = label("取件码", 14, MUTED);
    ticketTitle.setGravity(Gravity.CENTER);
    ticket.addView(ticketTitle);
    TextView big = label(item.code, item.code.length() > 10 ? 32 : 44, INK);
    big.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    big.setGravity(Gravity.CENTER);
    big.setPadding(0, dp(30), 0, dp(30));
    big.setId(R.id.focus_code);
    ticket.addView(big, new LinearLayout.LayoutParams(-1, -2));
    View divider = new View(this);
    divider.setBackgroundColor(AppStyle.LINE);
    ticket.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    TextView ticketNote = label("向工作人员出示 · 核对后标记已取", 12, MUTED);
    ticketNote.setGravity(Gravity.CENTER);
    ticketNote.setPadding(0, dp(18), 0, 0);
    ticket.addView(ticketNote);
    body.addView(ticket, new LinearLayout.LayoutParams(-1, -2));
    body.addView(
        label(
            ParcelAge.label(item.createdAt),
            15,
            ParcelAge.days(item.createdAt) >= store.reminderDays() ? ACCENT : MUTED));
    CheckBox awake = new CheckBox(this);
    awake.setText("取件时保持屏幕常亮");
    awake.setTextColor(MUTED);
    awake.setTextSize(14);
    awake.setPadding(0, dp(8), 0, dp(8));
    awake.setChecked(keepAwake);
    body.addView(awake);
    awake.setOnCheckedChangeListener(
        (b, checked) -> {
          keepAwake = checked;
          getPreferences(MODE_PRIVATE).edit().putBoolean("keepAwake", checked).apply();
          applyAwake();
        });
    applyAwake();
    primary(
        "已取，查看下一件",
        () -> {
          try {
            store.delete(item.id);
            body.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            currentId = nextId;
            showPickup();
          } catch (RuntimeException e) {
            message("标记失败，请重试。");
          }
        });
    action(
        "跳过，稍后取",
        () -> {
          currentId = nextId;
          showPickup();
        });
    body.addView(label("取件码按每段数字升序排列。取错可到已取件记录恢复。", 13, MUTED));
  }

  private void applyAwake() {
    if ("pickup".equals(mode) && keepAwake && currentId != -1)
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  private Spinner stationPicker(List<Station> stations, int selected) {
    List<String> names = new ArrayList<>();
    int index = 0;
    for (int n = 0; n < stations.size(); n++) {
      names.add(stations.get(n).name);
      if (stations.get(n).id == selected) index = n;
    }
    Spinner spinner = AppStyle.spinner(this,names.toArray(new String[0]));
    spinner.setSelection(index);
    spinner.setMinimumHeight(dp(52));
    return spinner;
  }

  private void showHistory() {
    LinearLayout root = shellRoot("已取件");
    TextView notice = label("保留最近 7 天，可恢复到待取列表。", 14, MUTED);
    notice.setPadding(dp(22), dp(8), dp(22), dp(14));
    root.addView(notice, new LinearLayout.LayoutParams(-1, -2));
    ScrollView scroll = new ScrollView(this);
    scroll.setId(R.id.history_scroll);
    scroll.setFillViewport(true);
    body = column();
    body.setPadding(dp(22), dp(4), dp(22), dp(12));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    List<HistoryEntry> history = store.history();
    if (history.isEmpty()) body.addView(label("最近还没有已取件记录", 20, INK));
    Map<Integer, Station> stations = new HashMap<>();
    for (Station station : store.stations()) stations.put(station.id, station);
    SimpleDateFormat f = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);
    for (HistoryEntry entry : history) {
      LinearLayout card = column();
      card.setPadding(dp(16), dp(10), dp(16), dp(10));
      LinearLayout metadata = new LinearLayout(this);
      metadata.setGravity(Gravity.CENTER_VERTICAL);
      ImageView icon = new ImageView(this);
      Station station = stations.get(entry.stationId);
      icon.setImageResource(station == null ? R.drawable.pixel_parcel
          : StationIcons.RESOURCES[StationIcons.indexOf(station.iconKey)]);
      icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      metadata.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
      TextView stationName = label(entry.stationName, 14, MUTED);
      stationName.setPadding(dp(8), 0, dp(8), 0);
      metadata.addView(stationName, new LinearLayout.LayoutParams(0, -2, 1));
      TextView time = label(f.format(new Date(entry.collectedAt)), 13, MUTED);
      time.setPadding(0, 0, 0, 0);
      metadata.addView(time, new LinearLayout.LayoutParams(-2, -2));
      card.addView(metadata, new LinearLayout.LayoutParams(-1, -2));
      View seam = new View(this);
      android.graphics.drawable.GradientDrawable dashed = new android.graphics.drawable.GradientDrawable();
      dashed.setShape(android.graphics.drawable.GradientDrawable.LINE);
      dashed.setStroke(dp(1), AppStyle.LINE, dp(3), dp(3));
      seam.setBackground(dashed);
      LinearLayout.LayoutParams seamParams = new LinearLayout.LayoutParams(-1, dp(2));
      seamParams.topMargin = dp(8);
      seamParams.bottomMargin = dp(8);
      card.addView(seam, seamParams);
      card.setBackground(AppStyle.receiptSurface(this, seam));
      LinearLayout details = new LinearLayout(this);
      details.setGravity(Gravity.CENTER_VERTICAL);
      TextView code = label(entry.code, 24, INK);
      code.setTypeface(Typeface.DEFAULT);
      code.setPadding(0, 0, dp(12), 0);
      code.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
      details.addView(code, new LinearLayout.LayoutParams(0, -2, 1));
      Button restore = AppStyle.button(this, "恢复", INK, AppStyle.TINT);
      restore.setContentDescription("恢复 " + entry.code);
      restore.setMinWidth(dp(80));
      restore.setMinimumWidth(dp(80));
      restore.setMinHeight(dp(40));
      restore.setMinimumHeight(dp(40));
      restore.setPadding(dp(12), dp(6), dp(12), dp(6));
      restore.setOnClickListener(v -> restoreHistory(entry));
      details.addView(restore, new LinearLayout.LayoutParams(-2, -2));
      card.addView(details, new LinearLayout.LayoutParams(-1, -2));
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
      if (body.getChildCount() > 0) cardParams.topMargin = dp(12);
      body.addView(card, cardParams);
    }
    if (!history.isEmpty()) {
      LinearLayout footer = column();
      footer.setId(R.id.history_footer);
      footer.setPadding(dp(22), dp(8), dp(22), dp(18));
      footer.setBackgroundColor(BG);
      Button clear = AppStyle.button(this, "清空已取件", Color.WHITE, AppStyle.PRIMARY);
      clear.setOnClickListener(v -> confirmClearHistory(history.size()));
      footer.addView(clear, new LinearLayout.LayoutParams(-1, -2));
      root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    }
    AppStyle.enter(body);
  }

  private void confirmClearHistory(int count) {
    dialog =
        new AlertDialog.Builder(this)
            .setTitle("清空已取件记录？")
            .setMessage("将永久删除这 " + count + " 条已取件记录，清空后无法恢复。待取件和站点设置不受影响。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认清空", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        store.clearHistory();
                        dialog.dismiss();
                        showHistory();
                      } catch (RuntimeException e) {
                        message("清空失败，请重试。");
                      }
                    }));
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void restoreHistory(HistoryEntry entry) {
    List<Station> stations = store.stations();
    if (stations.isEmpty()) {
      message("请先返回站点设置新增一个站点，再恢复记录。");
      return;
    }
    Spinner picker = stationPicker(stations, entry.stationId);
    LinearLayout box = column();
    box.setPadding(dp(22), dp(8), dp(22), dp(8));
    box.addView(label("选择恢复到哪个站点", 15, INK));
    box.addView(picker);
    dialog =
        new AlertDialog.Builder(this)
            .setTitle("恢复 " + entry.code)
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复到待取", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        store.restoreHistory(
                            entry.id, stations.get(picker.getSelectedItemPosition()).id);
                        dialog.dismiss();
                        showHistory();
                      } catch (SQLiteConstraintException e) {
                        message("此取件码已在待取列表中，历史记录保持不变。");
                      } catch (RuntimeException e) {
                        message("恢复失败，历史记录保持不变。请重新打开后重试。");
                      }
                    }));
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void showEdit() {
    Parcel parcel = null;
    for (Parcel p : store.all()) if (p.id == parcelId) parcel = p;
    if (parcelId != -1 && parcel == null) {
      shell("编辑取件码");
      body.addView(label("这条记录已不在待取列表中", 17, MUTED));
      return;
    }
    shell(parcel == null ? "手动录入" : "编辑取件码");
    body.addView(label("取件码", 15, INK));
    codeInput = input("数字和连字符，最多 32 个字符", false);
    codeInput.setId(R.id.edit_code);
    codeInput.setText(editDraft != null ? editDraft : parcel == null ? "" : parcel.code);
    body.addView(label("选择站点（可不符合该站点预设格式）", 14, MUTED));
    List<Station> stations = store.stations();
    if (stations.isEmpty()) {
      body.addView(label("请先返回站点设置新增站点。", 16, ACCENT));
      return;
    }
    stationInput =
        stationPicker(
            stations, editStation >= 0 ? editStation : parcel == null ? -1 : parcel.stationId);
    stationInput.setId(R.id.edit_station);
    body.addView(stationInput);
    TextView status = label("", 14, ACCENT);
    status.setId(R.id.edit_error);
    body.addView(status);
    primary(
        "保存取件码",
        () -> {
          try {
            int selected = stations.get(stationInput.getSelectedItemPosition()).id;
            if (parcelId < 0) store.addManual(codeInput.getText().toString(), selected);
            else store.updateParcel(parcelId, codeInput.getText().toString(), selected);
            finish();
          } catch (SQLiteConstraintException e) {
            status.setText("此取件码已在待取列表中");
          } catch (IllegalArgumentException e) {
            status.setText(e.getMessage());
          } catch (RuntimeException e) {
            status.setText("保存失败，请重试，原记录未更改。");
          }
        });
  }

  private Spinner appearancePicker(String title, String[] options, int selected) {
    body.addView(label(title, 15, INK));
    Spinner picker = AppStyle.spinner(this,options);
    picker.setContentDescription(title);
    picker.setSelection(selected);
    picker.setMinimumHeight(dp(48));
    body.addView(picker, new LinearLayout.LayoutParams(-1, -2));
    return picker;
  }

  private void showAppearanceSettings() {
    body.addView(label("首页显示", 19, INK));
    body.addView(label("选择后自动保存，返回首页生效。长码会按需换行。", 14, MUTED));
    CodeAppearance saved = CodeAppearance.read(this);
    Spinner size = appearancePicker("取件码字号", new String[] {"小", "标准", "大", "特大"}, saved.size);
    Spinner spacing = appearancePicker("取件码行距", new String[] {"紧凑", "标准", "宽松"}, saved.spacing);
    AdapterView.OnItemSelectedListener listener =
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            int a = size.getSelectedItemPosition(), b = spacing.getSelectedItemPosition();
            if (a >= 0 && b >= 0) CodeAppearance.save(FeaturesActivity.this, a, b);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        };
    size.setOnItemSelectedListener(listener);
    spacing.setOnItemSelectedListener(listener);
    gap();
  }

  private void showGuide() {
    LinearLayout root = shellRoot("使用引导");
    guidePage = Math.max(0, Math.min(guidePage, GuideContent.COUNT - 1));
    GuideContent.Page page = GuideContent.page(guidePage);

    ScrollView contentScroll = new ScrollView(this);
    contentScroll.setId(R.id.guide_content_scroll);
    contentScroll.setFillViewport(true);
    contentScroll.setClipToPadding(false);
    body = column();
    body.setPadding(dp(22), dp(8), dp(22), dp(12));
    contentScroll.addView(body);
    root.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));

    LinearLayout card = column();
    card.setGravity(Gravity.CENTER_HORIZONTAL);
    card.setPadding(dp(20), dp(20), dp(20), dp(20));
    card.setBackground(AppStyle.surface(this, Color.WHITE, 20, false));
    ImageView illustration = new ImageView(this);
    illustration.setImageResource(page.image);
    illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(illustration, new LinearLayout.LayoutParams(dp(76), dp(76)));
    TextView heading = label(page.title, 24, INK);
    heading.setGravity(Gravity.CENTER);
    heading.setTypeface(AppStyle.pixel(this));
    heading.getPaint().setFakeBoldText(true);
    LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
    headingParams.topMargin = dp(14);
    card.addView(heading, headingParams);
    TextView copy = label(page.lead, 16, MUTED);
    copy.setGravity(Gravity.CENTER);
    copy.setLineSpacing(dp(4), 1f);
    LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
    copyParams.topMargin = dp(8);
    card.addView(copy, copyParams);

    for (String item : page.items) {
      String[] parts = item.split("\\n", 2);
      LinearLayout itemCard = column();
      itemCard.setPadding(dp(14), dp(9), dp(14), dp(9));
      itemCard.setBackground(AppStyle.surface(this, AppStyle.TINT, 8, false));
      TextView itemTitle = label(parts[0], 16, INK);
      itemTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      itemCard.addView(itemTitle, new LinearLayout.LayoutParams(-1, -2));
      if (parts.length > 1) itemCard.addView(label(parts[1], 14, MUTED));
      LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
      itemParams.topMargin = dp(9);
      card.addView(itemCard, itemParams);
    }
    if (!page.note.isEmpty()) {
      TextView note = label(page.note, 14, MUTED);
      note.setLineSpacing(dp(3), 1f);
      LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
      noteParams.topMargin = dp(10);
      card.addView(note, noteParams);
    }
    body.addView(card, new LinearLayout.LayoutParams(-1, -2));

    LinearLayout footer = column();
    footer.setId(R.id.guide_footer);
    footer.setPadding(dp(22), dp(4), dp(22), dp(18));
    footer.setBackgroundColor(BG);

    TextView progress = label((guidePage + 1) + " / " + GuideContent.COUNT, 14, MUTED);
    progress.setGravity(Gravity.CENTER);
    progress.setPadding(0, 0, 0, dp(2));
    footer.addView(progress, new LinearLayout.LayoutParams(-1, -2));

    LinearLayout navigation = new LinearLayout(this);
    Button previous = AppStyle.button(this, "上一步", INK, Color.WHITE);
    previous.setEnabled(guidePage > 0);
    previous.setOnClickListener(v -> showGuidePage(guidePage - 1));
    navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
    Button next =
        AppStyle.button(
            this, guidePage == GuideContent.COUNT - 1 ? "开始使用" : "下一步", Color.WHITE, AppStyle.PRIMARY);
    next.setOnClickListener(
        v -> {
          if (guidePage == GuideContent.COUNT - 1) finishGuide();
          else showGuidePage(guidePage + 1);
        });
    LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, -2, 1);
    nextParams.leftMargin = dp(10);
    navigation.addView(next, nextParams);
    LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(-1, -2);
    navigationParams.topMargin = dp(6);
    footer.addView(navigation, navigationParams);
    Button skip = AppStyle.button(this, "跳过引导", INK, Color.WHITE);
    skip.setOnClickListener(v -> finishGuide());
    LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(-1, -2);
    skipParams.topMargin = dp(8);
    footer.addView(skip, skipParams);
    root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    AppStyle.enter(body);
  }

  private void showGuidePage(int page) {
    guidePage = Math.max(0, Math.min(page, GuideContent.COUNT - 1));
    render();
  }

  private void finishGuide() {
    Onboarding.complete(this);
    finish();
  }

  private void showMore() {
    shell("更多设置");
    showAppearanceSettings();
    body.addView(label("放置天数提醒", 19, INK));
    body.addView(label("达到设定天数，在清单中突出显示。仅在应用内提醒。", 14, MUTED));
    NumberPicker days = new NumberPicker(this);
    days.setMinValue(1);
    days.setMaxValue(30);
    days.setValue(reminderDraft > 0 ? reminderDraft : store.reminderDays());
    reminderInput = days;
    days.setWrapSelectorWheel(false);
    LinearLayout dayRow = new LinearLayout(this);
    dayRow.setGravity(Gravity.CENTER);
    dayRow.setBackground(AppStyle.surface(this, Color.WHITE, 16, false));
    dayRow.addView(days, new LinearLayout.LayoutParams(dp(90), dp(138)));
    dayRow.addView(label("天后提醒取件", 16, INK));
    body.addView(dayRow, new LinearLayout.LayoutParams(-1, -2));
    primary(
        "保存提醒天数",
        () -> {
          days.clearFocus();
          store.setReminderDays(days.getValue());
          reminderDraft = days.getValue();
          Toast.makeText(this, "已设为 " + days.getValue() + " 天", Toast.LENGTH_SHORT).show();
        });
    gap();
    body.addView(label("桌面小部件", 19, INK));
    body.addView(label("4×4 尺寸，可查看取件码、逐件完成或一键取出全部。", 14, MUTED));
    primary("添加到桌面", this::showWidgetPreview);
    gap();
    body.addView(label("帮助", 19, INK));
    body.addView(label("查看顺手取的完整使用引导。", 14, MUTED));
    primary(
        "使用引导",
        () -> {
          guidePage = 0;
          mode = "guide";
          render();
        });
  }

  private void showWidgetPreview() {
    FrameLayout frame = new FrameLayout(this);
    frame.setPadding(dp(12), dp(8), dp(12), dp(8));
    View preview = getLayoutInflater().inflate(R.layout.pickup_widget_preview, frame, false);
    FrameLayout.LayoutParams previewParams =
        new FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER);
    frame.addView(preview, previewParams);
    dialog = new AlertDialog.Builder(this)
        .setTitle("预览桌面小部件")
        .setView(frame)
        .setNegativeButton("取消", null)
        .setPositiveButton("添加到桌面", (d, w) -> pinWidget())
        .create();
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private void pinWidget() {
    if (WidgetSupport.requestPin(this)) {
      Toast.makeText(this, "已向桌面发送添加请求", Toast.LENGTH_SHORT).show();
      return;
    }
    dialog = new AlertDialog.Builder(this)
        .setTitle("添加桌面小部件")
        .setMessage(WidgetSupport.manualInstructions())
        .setPositiveButton("知道了", null)
        .create();
    dialog.show();
    AppStyle.dialog(dialog);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    if (request == IMAGE) {
      try {
        startImages(ImportPayload.images(data));
      } catch (RuntimeException e) {
        message(e instanceof IllegalArgumentException ? e.getMessage() : "无法读取所选图片，请重试。");
      }
      return;
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(request, permissions, grantResults);
    if (request != READ_SMS) return;
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
      startSmsScan();
    else
      message("未获得短信读取权限。可继续使用分享、粘贴或截图录入；若系统不允许授权，请检查安装方式和权限设置。");
  }

  @Override
  protected void onResume() {
    super.onResume();
    applyAwake();
  }

  @Override
  protected void onPause() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    super.onPause();
  }

  @Override
  public void onBackPressed() {
    if ("guide".equals(mode)) Onboarding.complete(this);
    super.onBackPressed();
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (reminderInput != null) {
      reminderInput.clearFocus();
      state.putInt("reminderDraft", reminderInput.getValue());
    }
    state.putString("mode", mode);
    state.putString("source", sourceInput != null ? sourceInput.getText().toString() : source);
    state.putBoolean("preview", preview);
    state.putStringArrayList("unchecked", new ArrayList<>(unchecked));
    state.putLong("currentId", currentId);
    state.putBoolean("keepAwake", keepAwake);
    state.putString("imageUri", imageUri);
    state.putStringArrayList("imageQueue", imageQueue);
    state.putInt("imageIndex", imageIndex);
    state.putInt("imageFailures", imageFailures);
    state.putInt("guidePage", guidePage);
    state.putBoolean("busyImage", busyImage);
    if (codeInput != null) state.putString("editDraft", codeInput.getText().toString());
    if (stationInput != null) {
      List<Station> stations = store.stations();
      int selected = stationInput.getSelectedItemPosition();
      if (selected >= 0 && selected < stations.size())
        state.putInt("editStation", stations.get(selected).id);
    }
  }

  @Override
  public void finish() {
    if (getIntent().getBooleanExtra("externalEntry", false) && isTaskRoot())
      startActivity(new Intent(this, MainActivity.class));
    super.finish();
  }

  @Override
  protected void onDestroy() {
    if (recognizer != null) recognizer.close();
    if (dialog != null) dialog.dismiss();
    store.close();
    super.onDestroy();
  }
}
