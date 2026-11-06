package com.notepocket.app;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** This instrumentation is run against the signed 0.6 APK before installing 0.7. */
@RunWith(AndroidJUnit4.class)
public class LegacyUpgradeSeedTest {
    @Test public void seedOriginalReleaseSchema() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals(6,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
        c.deleteDatabase("notepocket.db");SQLiteDatabase db=c.openOrCreateDatabase("notepocket.db",0,null);
        try {
            db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,updated INTEGER NOT NULL,rich TEXT NOT NULL DEFAULT '',folder TEXT NOT NULL DEFAULT 'ทั่วไป',pinned INTEGER NOT NULL DEFAULT 0,deleted INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE docs(id TEXT PRIMARY KEY,name TEXT NOT NULL,mime TEXT NOT NULL,size INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE note_docs(note_id INTEGER NOT NULL,doc_id TEXT NOT NULL,PRIMARY KEY(note_id,doc_id))");
            db.execSQL("CREATE TABLE page_notes(doc_id TEXT NOT NULL,page INTEGER NOT NULL,body TEXT NOT NULL,bookmarked INTEGER NOT NULL,PRIMARY KEY(doc_id,page))");
            db.execSQL("CREATE TABLE reading(doc_id TEXT PRIMARY KEY,page INTEGER NOT NULL)");
            db.execSQL("INSERT INTO notes VALUES(77,'keep on upgrade','original note',123,'','เรียน',1,0)");
            db.execSQL("INSERT INTO docs VALUES('release-upgrade-doc','original.pdf','application/pdf',6)");
            db.execSQL("INSERT INTO note_docs VALUES(77,'release-upgrade-doc')");db.execSQL("INSERT INTO page_notes VALUES('release-upgrade-doc',0,'original page note',1)");db.execSQL("INSERT INTO reading VALUES('release-upgrade-doc',0)");db.execSQL("CREATE TABLE page_ink(doc_id TEXT NOT NULL,page INTEGER NOT NULL,drawing TEXT NOT NULL,PRIMARY KEY(doc_id,page))");
            ContentValues ink=new ContentValues();ink.put("doc_id","release-upgrade-doc");ink.put("page",0);ink.put("drawing",Ink.encode(java.util.Arrays.asList(new Ink(false,0xff245BCC,.004f,new float[]{.1f,.1f,.5f,.5f}))));db.insertOrThrow("page_ink",null,ink);db.execSQL("CREATE TABLE fonts(id TEXT PRIMARY KEY,name TEXT NOT NULL,size INTEGER NOT NULL)");db.execSQL("CREATE TABLE page_text(doc_id TEXT NOT NULL,page INTEGER NOT NULL,boxes TEXT NOT NULL DEFAULT '[]',PRIMARY KEY(doc_id,page))");db.execSQL("ALTER TABLE notes ADD COLUMN tables TEXT NOT NULL DEFAULT '[]'");ContentValues tableValue=new ContentValues();tableValue.put("tables","[{\"id\":\"old-table\",\"cells\":[[\"เดิม\",\"ข้อมูล\"]]}]");db.update("notes",tableValue,"id=77",null);db.setVersion(5);
        } finally {db.close();}
        try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),"release-upgrade-doc"))) {out.write(new byte[]{1,2,3,4,5,6});}
        String font="font-upgrade-preserved";File fontFile=new File(c.getFilesDir(),font);
        try(InputStream input=new FileInputStream(UiFlowTest.systemFont());OutputStream out=new FileOutputStream(fontFile)) {Backup.copy(input,out,20L*1024*1024);}
        SQLiteDatabase seed=c.openOrCreateDatabase("notepocket.db",0,null);
        try {ContentValues f=new ContentValues();f.put("id",font);f.put("name","Upgrade.ttf");f.put("size",fontFile.length());seed.insertOrThrow("fonts",null,f);
            ContentValues text=new ContentValues();text.put("doc_id","release-upgrade-doc");text.put("page",0);text.put("boxes",TextBox.encode(java.util.Arrays.asList(new TextBox("upgrade-text","ข้อความก่อนอัปเดต",font,.1f,.2f,.6f,.04f,0xffE13A3A))));seed.insertOrThrow("page_text",null,text);assertEquals(5,seed.getVersion());
        } finally {seed.close();}
    }
}
