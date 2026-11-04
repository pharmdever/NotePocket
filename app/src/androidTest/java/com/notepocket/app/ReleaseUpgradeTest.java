package com.notepocket.app;
import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ReleaseUpgradeTest {
    @Test public void signedReleaseUpgradeKeepsPreviousUserData() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals(4,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
        try(Store store=new Store(c)) {
            assertEquals("original note",store.note(77).getAsString("body"));assertEquals("เรียน",store.note(77).getAsString("folder"));assertEquals(1,(int)store.note(77).getAsInteger("pinned"));
            assertEquals("release-upgrade-doc",store.attachments(77).get(0).getAsString("id"));assertEquals(6,new File(c.getFilesDir(),"release-upgrade-doc").length());
            assertEquals("original page note",store.page("release-upgrade-doc",0).getAsString("body"));assertEquals(1,Ink.decode(store.drawing("release-upgrade-doc",0)).size());assertEquals("[]",store.textBoxes("release-upgrade-doc",0));assertEquals(4,store.getReadableDatabase().getVersion());
        }
    }
}
