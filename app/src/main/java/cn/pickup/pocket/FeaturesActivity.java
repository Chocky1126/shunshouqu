package cn.pickup.pocket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
  private ParcelStore store;
  private LinearLayout body;
  private String mode, source = "", imageUri;
  private boolean preview, keepAwake, busyImage;
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
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private GradientDrawable shape(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(3));
    return d;
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
    AppStyle.styleButton(b, Color.WHITE, ACCENT);
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
    String note = getIntent().getStringExtra("clipboardNote");
    if (note != null && !note.isEmpty()) body.addView(label(note, 13, MUTED));
    sourceInput = input("粘贴短信全文，或每行一个取件码", true);
    sourceInput.setId(R.id.batch_source);
    sourceInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100000)});
    sourceInput.setText(source);
    sourceInput.setEnabled(!busyImage);
    Button paste =
        action(
            "读取剪贴板",
            () -> {
              ClipboardImport.Result result = ClipboardImport.read(this, store, true);
              if (result.codes == null) {
                message(result.message);
                return;
              }
              source = sourceInput.getText().toString();
              if (source.length() + result.codes.length() + 1 > ImportPayload.MAX_TEXT) {
                message("文字过长，请分批录入。");
                return;
              }
              source += (source.isEmpty() ? "" : "\n") + result.codes;
              unchecked.clear();
              preview = true;
              getIntent().removeExtra("clipboardNote");
              hideKeyboard();
              showBatch();
            });
    paste.setEnabled(!busyImage);
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
    AppStyle.styleButton(analyze, Color.WHITE, ACCENT);
    analyze.setEnabled(!busyImage);
    Button image =
        action(
            busyImage ? "正在识别第 " + (imageIndex + 1) + " / " + imageQueue.size() + " 张…" : "选择截图识别",
            this::chooseImage);
    image.setEnabled(!busyImage);
    body.addView(label("支持一次选择最多 10 张，合并识别后核对保存。", 12, MUTED));
    if (imageFailures > 0)
      body.addView(label(imageFailures + " 张图片未能读取，其余结果已保留，可重新选择失败图片。", 13, ACCENT));
    if (busyImage && android.animation.ValueAnimator.areAnimatorsEnabled()) {
      ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      progress.setIndeterminate(true);
      body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
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
            ParcelAge.days(item.createdAt) >= store.reminderDays() ? 0xFFC24B19 : MUTED));
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
    Spinner spinner = new Spinner(this);
    List<String> names = new ArrayList<>();
    int index = 0;
    for (int n = 0; n < stations.size(); n++) {
      names.add(stations.get(n).name);
      if (stations.get(n).id == selected) index = n;
    }
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    spinner.setSelection(index);
    spinner.setMinimumHeight(dp(52));
    return spinner;
  }

  private void showHistory() {
    shell("已取件");
    body.addView(label("保留最近 7 天，可恢复到待取列表。", 14, MUTED));
    List<HistoryEntry> history = store.history();
    if (history.isEmpty()) body.addView(label("最近还没有已取件记录", 20, INK));
    if (!history.isEmpty()) action("清空已取件", () -> confirmClearHistory(history.size()));
    SimpleDateFormat f = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);
    for (HistoryEntry entry : history) {
      gap();
      TextView code = label(entry.code, 24, INK);
      code.setTypeface(Typeface.MONOSPACE);
      LinearLayout card = column();
      card.setPadding(dp(18), dp(14), dp(18), dp(14));
      card.setBackground(AppStyle.surface(this, Color.WHITE, 16, false));
      card.addView(code);
      card.addView(
          label(
              entry.stationName + " · " + f.format(new Date(entry.collectedAt)) + " 已取",
              13,
              MUTED));
      Button restore = AppStyle.button(this, "恢复 " + entry.code, INK, AppStyle.TINT);
      restore.setOnClickListener(v -> restoreHistory(entry));
      LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, -2);
      restoreParams.topMargin = dp(10);
      card.addView(restore, restoreParams);
      body.addView(card);
    }
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

  private void showClipboardHelp() {
    dialog =
        new AlertDialog.Builder(this)
            .setTitle("ColorOS 剪贴板读取")
            .setMessage(
                "若自动识别没有出现，请使用主页菜单的“粘贴录入”，或长按录入框选择系统“粘贴”。\n\n"
                    + "也可在系统设置中搜索“剪贴板”，检查顺手取的读取权限。应用不能替你开启系统权限。\n\n"
                    + "点击忽略后，同一组取件码不再提示；系统标记的敏感内容会跳过。")
            .setPositiveButton(
                "应用设置",
                (d, w) ->
                    startActivity(
                        new Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("知道了", null)
            .create();
    dialog.show();
    AppStyle.dialog(dialog);
  }

  private Spinner appearancePicker(String title, String[] options, int selected) {
    body.addView(label(title, 15, INK));
    Spinner picker = new Spinner(this);
    picker.setContentDescription(title);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    picker.setAdapter(adapter);
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
            this, guidePage == GuideContent.COUNT - 1 ? "开始使用" : "下一步", Color.WHITE, ACCENT);
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
    body.addView(label("帮助", 19, INK));
    action(
        "使用引导",
        () -> {
          guidePage = 0;
          mode = "guide";
          render();
        });
    gap();
    showAppearanceSettings();
    body.addView(label("剪贴板自动识别", 19, INK));
    Switch clipboard = new Switch(this);
    clipboard.setText("打开应用时识别剪贴板");
    clipboard.setTextColor(INK);
    clipboard.setMinHeight(dp(52));
    clipboard.setChecked(ClipboardImport.enabled(this));
    clipboard.setOnCheckedChangeListener(
        (button, checked) ->
            ClipboardImport.prefs(this).edit().putBoolean("clipboard_enabled", checked).apply());
    body.addView(clipboard);
    body.addView(label("仅主页显示时读取，发现新取件码后提示核对，不会自动保存。", 14, MUTED));
    action("剪贴板读取帮助", this::showClipboardHelp);
    gap();
    body.addView(label("桌面小部件", 19, INK));
    body.addView(label("4×4 尺寸，可直接查看取件码并标记完成。", 14, MUTED));
    action(
        "添加到桌面",
        () -> {
          if (WidgetSupport.requestPin(this)) {
            Toast.makeText(this, "已向桌面发送添加请求", Toast.LENGTH_SHORT).show();
          } else {
            new AlertDialog.Builder(this)
                .setTitle("添加桌面小部件")
                .setMessage(WidgetSupport.manualInstructions())
                .setPositiveButton("知道了", null)
                .show();
          }
        });
    gap();
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
    action(
        "手动指定站点录入",
        () -> {
          mode = "edit";
          parcelId = -1;
          render();
        });
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
