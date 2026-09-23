package cn.pickup.pocket;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/** A horizontal dismiss gesture that yields vertical movement to the parent scroll view. */
public final class SwipeCard extends FrameLayout {
  private final View foreground;
  private final TextView hint;
  private final Runnable onDismiss;
  private final int slop;
  private float startX, startY;
  private boolean dragging, vertical, dismissing;

  public SwipeCard(Context context, View foreground, Runnable onDismiss) {
    super(context);
    this.foreground = foreground;
    this.onDismiss = onDismiss;
    slop = ViewConfiguration.get(context).getScaledTouchSlop();
    GradientDrawable background = new GradientDrawable();
    background.setColor(AppStyle.ACCENT);
    background.setCornerRadius(0);
    setBackground(background);
    setClipToOutline(true);
    hint = new TextView(context);
    hint.setAlpha(0f);
    hint.setText("取件完成");
    hint.setTextColor(Color.WHITE);
    hint.setTextSize(15);
    hint.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    hint.setPadding(dp(16), 0, dp(20), 0);
    hint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(hint, new LayoutParams(-1, -1));
    addView(foreground, new LayoutParams(-1, -2));
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void begin(MotionEvent event) {
    startX = event.getX();
    startY = event.getY();
    dragging = false;
    vertical = false;
    foreground.animate().cancel();
    hint.animate().cancel();
  }

  private void detect(MotionEvent event) {
    float dx = event.getX() - startX, dy = event.getY() - startY;
    if (!dragging && !vertical) {
      if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) vertical = true;
      else if (dx < -slop && -dx > Math.abs(dy) * 1.2f) {
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
      }
    }
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    if (dismissing) return true;
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) begin(event);
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) detect(event);
    return dragging;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (dismissing) return true;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        begin(event);
        return true;
      case MotionEvent.ACTION_MOVE:
        detect(event);
        if (dragging) {
          foreground.setTranslationX(Math.max(-getWidth(), Math.min(0, event.getX() - startX)));
          hint.setAlpha(Math.min(1f, -foreground.getTranslationX() / dp(100)));
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (dragging && foreground.getTranslationX() < -Math.min(getWidth() * .3f, dp(120))) {
          dismissing = true;
          if (!android.animation.ValueAnimator.areAnimatorsEnabled()) onDismiss.run();
          else
            foreground
                .animate()
                .translationX(-getWidth())
                .setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(onDismiss)
                .start();
        } else {
          reset();
          if (!dragging && !vertical) performClick();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        reset();
        return true;
      default:
        return true;
    }
  }

  public void reset() {
    dismissing = false;
    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
      foreground.setTranslationX(0);
      hint.setAlpha(0);
    } else {
      foreground
          .animate()
          .translationX(0)
          .setDuration(220)
          .setInterpolator(new android.view.animation.DecelerateInterpolator())
          .start();
      hint.animate().alpha(0).setDuration(180).start();
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
