package cn.pickup.pocket;

import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;

/** Prevents unrelated UI tests from being intercepted by the first-install guide. */
public final class PickupTestRunner extends AndroidJUnitRunner {
  @Override
  public void onStart() {
    Context context = getTargetContext();
    context
        .getSharedPreferences(Onboarding.PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt("seen_version", Onboarding.VERSION)
        .commit();
    super.onStart();
  }
}
