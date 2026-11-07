package com.notepocket.app;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DriveSyncTest {
    private final List<Device> devices=new ArrayList<>();
    @After public void cleanup() {for(Device d:devices) d.close();assertEquals(0,SyncRuntime.editors);}
    private Device device() throws Exception {Device d=new Device();devices.add(d);return d;}
    private static final class Space extends ContextWrapper {
        final File root,files,cache,db;
        Space(Context context) {super(context);root=new File(context.getCacheDir(),"drive-qa-"+UUID.randomUUID());files=new File(root,"files");cache=new File(root,"cache");db=new File(root,"db");files.mkdirs();cache.mkdirs();db.mkdirs();}
        @Override public File getFilesDir() {return files;}@Override public File getCacheDir() {return cache;}@Override public File getDatabasePath(String name) {return new File(db,name);}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f) {return openOrCreateDatabase(n,mode,f,null);}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler handler) {return SQLiteDatabase.openDatabase(getDatabasePath(n).getPath(),f,SQLiteDatabase.CREATE_IF_NECESSARY|SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,handler);}
    }
    private static final class Device implements AutoCloseable {
        final Space c=new Space(InstrumentationRegistry.getInstrumentation().getTargetContext());final Store store=new Store(c,"sync.db");
        Device() throws Exception {new SyncEngine(c,store,new SyncCancel(),m->{}).bindAccount(user("owner"));}
        String sync(MemoryDrive remote) throws Exception {return new SyncEngine(c,store,new SyncCancel(),m->{}).sync(remote);}
        @Override public void close() {store.close();Backup.deleteTree(c.root);}
    }
    private static JSONObject user(String owner) throws JSONException {return new JSONObject().put("permissionId",owner).put("emailAddress","sample@example.com");}
    private static final class MemoryDrive implements SyncRemote {
        String owner="owner";final Map<String,JSONObject> journals=new LinkedHashMap<>();final Map<String,byte[]> blobs=new LinkedHashMap<>();boolean failDownload,corruptDownload,failAfterPublish,reverse;int uploads;
        public JSONObject account() throws Exception {return new JSONObject().put("user",user(owner));}
        public List<Item> list() {List<Item> result=new ArrayList<>();for(String sha:blobs.keySet()) result.add(new Item("blob_"+sha,"blob","blob-v1",sha));for(String id:journals.keySet()) result.add(new Item(id,"journal","journal-v1",""));if(reverse) Collections.reverse(result);return result;}
        public JSONObject journal(String id) throws Exception {return SyncJson.copy(journals.get(id));}
        public String publish(JSONObject commit) throws Exception {String id="j_"+UUID.randomUUID();journals.put(id,SyncJson.copy(commit));if(failAfterPublish) {failAfterPublish=false;throw new IOException("Lost acknowledgement");}return id;}
        public String uploadBlob(String sha,File file) throws Exception {byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());assertEquals(sha,SyncJson.digest(bytes));blobs.put(sha,bytes);uploads++;return "blob_"+sha;}
        public void downloadBlob(String id,File file,long size) throws Exception {if(failDownload) throw new IOException("Offline midway");byte[] bytes=blobs.get(id.substring(5));if(corruptDownload) bytes=new byte[bytes.length];try(OutputStream out=new FileOutputStream(file)) {out.write(bytes);}assertEquals(size,file.length());}
    }
    private static String doc(Device d,String name) throws Exception {String id=UUID.randomUUID().toString();File file=new File(d.c.getFilesDir(),id);PdfDocument pdf=new PdfDocument();PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());page.getCanvas().drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.BLACK);p.setTextSize(24);page.getCanvas().drawText("Drive offline fixture",30,80,p);pdf.finishPage(page);try(OutputStream out=new FileOutputStream(file)) {pdf.writeTo(out);}finally {pdf.close();}ContentValues row=new ContentValues();row.put("id",id);row.put("name",name);row.put("mime","application/pdf");row.put("size",file.length());d.store.getWritableDatabase().insertOrThrow("docs",null,row);return id;}
    private static ContentValues note(Device d,String title) {List<ContentValues> rows=d.store.query("SELECT * FROM notes WHERE title=?",title);assertEquals(1,rows.size());return rows.get(0);}

    @Test public void allContentFilesFontsAndAnnotationsRoundTripAndOpenOffline() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();String file=doc(a,"study.pdf");long id=a.store.save(-1,"ครบทุกส่วน","เนื้อหาไทย","","เรียน",NoteTable.encode(Arrays.asList(new NoteTable(3,3))),new SoapDraft().json());a.store.attach(id,file);a.store.createFolder("empty-folder");
        String font;try(InputStream in=new FileInputStream(UiFlowTest.systemFont())) {font=Fonts.importFont(a.c,a.store,in,"Sample.ttf");}
        a.store.saveDrawing(file,0,Ink.encode(Arrays.asList(new Ink(false,Color.RED,.004f,new float[]{.1f,.1f,.5f,.5f}))));a.store.saveText(file,0,TextBox.encode(Arrays.asList(new TextBox("box","ข้อความไทย",font,.1f,.2f,.6f,.04f,Color.BLUE))));ContentValues page=a.store.page(file,0);page.put("body","page note");page.put("bookmarked",1);a.store.savePage(page);a.store.remember(file,0);
        a.sync(remote);b.sync(remote);ContentValues copy=note(b,"ครบทุกส่วน");assertEquals("เนื้อหาไทย",copy.getAsString("body"));assertEquals(1,b.store.attachments(copy.getAsLong("id")).size());assertEquals("page note",b.store.page(file,0).getAsString("body"));assertEquals(1,Ink.decode(b.store.drawing(file,0)).size());assertEquals(Color.RED,Ink.decode(b.store.drawing(file,0)).get(0).color);assertArrayEquals(new float[]{.1f,.1f,.5f,.5f},Ink.decode(b.store.drawing(file,0)).get(0).points,.0001f);assertEquals(font,TextBox.decode(b.store.textBoxes(file,0)).get(0).font);assertTrue(new File(b.c.getFilesDir(),font).isFile());assertFalse(b.store.query("SELECT name FROM folders WHERE name='empty-folder'").isEmpty());assertEquals(3,NoteTable.decode(copy.getAsString("tables")).get(0).cells.size());
        assertEquals(SyncJson.fileHash(new File(a.c.files,file),new SyncCancel()),SyncJson.fileHash(new File(b.c.files,file),new SyncCancel()));
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(new File(b.c.files,file),ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)) {assertEquals(1,renderer.getPageCount());}
        long count=b.store.query("SELECT COUNT(*) AS n FROM notes").get(0).getAsLong("n");for(int i=0;i<3;i++) {a.sync(remote);b.sync(remote);}assertEquals(count,(long)b.store.query("SELECT COUNT(*) AS n FROM notes").get(0).getAsLong("n"));assertEquals(2,remote.blobs.size());
    }
    @Test public void twoOfflineEditsKeepBothNotesAndConvergeWithoutGrowingCopies() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();long id=a.store.save(-1,"same","initial","","ทั่วไป");a.sync(remote);b.sync(remote);long bid=note(b,"same").getAsLong("id");a.store.save(id,"same","from A","","ทั่วไป");b.store.save(bid,"same","from B","","ทั่วไป");a.sync(remote);b.sync(remote);a.sync(remote);
        for(Device d:Arrays.asList(a,b)) {List<ContentValues> rows=d.store.query("SELECT body FROM notes");assertEquals(2,rows.size());Set<String> bodies=new HashSet<>();for(ContentValues row:rows) bodies.add(row.getAsString("body"));assertEquals(new HashSet<>(Arrays.asList("from A","from B")),bodies);}
        for(int i=0;i<3;i++) {b.sync(remote);a.sync(remote);}assertEquals(2,a.store.query("SELECT id FROM notes").size());assertEquals(2,b.store.query("SELECT id FROM notes").size());
    }
    @Test public void concurrentPdfChangesKeepSeparateReadableDocumentCopies() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();String file=doc(a,"paper.pdf");a.store.saveDrawing(file,0,"[]");a.sync(remote);b.sync(remote);a.store.saveDrawing(file,0,Ink.encode(Arrays.asList(new Ink(false,Color.RED,.004f,new float[]{.1f,.1f,.3f,.3f}))));b.store.saveDrawing(file,0,Ink.encode(Arrays.asList(new Ink(false,Color.BLUE,.004f,new float[]{.6f,.6f,.8f,.8f}))));a.sync(remote);b.sync(remote);a.sync(remote);
        for(Device d:Arrays.asList(a,b)) {List<ContentValues> docs=d.store.query("SELECT * FROM docs");assertEquals(2,docs.size());Set<Integer> colors=new HashSet<>();for(ContentValues row:docs) {String id=row.getAsString("id");colors.add(Ink.decode(d.store.drawing(id,0)).get(0).color);assertTrue(new File(d.c.files,id).isFile());}assertEquals(new HashSet<>(Arrays.asList(Color.RED,Color.BLUE)),colors);}
    }
    @Test public void deletedNoteIdReuseDoesNotOverwriteAnotherIdentity() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();long old=a.store.save(-1,"old","old text","","ทั่วไป");a.sync(remote);b.sync(remote);a.store.eraseNote(old);long replacement=a.store.save(-1,"new","new text","","ทั่วไป");assertEquals(old,replacement);a.sync(remote);b.sync(remote);assertTrue(b.store.query("SELECT id FROM notes WHERE title='old'").isEmpty());assertEquals("new text",note(b,"new").getAsString("body"));
    }
    @Test public void editConcurrentWithDeletionSurvives() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();long id=a.store.save(-1,"keep","initial","","ทั่วไป");a.sync(remote);b.sync(remote);a.store.eraseNote(id);b.store.save(note(b,"keep").getAsLong("id"),"keep","offline edit","","ทั่วไป");a.sync(remote);b.sync(remote);a.sync(remote);assertEquals("offline edit",note(a,"keep").getAsString("body"));assertEquals("offline edit",note(b,"keep").getAsString("body"));
    }
    @Test public void failedOrCorruptDownloadsNeverPublishPartialLocalDocuments() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();String file=doc(a,"checked.pdf");long id=a.store.save(-1,"cloud","body","","ทั่วไป");a.store.attach(id,file);a.sync(remote);b.store.save(-1,"local only","must remain","","ทั่วไป");remote.corruptDownload=true;
        try {b.sync(remote);fail();}catch(IOException expected) {}assertTrue(b.store.query("SELECT * FROM docs").isEmpty());assertEquals("must remain",note(b,"local only").getAsString("body"));assertFalse(new File(b.c.files,file).exists());remote.corruptDownload=false;b.sync(remote);assertTrue(new File(b.c.files,file).isFile());assertEquals(1,b.store.query("SELECT id FROM notes WHERE title='cloud'").size());
    }
    @Test public void lostUploadAcknowledgementIsIdempotent() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();a.store.save(-1,"idempotent","saved once","","ทั่วไป");remote.failAfterPublish=true;try {a.sync(remote);fail();}catch(IOException expected) {}a.sync(remote);b.sync(remote);assertEquals(1,b.store.query("SELECT id FROM notes WHERE title='idempotent'").size());a.sync(remote);assertEquals(1,a.store.query("SELECT id FROM notes WHERE title='idempotent'").size());
    }
    @Test public void accountMismatchCannotUploadOrChangeBinding() throws Exception {
        Device a=device();MemoryDrive remote=new MemoryDrive();remote.owner="different";a.store.save(-1,"private","not for another account","","ทั่วไป");try {a.sync(remote);fail();}catch(IOException expected) {}assertTrue(remote.journals.isEmpty());SyncEngine engine=new SyncEngine(a.c,a.store,new SyncCancel(),m->{});try {engine.bindAccount(user("different"));fail();}catch(IOException expected) {}assertEquals("owner",engine.config("owner"));
    }
    @Test public void cancellationAndLiveEditorDeferWithoutChangingLocalRows() throws Exception {
        Device a=device();MemoryDrive remote=new MemoryDrive();a.store.save(-1,"draft","safe","","ทั่วไป");SyncCancel c=new SyncCancel();c.cancel();try {new SyncEngine(a.c,a.store,c,m->{}).sync(remote);fail();}catch(InterruptedIOException expected) {}SyncRuntime.enterEditor();try {a.sync(remote);fail();}catch(SyncRuntime.Deferred expected) {}finally {SyncRuntime.leaveEditor();}assertTrue(remote.journals.isEmpty());assertEquals("safe",note(a,"draft").getAsString("body"));
    }
    @Test public void historyRestoreCreatesNewCopyAndKeepsCurrentVersion() throws Exception {
        Device a=device();MemoryDrive remote=new MemoryDrive();long id=a.store.save(-1,"history","original","","ทั่วไป");a.sync(remote);String rev=a.store.query("SELECT rev FROM sync_ops WHERE kind='note'").get(0).getAsString("rev");a.store.save(id,"history","new content","","ทั่วไป");a.sync(remote);new SyncEngine(a.c,a.store,new SyncCancel(),m->{}).restoreCopy(rev);a.sync(remote);assertEquals("new content",note(a,"history").getAsString("body"));List<ContentValues> copies=a.store.query("SELECT body FROM notes WHERE title<>?","history");assertEquals(1,copies.size());assertEquals("original",copies.get(0).getAsString("body"));
    }
    @Test public void folderRenameAndDeleteEmptyFolderPropagate() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();a.store.createFolder("empty");a.store.save(-1,"folder note","body","","before");a.sync(remote);b.sync(remote);a.store.renameFolder("before","after");a.store.deleteFolder("empty");a.sync(remote);b.sync(remote);assertEquals("after",note(b,"folder note").getAsString("folder"));assertTrue(b.store.query("SELECT name FROM folders WHERE name IN ('empty','before')").isEmpty());assertFalse(b.store.query("SELECT name FROM folders WHERE name='after'").isEmpty());
    }
    @Test public void missingFolderMetadataIsReconstructedWithoutChangingNoteContent() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();JSONObject value=new JSONObject().put("title","reconstruct").put("body","complete note").put("rich","").put("folder","from note metadata").put("tables","[]").put("soap","{}").put("pinned",0).put("deleted",0).put("updated",1).put("docs",new JSONArray());SyncOp op=new SyncOp(UUID.randomUUID().toString(),"n-"+UUID.randomUUID(),"note",Collections.emptyList(),value,1);remote.publish(new JSONObject().put("format","NotePocket-sync-1").put("ops",new JSONArray().put(op.json())));a.sync(remote);b.sync(remote);assertEquals("complete note",note(b,"reconstruct").getAsString("body"));assertFalse(b.store.query("SELECT name FROM folders WHERE name='from note metadata'").isEmpty());
    }
    @Test public void annotationEditConcurrentWithDocumentDeletionRetainsThePdf() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();String file=doc(a,"survive.pdf");a.store.saveDrawing(file,0,"[]");a.sync(remote);b.sync(remote);a.store.eraseDoc(file);new File(a.c.files,file).delete();b.store.saveDrawing(file,0,Ink.encode(Arrays.asList(new Ink(false,Color.BLUE,.004f,new float[]{.2f,.2f,.6f,.6f}))));a.sync(remote);b.sync(remote);a.sync(remote);for(Device d:Arrays.asList(a,b)) {assertEquals(1,d.store.query("SELECT id FROM docs WHERE id=?",file).size());assertTrue(new File(d.c.files,file).isFile());assertEquals(Color.BLUE,Ink.decode(d.store.drawing(file,0)).get(0).color);}
    }
    @Test public void unorderedJournalsStillRespectRevisionAncestry() throws Exception {
        Device a=device(),b=device();MemoryDrive remote=new MemoryDrive();long id=a.store.save(-1,"ordered","one","","ทั่วไป");a.sync(remote);a.store.save(id,"ordered","two","","ทั่วไป");a.sync(remote);a.store.save(id,"ordered","three","","ทั่วไป");a.sync(remote);remote.reverse=true;b.sync(remote);assertEquals("three",note(b,"ordered").getAsString("body"));assertEquals(1,b.store.query("SELECT id FROM notes").size());
    }
}
