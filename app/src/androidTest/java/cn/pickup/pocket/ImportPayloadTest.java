package cn.pickup.pocket;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImportPayloadTest {
  @Test
  public void acceptsTextWithoutSaving() {
    ImportPayload p =
        ImportPayload.share(
            new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "取件 2-1-003"));
    assertEquals("取件 2-1-003", p.text);
    assertTrue(p.images.isEmpty());
  }

  @Test
  public void imagesDeduplicateAcrossStreamAndClip() {
    Uri a = Uri.parse("content://fixture/a"), b = Uri.parse("content://fixture/b");
    Intent i =
        new Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/png")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM, new ArrayList<>(Arrays.asList(a, b, a)));
    i.setClipData(new ClipData("images", new String[] {"image/png"}, new ClipData.Item(a)));
    assertEquals(Arrays.asList(a, b), ImportPayload.share(i).images);
  }

  @Test
  public void rejectsUnsupportedAction() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ImportPayload.share(new Intent(Intent.ACTION_VIEW).setType("text/plain")));
  }

  @Test
  public void rejectsFileAndOverLimit() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ImportPayload.share(
                new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///data/private"))));
    ArrayList<Uri> uris = new ArrayList<>();
    for (int n = 0; n < 11; n++) uris.add(Uri.parse("content://fixture/" + n));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ImportPayload.share(
                new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("image/png")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)));
  }

  @Test
  public void rejectsOversizedText() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ImportPayload.share(
                new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "1".repeat(100001))));
  }

  @Test
  public void pickerSupportsDataAndClip() {
    Uri a = Uri.parse("content://fixture/a"), b = Uri.parse("content://fixture/b");
    Intent i = new Intent().setData(a);
    i.setClipData(new ClipData("images", new String[] {"image/png"}, new ClipData.Item(b)));
    assertEquals(Arrays.asList(a, b), ImportPayload.images(i));
  }
}
