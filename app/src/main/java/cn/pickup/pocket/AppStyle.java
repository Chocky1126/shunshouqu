package cn.pickup.pocket;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

/** Shared visual language for the offline native screens; no data or navigation state. */
final class AppStyle {
  static final int BG = 0xFFFFF6DA, INK = 0xFF33291E, MUTED = 0xFF76664F;
  static final int ACCENT = 0xFFB34B16, LINE = 0xFFE5D8B9, TINT = 0xFFFFEDB9;

  private AppStyle() {}

  static int dp(Context c, float n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable surface(Context c, int color, int radius, boolean border) {
    return new PixelSurface(c, color == Color.WHITE ? PAPER : color, border);
  }

  static final int PAPER = 0xFFFFFCF1;
  private static Typeface pixelFont;
  static Typeface pixel(Context c) {
    if (pixelFont == null) pixelFont = c.getResources().getFont(R.font.pixel);
    return pixelFont;
  }

  /** Stepped native UI border; scales with the view and remains sharp at each density. */
  private static final class PixelSurface extends GradientDrawable {
    private final android.graphics.Paint paint = new android.graphics.Paint();
    private final android.graphics.Path path = new android.graphics.Path();
    private final float step;
    private boolean border;
    private int strokeColor = INK;
    private float strokeWidth;
    PixelSurface(Context c, int color, boolean border) {
      step = dp(c, 3); this.border = border; strokeWidth=step*2/3; setColor(color);
    }
    @Override public void setStroke(int width, int color) {
      border = true; strokeColor = color; strokeWidth = width;
    }
    @Override public void draw(android.graphics.Canvas canvas) {
      android.graphics.Rect b = getBounds();
      float inset = border ? step / 3 : 0;
      float l=b.left+inset, t=b.top+inset, r=b.right-inset, d=b.bottom-inset, k=step;
      path.reset();
      path.moveTo(l+2*k,t); path.lineTo(r-2*k,t); path.lineTo(r-2*k,t+k);
      path.lineTo(r-k,t+k); path.lineTo(r-k,t+2*k); path.lineTo(r,t+2*k);
      path.lineTo(r,d-2*k); path.lineTo(r-k,d-2*k); path.lineTo(r-k,d-k);
      path.lineTo(r-2*k,d-k); path.lineTo(r-2*k,d); path.lineTo(l+2*k,d);
      path.lineTo(l+2*k,d-k); path.lineTo(l+k,d-k); path.lineTo(l+k,d-2*k);
      path.lineTo(l,d-2*k); path.lineTo(l,t+2*k); path.lineTo(l+k,t+2*k);
      path.lineTo(l+k,t+k); path.lineTo(l+2*k,t+k); path.close();
      paint.setColor(getColor() == null ? PAPER : getColor().getDefaultColor());
      paint.setStyle(android.graphics.Paint.Style.FILL); canvas.drawPath(path,paint);
      if (border) {
        paint.setColor(strokeColor); paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth); canvas.drawPath(path,paint);
      }
    }
  }

  static RippleDrawable ripple(Context c, int color, int radius) {
    return new RippleDrawable(
        ColorStateList.valueOf(color == INK || color == ACCENT ? 0x30FFFFFF : 0x1833291E),
        surface(c, color, radius, color != Color.TRANSPARENT),
        surface(c, Color.WHITE, radius, false));
  }

  static void styleButton(Button b, int foreground, int background) {
    Context c = b.getContext();
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    b.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {0xFF9B9283, foreground}));
    b.setBackground(ripple(c, background, 6));
    b.setMinHeight(dp(c, 50));
    b.setMinimumHeight(dp(c, 50));
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
    b.setStateListAnimator(null);
    if (ValueAnimator.areAnimatorsEnabled()) {
      StateListAnimator states = new StateListAnimator();
      states.addState(
          new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled}, scale(b, .985f));
      states.addState(new int[] {}, scale(b, 1f));
      b.setStateListAnimator(states);
    }
  }

  private static AnimatorSet scale(View v, float scale) {
    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(v, View.SCALE_X, scale),
        ObjectAnimator.ofFloat(v, View.SCALE_Y, scale));
    set.setDuration(120);
    return set;
  }

  static Button button(Context c, String label, int foreground, int background) {
    Button b = new Button(c);
    b.setText(label);
    styleButton(b, foreground, background);
    return b;
  }

  static void icon(TextView v, int resource, int color, boolean above) {
    Drawable icon = v.getContext().getDrawable(resource).mutate();
    icon.setTint(color);
    icon.setBounds(0, 0, dp(v.getContext(), 23), dp(v.getContext(), 23));
    v.setCompoundDrawablePadding(dp(v.getContext(), above ? 9 : 8));
    v.setCompoundDrawablesRelative(above ? null : icon, above ? icon : null, null, null);
  }

  static void input(TextView v) {
    Context c = v.getContext();
    StateListDrawable backgrounds = new StateListDrawable();
    GradientDrawable focused = surface(c, Color.WHITE, 16, false);
    focused.setStroke(dp(c, 2), ACCENT);
    backgrounds.addState(new int[] {android.R.attr.state_focused}, focused);
    backgrounds.addState(new int[] {}, surface(c, Color.WHITE, 16, true));
    v.setBackground(backgrounds);
    v.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
    v.setHighlightColor(TINT);
  }

  static void enter(View v) {
    if (!ValueAnimator.areAnimatorsEnabled()) return;
    v.setAlpha(0f);
    v.setTranslationY(dp(v.getContext(), 8));
    v.animate()
        .alpha(1f)
        .translationY(0)
        .setDuration(220)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  static void dialog(AlertDialog dialog) {
    if (dialog.getWindow() == null) return;
    dialog.getWindow().setBackgroundDrawable(surface(dialog.getContext(), PAPER, 6, true));
    for (int which :
        new int[] {
          AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
      Button b = dialog.getButton(which);
      if (b != null) {
        b.setAllCaps(false);
        b.setTextColor(which == AlertDialog.BUTTON_POSITIVE ? ACCENT : MUTED);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      }
    }
  }
}
