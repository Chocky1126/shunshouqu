package cn.pickup.pocket;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
public class ScreenshotRecognitionTest {
 @Test public void bundledModelReadsChineseReceiptWithoutInternetPermission() throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,c.checkSelfPermission("android.permission.INTERNET"));
  Bitmap image=Bitmap.createBitmap(1080,1200,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(58);
  canvas.drawText("快递已到站，请凭码取件",70,170,p);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(82);canvas.drawText("2-1-003",70,370,p);canvas.drawText("001234",70,570,p);canvas.drawText("1-2-0034",70,770,p);
  File file=new File(c.getCacheDir(),"recognition-test.png");try(OutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>(),error=new AtomicReference<>();
  try(ScreenshotRecognizer r=new ScreenshotRecognizer()){
   r.read(c,Uri.fromFile(file),s->{result.set(s);latch.countDown();},s->{error.set(s);latch.countDown();});
   assertTrue("OCR timed out",latch.await(45,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(result.get());
   try(ParcelStore store=new ParcelStore(c)){java.util.List<String> codes=BatchParser.extract(result.get(),store.stations());assertTrue(result.get(),codes.contains("2-1-003"));assertTrue(result.get(),codes.contains("001234"));assertTrue(result.get(),codes.contains("1-2-0034"));}
  }
 }
}
