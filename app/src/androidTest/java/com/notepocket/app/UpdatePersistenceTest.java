package com.notepocket.app;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/** Run after UiFlowTest, then reinstall the APK using adb install -r. */
@RunWith(AndroidJUnit4.class)
public class UpdatePersistenceTest {
    @Test public void notesFilesAndPageNotesSurviveApkReinstallation() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(12,context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode);
        try(Store store=new Store(context)) {
            assertFalse(store.query("SELECT * FROM folders WHERE name=?","empty-upgrade-folder").isEmpty());
            ContentValues editorNote=store.query("SELECT * FROM notes WHERE title=?","Note editor upgrade fixture").get(0);
            assertEquals("ตารางก่อนอัปเดต",NoteTable.decode(editorNote.getAsString("tables")).get(0).cells.get(0).get(0));assertEquals(240,NoteTable.decode(editorNote.getAsString("tables")).get(0).width(0));assertEquals("ข้อความ SOAP ก่อนอัปเดต",new SoapDraft(editorNote.getAsString("soap")).get("s"));android.text.SpannableStringBuilder rich=NoteFormat.decode(editorNote.getAsString("rich"),editorNote.getAsString("body"));assertEquals(26,rich.getSpans(0,rich.length(),android.text.style.AbsoluteSizeSpan.class)[0].getSize());assertEquals(0xffD12D39,rich.getSpans(0,rich.length(),android.text.style.ForegroundColorSpan.class)[0].getForegroundColor());
            ContentValues note=store.query("SELECT * FROM notes WHERE title=?","ทบทวนบทเรียน").get(0);
            assertTrue(note.getAsString("body").contains("สรุปจากเอกสาร"));
            String id=store.attachments(note.getAsLong("id")).get(0).getAsString("id");assertTrue(new File(context.getFilesDir(),id).length()>0);
            assertTrue(Ink.decode(store.drawing(id,0)).size()>=2);assertTrue(store.textBoxes(id,0).contains("ข้อความใหม่บน PDF"));
            assertEquals("จดจำโครงสร้างเซลล์",store.page(id,0).getAsString("body"));assertEquals(1,(int)store.page(id,0).getAsInteger("bookmarked"));
        }
    }
}
