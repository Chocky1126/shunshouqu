package cn.pickup.pocket;
import static org.junit.Assert.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
public class AppearanceLayoutTest {
 @Test public void allPresetsKeepFullCodesAtLargeSystemFontScale() {
  Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Configuration config=new Configuration(base.getResources().getConfiguration());config.fontScale=1.5f;
   Context c=base.createConfigurationContext(config);
   for(String value:new String[]{"12-3-0045","12345678901234567890123456789012"})
    for(int size=0;size<4;size++)for(int gap=0;gap<3;gap++) {
     TextView text=new TextView(c);text.setText(value);text.setTypeface(Typeface.DEFAULT_BOLD);
     new CodeAppearance(size,gap).apply(text);
     int width=AppStyle.dp(c,150);
     for(int pass=0;pass<3;pass++) {
      // Detached views have no parent traversal to consume requestLayout after padding changes.
      text.forceLayout();
      text.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
      text.layout(0,0,width,text.getMeasuredHeight());
     }
     android.text.Layout layout=text.getLayout();assertNotNull(layout);
     assertEquals(value.length(),layout.getLineEnd(layout.getLineCount()-1));
     assertTrue(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
     for(int line=0;line<layout.getLineCount();line++) {
      assertEquals(0,layout.getEllipsisCount(line));
      assertTrue(layout.getLineWidth(line)<=width-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1);
     }
    }
  });
 }
}
