package cn.pickup.pocket;

import android.content.ClipData;
import android.os.PersistableBundle;

final class ClipboardFixtures {
  static ClipData text(String label, String text) {
    ClipData clip = ClipData.newPlainText(label, text);
    PersistableBundle extras = new PersistableBundle();
    // AOSP ClipboardListener supports this for emulator clipboard synchronization.
    // Prevent asynchronous SYSTEM floating windows from intercepting later Espresso clicks.
    // This flag is only in test fixtures; actual user copying is verified separately.
    extras.putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true);
    clip.getDescription().setExtras(extras);
    return clip;
  }
}
