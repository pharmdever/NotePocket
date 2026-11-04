package com.notepocket.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

/** Offline notes and private document storage. No account or Internet permission. */
public class MainActivity extends Activity {
    private SQLiteDatabase db;
    private LinearLayout root, list;
    private EditText title, body;
    private long editing = -1, pendingNote = -1, pdfReturnNote = -1;
    private boolean editor = false, files = false, importing = false;
    private String query = "";
    private File exporting;
    private PdfRenderer pdf;
    private ParcelFileDescriptor pdfDescriptor;
    private Bitmap pageBitmap;
    private ImageView pageView;
    private TextView pageLabel;
    private int pageIndex;
    private final int ink = Color.rgb(25, 52, 48);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = openOrCreateDatabase("notepocket.db", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS docs (id TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS note_docs (note_id INTEGER NOT NULL, doc_id TEXT NOT NULL, PRIMARY KEY(note_id,doc_id))");
        if (state != null) {
            files = state.getBoolean("files"); query = state.getString("query", "");
            pendingNote = state.getLong("pendingNote", -1);
            String exportId = state.getString("exportId");
            if(exportId != null) exporting = new File(getFilesDir(),exportId);
        }
        if (state != null && state.getBoolean("editor")) edit(state.getLong("editing", -1));
        else home();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(ink); v.setPadding(0, dp(8), 0, dp(8)); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b;
    }
    private void screen(String heading) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(12)); root.setBackgroundColor(Color.rgb(244,247,244));
        root.addView(label(heading, 28)); setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(20), dp(12) + insets.getSystemWindowInsetTop(), dp(20), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this); parent.addView(r); return r;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void home() {
        closePdf(); pdfReturnNote = -1; editor = false; screen("NotePocket");
        root.addView(label("พื้นที่ส่วนตัวสำหรับโน้ตและเอกสาร", 15));
        LinearLayout tabs = row(root);
        tabs.addView(button(files ? "โน้ต" : "● โน้ต", () -> { files = false; query = ""; home(); }));
        tabs.addView(button(files ? "● ไฟล์" : "ไฟล์", () -> { files = true; query = ""; home(); }));
        EditText search = new EditText(this); search.setSingleLine(); search.setHint(files ? "ค้นหาชื่อไฟล์" : "ค้นหาโน้ต"); search.setText(query); root.addView(search);
        root.addView(button(files ? "＋ เพิ่มไฟล์จากเครื่อง" : "＋ เขียนโน้ตใหม่", () -> {
            if (files) {
                pickDocument(-1);
            } else edit(-1);
        }));
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int f) {}
            public void onTextChanged(CharSequence s,int a,int b,int c) { query = s.toString(); refresh(); }
            public void afterTextChanged(Editable e) {}
        }); refresh();
    }
    private void refresh() {
        list.removeAllViews();
        String filter = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String sql = files ? "SELECT id,name,mime,size FROM docs WHERE name LIKE ? ESCAPE '\\' ORDER BY rowid DESC" : "SELECT id,title,body,updated FROM notes WHERE title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' ORDER BY updated DESC";
        try (Cursor c = db.rawQuery(sql, files ? new String[]{filter} : new String[]{filter,filter})) {
            if (!c.moveToFirst()) { list.addView(label(query.isEmpty() ? (files ? "ยังไม่มีไฟล์ • แตะเพิ่มไฟล์เพื่อเริ่มต้น" : "เริ่มบันทึกไอเดียแรกของคุณ") : "ไม่พบรายการที่ค้นหา", 16)); return; }
            do {
                String id = c.getString(0), name = c.getString(1), content = c.getString(2); long value = c.getLong(3);
                boolean document = files;
                LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(8),dp(12),dp(12));
                card.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(8),0,dp(8)); list.addView(card,params);
                card.addView(label(name,20));
                card.addView(label(document ? (content + " · " + android.text.format.Formatter.formatFileSize(this,value)) : (content.length() > 100 ? content.substring(0,100)+"…" : content),14));
                if (!document) card.addView(label(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(value)),12));
                LinearLayout actions = row(card);
                actions.addView(button(document ? "เปิดไฟล์" : "แก้ไข", () -> { if (document) openDocument(id,name,content); else edit(Long.parseLong(id)); }));
                if (document) actions.addView(button("ส่งออก", () -> exportDocument(id,name,content)));
                actions.addView(button("ลบ", () -> new AlertDialog.Builder(this).setTitle("ลบ “"+name+"”?").setMessage("ลบออกจากแอปอย่างถาวร").setNegativeButton("ยกเลิก",null).setPositiveButton("ลบ",(d,w) -> {
                    db.beginTransaction();
                    try {
                        db.delete("note_docs", document ? "doc_id=?" : "note_id=?", new String[]{id});
                        db.delete(document ? "docs" : "notes", "id=?", new String[]{id}); db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                    if (document) new File(getFilesDir(),id).delete(); refresh();
                }).show()));
            } while(c.moveToNext());
        }
    }
    private void edit(long id) {
        closePdf(); pdfReturnNote = -1; editor = true; editing = id; screen("เขียนโน้ต");
        LinearLayout actions = row(root);
        actions.addView(button("บันทึกและกลับ", () -> { if (saveNote()) home(); }));
        actions.addView(button("แชร์ข้อความ", () -> {
            Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT,title.getText()+"\n\n"+body.getText()); startActivity(Intent.createChooser(i,"แชร์โน้ต"));
        }));
        title = new EditText(this); title.setHint("ชื่อโน้ต"); title.setTextSize(23); title.setSingleLine(); root.addView(title);
        body = new EditText(this); body.setHint("เขียนสิ่งที่อยากจดจำ…"); body.setTextSize(18); body.setGravity(Gravity.TOP); body.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(body, new LinearLayout.LayoutParams(-1,0,1));
        root.addView(button("＋ แนบ PDF หรือไฟล์", () -> {
            if(editing == -1 && title.getText().toString().trim().isEmpty() && body.getText().toString().trim().isEmpty()) title.setText("โน้ตแนบไฟล์");
            if(saveNote()) pickDocument(editing);
        }));
        ScrollView attachments = new ScrollView(this);
        LinearLayout attached = new LinearLayout(this); attached.setOrientation(LinearLayout.VERTICAL); attachments.addView(attached);
        int attachmentCount = 0;
        try(Cursor c = db.rawQuery("SELECT d.id,d.name,d.mime FROM docs d JOIN note_docs n ON n.doc_id=d.id WHERE n.note_id=? ORDER BY d.rowid",new String[]{""+id})) {
            while(c.moveToNext()) {
                attachmentCount++;
                String docId = c.getString(0), docName = c.getString(1), mime = c.getString(2);
                attached.addView(button("เปิด: " + docName, () -> { if(saveNote()) openDocument(docId,docName,mime); }));
                attached.addView(button("เอาไฟล์แนบนี้ออก", () -> new AlertDialog.Builder(this).setTitle("เอาไฟล์แนบออก?").setMessage("ไฟล์ยังคงอยู่ในแท็บไฟล์").setNegativeButton("ยกเลิก",null).setPositiveButton("เอาออก",(d,w) -> {
                    if(!saveNote()) return;
                    db.delete("note_docs","note_id=? AND doc_id=?",new String[]{""+editing,docId}); edit(editing);
                }).show()));
            }
        }
        if(attachmentCount > 0) root.addView(attachments,new LinearLayout.LayoutParams(-1,dp(140)));
        root.addView(label("บันทึกอัตโนมัติเมื่อออกจากหน้านี้",12));
        if(id != -1) try(Cursor c = db.rawQuery("SELECT title,body FROM notes WHERE id=?",new String[]{""+id})) { if(c.moveToFirst()) { title.setText(c.getString(0)); body.setText(c.getString(1)); } }
    }
    private boolean saveNote() {
        if(!editor || title == null || body == null) return true;
        String t = title.getText().toString().trim(), b = body.getText().toString();
        if(editing == -1 && t.isEmpty() && b.trim().isEmpty()) return true;
        ContentValues cv = new ContentValues(); cv.put("title",t.isEmpty()?"ไม่มีชื่อ":t); cv.put("body",b); cv.put("updated",System.currentTimeMillis());
        try { if(editing == -1) editing = db.insertOrThrow("notes",null,cv); else db.update("notes",cv,"id=?",new String[]{""+editing}); return true; }
        catch(Exception e) { toast("บันทึกไม่สำเร็จ กรุณาตรวจสอบพื้นที่ว่าง"); return false; }
    }
    @Override protected void onPause() { saveNote(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { saveNote(); state.putLong("pendingNote",pendingNote); state.putBoolean("editor",editor || pdfReturnNote != -1); state.putLong("editing",pdfReturnNote != -1 ? pdfReturnNote : editing); state.putBoolean("files",files); state.putString("query",query); if(exporting != null) state.putString("exportId",exporting.getName()); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if(editor) { if(saveNote()) home(); } else if(pdf != null) returnFromPdf(); else super.onBackPressed(); }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if(request == 10) {
            final long noteId = pendingNote; pendingNote = -1;
            importing = true; toast("กำลังคัดลอกไฟล์เข้าแอป…");
            new Thread(() -> {
                File target = new File(getFilesDir(), UUID.randomUUID().toString());
                try {
                    String name = "เอกสาร", mime = getContentResolver().getType(uri);
                    try(Cursor c = getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if(c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0); }
                    if(mime == null) mime = name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
                    try(InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(target)) { if(in == null) throw new IOException(); copy(in,out); }
                    ContentValues cv = new ContentValues(); cv.put("id",target.getName()); cv.put("name",name); cv.put("mime",mime); cv.put("size",target.length()); db.beginTransaction();
                    try {
                        db.insertOrThrow("docs",null,cv);
                        if(noteId != -1) {
                            try(Cursor note = db.rawQuery("SELECT id FROM notes WHERE id=?",new String[]{""+noteId})) {
                                if(note.moveToFirst()) {
                                    ContentValues link = new ContentValues(); link.put("note_id",noteId); link.put("doc_id",target.getName()); db.insertOrThrow("note_docs",null,link);
                                }
                            }
                        }
                        db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                    runOnUiThread(() -> {
                        importing = false;
                        if(!isFinishing() && !isDestroyed()) {
                            // Keep any text typed while copying before rebuilding the screen.
                            if(!saveNote()) return;
                            if(noteId != -1 && editor && editing == noteId) edit(noteId);
                            else if(!editor && pdf == null) { files = true; query = ""; home(); }
                            toast("เก็บไฟล์แล้ว");
                        }
                    });
                } catch(Exception e) { target.delete(); runOnUiThread(() -> { importing = false; toast("นำเข้าไม่สำเร็จ ตรวจสอบไฟล์และพื้นที่ว่าง"); }); }
            }).start();
        } else if(request == 11 && exporting != null) {
            File source = exporting;
            new Thread(() -> {
                try(InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri,"wt")) { if(out == null) throw new IOException(); copy(in,out); runOnUiThread(() -> toast("ส่งออกไฟล์แล้ว")); }
                catch(Exception e) { runOnUiThread(() -> toast("ส่งออกไม่สำเร็จ กรุณาลองอีกครั้ง")); }
            }).start();
        }
    }
    private void pickDocument(long noteId) {
        if(importing) { toast("กำลังนำเข้าไฟล์ กรุณารอสักครู่"); return; }
        pendingNote = noteId;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,10);
    }
    private void returnFromPdf() {
        long noteId = pdfReturnNote; closePdf(); pdfReturnNote = -1;
        if(noteId != -1) edit(noteId); else home();
    }
    private static void copy(InputStream in,OutputStream out) throws IOException { byte[] buffer = new byte[65536]; int n; while((n=in.read(buffer)) != -1) out.write(buffer,0,n); }
    private void exportDocument(String id,String name,String mime) {
        exporting = new File(getFilesDir(),id); Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(mime); i.putExtra(Intent.EXTRA_TITLE,name); startActivityForResult(i,11);
    }
    private void openDocument(String id,String name,String mime) {
        if(!mime.equals("application/pdf") && !name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            new AlertDialog.Builder(this).setTitle(name).setMessage("ไฟล์นี้เก็บไว้ในแอปแล้ว สามารถส่งออกไปเปิดด้วยแอปที่รองรับได้").setPositiveButton("ส่งออก",(d,w) -> exportDocument(id,name,mime)).setNegativeButton("ปิด",null).show(); return;
        }
        if(editor && !saveNote()) return;
        pdfReturnNote = editor ? editing : -1; editor = false;
        try {
            pdfDescriptor = ParcelFileDescriptor.open(new File(getFilesDir(),id),ParcelFileDescriptor.MODE_READ_ONLY); pdf = new PdfRenderer(pdfDescriptor);
            if(pdf.getPageCount() == 0) throw new IOException();
            pageIndex = 0; screen(name); root.addView(button(pdfReturnNote != -1 ? "กลับไปโน้ต" : "กลับไปไฟล์",this::returnFromPdf));
            LinearLayout controls = row(root); controls.addView(button("ก่อนหน้า",() -> renderPage(pageIndex-1))); pageLabel = label("",14); controls.addView(pageLabel); controls.addView(button("ถัดไป",() -> renderPage(pageIndex+1)));
            ScrollView scroll = new ScrollView(this); pageView = new ImageView(this); pageView.setAdjustViewBounds(true); pageView.setContentDescription("หน้าเอกสาร PDF"); scroll.addView(pageView,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); renderPage(0);
        } catch(Exception e) { returnFromPdf(); toast("เปิด PDF ไม่ได้ ไฟล์อาจเสียหายหรือมีรหัสผ่าน"); }
    }
    private void renderPage(int index) {
        if(pdf == null || index < 0 || index >= pdf.getPageCount()) return;
        try(PdfRenderer.Page p = pdf.openPage(index)) {
            float scale = Math.min(2f, Math.min(1600f/p.getWidth(),2200f/p.getHeight()));
            Bitmap next = Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*scale)),Math.max(1,Math.round(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE); p.render(next,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pageView.setImageBitmap(next); if(pageBitmap != null) pageBitmap.recycle(); pageBitmap = next;
            pageIndex = index; pageLabel.setText("  "+(index+1)+" / "+pdf.getPageCount()+"  ");
            ((ScrollView)pageView.getParent()).scrollTo(0,0);
        } catch(Exception e) { toast("แสดงหน้านี้ไม่สำเร็จ"); }
    }
    private void closePdf() {
        if(pageView != null) { pageView.setImageDrawable(null); pageView = null; }
        if(pageBitmap != null) { pageBitmap.recycle(); pageBitmap = null; }
        if(pdf != null) { pdf.close(); pdf = null; }
        if(pdfDescriptor != null) { try { pdfDescriptor.close(); } catch(IOException ignored) {} pdfDescriptor = null; }
    }
    @Override protected void onDestroy() { closePdf(); super.onDestroy(); }
}
