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
        assertEquals(3,context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode);
        try(Store store=new Store(context)) {
            ContentValues note=store.query("SELECT * FROM notes WHERE title=?","ทบทวนบทเรียน").get(0);
            assertTrue(note.getAsString("body").contains("สรุปจากเอกสาร"));
            String id=store.attachments(note.getAsLong("id")).get(0).getAsString("id");assertTrue(new File(context.getFilesDir(),id).length()>0);
            assertEquals("จดจำโครงสร้างเซลล์",store.page(id,0).getAsString("body"));assertEquals(1,(int)store.page(id,0).getAsInteger("bookmarked"));
        }
    }
}
