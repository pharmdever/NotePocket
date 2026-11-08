package com.notepocket.app;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** This instrumentation is run against the signed 0.11.0 APK before installing 0.12.0. */
@RunWith(AndroidJUnit4.class)
public class LegacyUpgradeSeedTest {
    @Test public void seedOriginalReleaseSchema() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals(12,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode);
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
            ContentValues ink=new ContentValues();ink.put("doc_id","release-upgrade-doc");ink.put("page",0);ink.put("drawing","[{\"highlight\":false,\"color\":-14394420,\"width\":0.004,\"points\":[1000,1000,5000,5000]}]");db.insertOrThrow("page_ink",null,ink);db.execSQL("CREATE TABLE fonts(id TEXT PRIMARY KEY,name TEXT NOT NULL,size INTEGER NOT NULL)");db.execSQL("CREATE TABLE page_text(doc_id TEXT NOT NULL,page INTEGER NOT NULL,boxes TEXT NOT NULL DEFAULT '[]',PRIMARY KEY(doc_id,page))");db.execSQL("ALTER TABLE notes ADD COLUMN tables TEXT NOT NULL DEFAULT '[]'");ContentValues tableValue=new ContentValues();tableValue.put("tables","[{\"id\":\"old-table\",\"cells\":[[\"เดิม\",\"ข้อมูล\"]]}]");db.update("notes",tableValue,"id=77",null);db.execSQL("ALTER TABLE notes ADD COLUMN soap TEXT NOT NULL DEFAULT '{}'");db.execSQL("CREATE TABLE folders(name TEXT PRIMARY KEY NOT NULL)");db.execSQL("INSERT INTO folders VALUES('ทั่วไป'),('เรียน'),('empty-stable-folder')");tableValue.clear();tableValue.put("soap","{\"s\":\"SOAP ก่อนเปลี่ยนธีม\"}");db.update("notes",tableValue,"id=77",null);db.execSQL("CREATE INDEX notes_page ON notes(deleted,pinned DESC,updated DESC,id DESC)");db.execSQL("CREATE INDEX notes_folder_page ON notes(folder,deleted,pinned DESC,updated DESC,id DESC)");        db.execSQL("ALTER TABLE notes ADD COLUMN sync_id TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS notes_sync_id ON notes(sync_id) WHERE sync_id!=''");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_config(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_ops(rev TEXT PRIMARY KEY,entity TEXT NOT NULL,kind TEXT NOT NULL,json TEXT NOT NULL,label TEXT NOT NULL,time INTEGER NOT NULL,deleted INTEGER NOT NULL,uploaded INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_entity ON sync_ops(entity)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_parents(rev TEXT NOT NULL,parent TEXT NOT NULL,PRIMARY KEY(rev,parent))");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_parent ON sync_parents(parent)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_bindings(entity TEXT PRIMARY KEY,kind TEXT NOT NULL,local_key TEXT NOT NULL,fingerprint TEXT NOT NULL,head TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_seen(file_id TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_blob_local(id TEXT PRIMARY KEY,sha TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL)");
db.execSQL("UPDATE notes SET sync_id='upgrade-note-sync-77' WHERE id=77");db.execSQL("INSERT INTO sync_config(k,v) VALUES('upgrade-marker','keep')");ContentValues diagram=new ContentValues();diagram.put("id",78);diagram.put("title","Previous Mermaid source");diagram.put("body","before\n```mermaid\nflowchart TD\nA-->B\n```\nafter");diagram.put("updated",124);diagram.put("sync_id","upgrade-diagram-78");db.insertOrThrow("notes",null,diagram);db.setVersion(8);
        } finally {db.close();}
        try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),"release-upgrade-doc"))) {out.write(new byte[]{1,2,3,4,5,6});}
        String font="font-upgrade-preserved";File fontFile=new File(c.getFilesDir(),font);
        try(InputStream input=new FileInputStream(systemFont());OutputStream out=new FileOutputStream(fontFile)) {byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1) out.write(block,0,n);}
        SQLiteDatabase seed=c.openOrCreateDatabase("notepocket.db",0,null);
        try {ContentValues f=new ContentValues();f.put("id",font);f.put("name","Upgrade.ttf");f.put("size",fontFile.length());seed.insertOrThrow("fonts",null,f);
            ContentValues text=new ContentValues();text.put("doc_id","release-upgrade-doc");text.put("page",0);text.put("boxes","[{\"id\":\"upgrade-text\",\"text\":\"ข้อความก่อนอัปเดต\",\"font\":\"font-upgrade-preserved\",\"x\":0.1,\"y\":0.2,\"width\":0.6,\"size\":0.04,\"color\":-2016710}]");seed.insertOrThrow("page_text",null,text);assertEquals(8,seed.getVersion());
        } finally {seed.close();}
    }
    private static File systemFont() {File[] fonts=new File("/system/fonts").listFiles((dir,name)->name.endsWith(".ttf"));assertNotNull(fonts);assertTrue(fonts.length>0);for(File f:fonts) if(f.getName().contains("Thai")) return f;return fonts[0];}
}
