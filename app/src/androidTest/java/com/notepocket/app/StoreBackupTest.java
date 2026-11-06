package com.notepocket.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoreBackupTest {
    private Context context;
    private Store store;
    private final List<File> files=new ArrayList<>();
    @Before public void setup() {context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("store-test.db");store=new Store(context,"store-test.db");}
    @After public void cleanup() {if(store!=null) {for(ContentValues d:store.query("SELECT id FROM docs")) new File(context.getFilesDir(),d.getAsString("id")).delete();store.close();}context.deleteDatabase("store-test.db");for(File f:files) f.delete();}
    @Test public void migratesOriginalVersionZeroWithoutLosingNotesOrFiles() {
        store.close();context.deleteDatabase("store-test.db");
        SQLiteDatabase legacy=context.openOrCreateDatabase("store-test.db",0,null);
        legacy.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,updated INTEGER NOT NULL)");
        legacy.execSQL("CREATE TABLE docs(id TEXT PRIMARY KEY,name TEXT NOT NULL,mime TEXT NOT NULL,size INTEGER NOT NULL)");
        legacy.execSQL("CREATE TABLE note_docs(note_id INTEGER NOT NULL,doc_id TEXT NOT NULL,PRIMARY KEY(note_id,doc_id))");
        legacy.execSQL("INSERT INTO notes VALUES(42,'โน้ตเดิม','ข้อมูลที่ต้องรักษา',123)");
        legacy.execSQL("INSERT INTO docs VALUES('legacy-doc','เก่า.pdf','application/pdf',8)");
        legacy.execSQL("INSERT INTO note_docs VALUES(42,'legacy-doc')");legacy.close();
        store=new Store(context,"store-test.db");ContentValues old=store.note(42);
        assertEquals("ข้อมูลที่ต้องรักษา",old.getAsString("body"));assertEquals("ทั่วไป",old.getAsString("folder"));
        assertEquals(1,store.attachments(42).size());assertEquals(0,(int)old.getAsInteger("deleted"));assertEquals(5,store.getReadableDatabase().getVersion());
    }
    private String document() throws Exception {
        String id=UUID.randomUUID().toString();File file=new File(context.getFilesDir(),id);files.add(file);byte[] bytes="original PDF bytes".getBytes(StandardCharsets.UTF_8);
        try(OutputStream out=new FileOutputStream(file)) {out.write(bytes);}
        ContentValues doc=new ContentValues();doc.put("id",id);doc.put("name","บทเรียน.pdf");doc.put("mime","application/pdf");doc.put("size",bytes.length);store.getWritableDatabase().insertOrThrow("docs",null,doc);return id;
    }
    @Test public void roundTripAddsCopiesAndPreservesAttachmentsPageNotesAndTrash() throws Exception {
        long note=store.save(-1,"ชีววิทยา","ทบทวน","<b>ทบทวน</b>","เรียน");store.flag(note,"pinned",1);store.flag(note,"deleted",1);
        String doc=document();store.attach(note,doc);ContentValues page=store.page(doc,4);page.put("body","จำเรื่องเซลล์");page.put("bookmarked",1);store.savePage(page);store.remember(doc,4);store.saveText(doc,4,TextBox.encode(Arrays.asList(new TextBox("textbox-test","ข้อความไทย","sans",.1f,.1f,.6f,.04f,0xffE13A3A))));store.saveDrawing(doc,4,Ink.encode(Arrays.asList(new Ink(false,0xff245BCC,0.004f,new float[]{0.1f,0.2f,0.5f,0.3f}))));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(context,store,bytes);
        assertEquals(1,Backup.restore(context,store,new ByteArrayInputStream(bytes.toByteArray())));
        List<ContentValues> notes=store.query("SELECT * FROM notes ORDER BY id");assertEquals(2,notes.size());
        long restored=notes.get(1).getAsLong("id");assertNotEquals(note,restored);assertEquals("<b>ทบทวน</b>",notes.get(1).getAsString("rich"));assertEquals(1,(int)notes.get(1).getAsInteger("deleted"));
        String copy=store.attachments(restored).get(0).getAsString("id");assertNotEquals(doc,copy);
        assertEquals("จำเรื่องเซลล์",store.page(copy,4).getAsString("body"));assertEquals(1,(int)store.page(copy,4).getAsInteger("bookmarked"));assertEquals(4,store.lastPage(copy));
        assertEquals(store.drawing(doc,4),store.drawing(copy,4));assertEquals(store.textBoxes(doc,4),store.textBoxes(copy,4));
        assertEquals(new File(context.getFilesDir(),doc).length(),new File(context.getFilesDir(),copy).length());
    }
    @Test public void failedRestoreRollsBackWithoutChangingExistingData() throws Exception {
        long note=store.save(-1,"existing","keep","","ทั่วไป");document();ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(context,store,bytes);
        ByteArrayOutputStream broken=new ByteArrayOutputStream();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));ZipOutputStream out=new ZipOutputStream(broken)) {
            ZipEntry entry;while((entry=in.getNextEntry())!=null) if(entry.getName().equals("manifest.json")) {out.putNextEntry(new ZipEntry(entry.getName()));Backup.copy(in,out,1000000);out.closeEntry();}
        }
        try {Backup.restore(context,store,new ByteArrayInputStream(broken.toByteArray()));fail("Expected incomplete backup rejection");}catch(IOException expected) {}
        assertEquals(1,store.query("SELECT * FROM notes").size());assertEquals("keep",store.note(note).getAsString("body"));assertEquals(1,store.query("SELECT * FROM docs").size());
    }
    @Test public void restoresVersionTwoBackupWithoutDrawingTable() throws Exception {
        long note=store.save(-1,"old backup","keep text","","ทั่วไป");String doc=document();store.attach(note,doc);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(context,store,bytes);ByteArrayOutputStream old=new ByteArrayOutputStream();
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));ZipOutputStream out=new ZipOutputStream(old)) {
            ZipEntry entry;while((entry=in.getNextEntry())!=null) {
                out.putNextEntry(new ZipEntry(entry.getName()));ByteArrayOutputStream data=new ByteArrayOutputStream();Backup.copy(in,data,1000000);
                if(entry.getName().equals("manifest.json")) {org.json.JSONObject manifest=new org.json.JSONObject(data.toString("UTF-8"));manifest.put("version",2);manifest.remove("page_ink");manifest.remove("page_text");manifest.remove("fonts");out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));}
                else out.write(data.toByteArray());out.closeEntry();
            }
        }
        assertEquals(1,Backup.restore(context,store,new ByteArrayInputStream(old.toByteArray())));assertEquals(2,store.query("SELECT id FROM notes").size());assertTrue(store.query("SELECT * FROM page_ink").isEmpty());
    }
    @Test public void rejectsArchivePathTraversal() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)) {out.putNextEntry(new ZipEntry("docs/../../escape"));out.write(1);out.closeEntry();}
        try {Backup.restore(context,store,new ByteArrayInputStream(bytes.toByteArray()));fail("Expected path rejection");}catch(IOException expected) {}
        assertTrue(store.query("SELECT * FROM notes").isEmpty());assertFalse(new File(context.getCacheDir(),"escape").exists());
    }
    @Test public void trashAndRestorePreserveAttachment() throws Exception {
        long note=store.save(-1,"title","text","","ทั่วไป");String doc=document();store.attach(note,doc);store.flag(note,"deleted",1);store.flag(note,"deleted",0);
        assertEquals(1,store.attachments(note).size());store.eraseNote(note);assertEquals(1,store.query("SELECT * FROM docs").size());assertTrue(store.query("SELECT * FROM note_docs").isEmpty());
    }
}
