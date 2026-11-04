package com.notepocket.app;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int GREEN=0xff216B58,INK=0xff203B34,MUTED=0xff748079,BG=0xffF5F6F1,LINE=0xffE4E8DF;
    private static final int PICK=10,EXPORT=11,BACKUP=12,RESTORE=13;
    private Store store;
    private LinearLayout root,list,attachments;
    private EditText title,body;
    private TextView savedLabel,pageLabel,bookmarkButton;
    private String tab="notes",filter="all",query="",folder="ทั่วไป",pdfId,pdfName,exportId;
    private long noteId=-1,pendingAttach=-1,pdfReturnNote=-1;
    private boolean editor=false,loading=false,dirty=false,busy=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable autosave=() -> saveNote(false);
    private PdfRenderer pdf;
    private ParcelFileDescriptor pdfFd;
    private Bitmap pageBitmap;
    private PdfPageView pageView;
    private int pageIndex;
    private ProgressDialog progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new Store(getApplicationContext());store.getWritableDatabase();
        if(state!=null) {
            tab=state.getString("tab","notes");filter=state.getString("filter","all");query=state.getString("query","");
            pendingAttach=state.getLong("pendingAttach",-1);exportId=state.getString("exportId");
            if(state.getString("pdfId")!=null) { pdfReturnNote=state.getLong("pdfReturnNote",-1);openPdf(state.getString("pdfId"),state.getString("pdfName","PDF"),state.getInt("page",0),pdfReturnNote);return; }
            if(state.getBoolean("editor")) { edit(state.getLong("noteId",-1));return; }
        }
        home();
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    private TextView text(String value,int size,int color) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));return v;
    }
    private TextView strong(String value,int size) { TextView v=text(value,size,INK);v.setTypeface(null,Typeface.BOLD);return v; }
    private Button button(String value,Runnable action,boolean primary) {
        Button b=new Button(this);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);
        b.setBackground(background(primary?GREEN:0xffE7EEE4,14));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setStateListAnimator(null);
        b.setOnClickListener(v -> { if(!busy) action.run(); });return b;
    }
    private void gap(LinearLayout parent,int size) { Space v=new Space(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size))); }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout horizontal(LinearLayout parent) { LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v; }
    private void action(LinearLayout row,String label,Runnable run,boolean primary) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(button(label,run,primary),p);
    }
    private void screen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),dp(12));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(dp(18),dp(12)+i.getSystemWindowInsetTop(),dp(10)+dp(8),dp(10)+i.getSystemWindowInsetBottom());return i; });
    }
    private LinearLayout scroll(LinearLayout parent,int weight) {
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);LinearLayout v=vertical();sc.addView(v);
        parent.addView(sc,weight>0?new LinearLayout.LayoutParams(-1,0,weight):new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=vertical();c.setPadding(dp(17),dp(13),dp(17),dp(13));c.setBackground(background(Color.WHITE,20));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));parent.addView(c,p);return c;
    }
    private EditText input(String hint,boolean single) {
        EditText e=new EditText(this);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(17);e.setSingleLine(single);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(background(Color.WHITE,14));return e;
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void keyboardOff() { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0); }
    private void titleBar(String heading,Runnable back) {
        LinearLayout row=horizontal(root);row.addView(button("‹",back,false),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView t=strong(heading,20);t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(12),0,0,0);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));gap(root,12);
    }
    private void home() {
        if(editor&&!saveNote(false)) return;
        handler.removeCallbacks(autosave);closePdf();editor=false;pdfReturnNote=-1;screen();
        LinearLayout heading=horizontal(root);heading.addView(strong("NotePocket",28),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(text("ออฟไลน์",12,GREEN));
        root.addView(text(tab.equals("files")?"เอกสารพร้อมอ่าน ใกล้โน้ตของคุณ":tab.equals("settings")?"ดูแลข้อมูลและการอัปเดต":"เก็บความคิด แล้วกลับมาต่อยอด",15,MUTED));gap(root,12);
        if(tab.equals("settings")) settings();
        else {
            EditText search=input(tab.equals("files")?"ค้นหาเอกสาร…":"ค้นหาในชื่อและเนื้อหา…",true);search.setText(query);root.addView(search);gap(root,10);
            LinearLayout commands=horizontal(root);
            if(tab.equals("files")) action(commands,"＋ นำเข้าไฟล์",() -> pickDocument(-1),true);
            else {
                action(commands,"＋ เขียนโน้ต",() -> edit(-1),true);
                action(commands,filter.equals("all")?"ทั้งหมด ▾":filter.equals("pinned")?"ปักหมุด ▾":filter.equals("trash")?"ถังขยะ ▾":filter+" ▾",this::chooseFilter,false);
            }
            list=scroll(root,1);refresh();
            search.addTextChangedListener(watcher(() -> {query=search.getText().toString();refresh();}));
        }
        gap(root,8);LinearLayout nav=horizontal(root);
        for(String key:new String[]{"notes","files","settings"}) {
            String label=key.equals("notes")?"โน้ต":key.equals("files")?"เอกสาร":"ตั้งค่า";
            action(nav,label,() -> {tab=key;query="";home();},tab.equals(key));
        }
    }
    private TextWatcher watcher(Runnable r) { return new TextWatcher() {
        public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
        public void onTextChanged(CharSequence s,int start,int before,int count) { r.run(); }
        public void afterTextChanged(Editable e) {}
    }; }
    private void chooseFilter() {
        List<String> values=new ArrayList<>(Arrays.asList("all","pinned","trash"));List<String> labels=new ArrayList<>(Arrays.asList("โน้ตทั้งหมด","ปักหมุด","ถังขยะ"));
        for(ContentValues r:store.query("SELECT DISTINCT folder FROM notes WHERE deleted=0 ORDER BY folder")) {values.add(r.getAsString("folder"));labels.add("โฟลเดอร์: "+r.getAsString("folder"));}
        new AlertDialog.Builder(this).setTitle("แสดงโน้ต").setItems(labels.toArray(new String[0]),(d,w) -> {filter=values.get(w);home();}).show();
    }
    private void refresh() {
        list.removeAllViews();String match=Store.like(query);
        if(tab.equals("files")) {
            List<ContentValues> docs=store.query("SELECT * FROM docs WHERE name LIKE ? ESCAPE '\\' ORDER BY rowid DESC",match);
            list.addView(text(docs.size()+" เอกสาร",13,MUTED));
            for(ContentValues doc:docs) documentCard(list,doc,false);
            if(docs.isEmpty()) empty("ยังไม่มีเอกสาร","นำเข้า PDF เพื่ออ่านและจดบันทึกประกอบแต่ละหน้า");
            return;
        }
        String clause=filter.equals("trash")?"deleted=1":"deleted=0";
        if(filter.equals("pinned")) clause+=" AND pinned=1";
        List<ContentValues> rows;
        if(!Arrays.asList("all","trash","pinned").contains(filter)) rows=store.query("SELECT * FROM notes WHERE "+clause+" AND folder=? AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY pinned DESC,updated DESC",filter,match,match);
        else rows=store.query("SELECT * FROM notes WHERE "+clause+" AND (title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') ORDER BY pinned DESC,updated DESC",match,match);
        list.addView(text(rows.size()+" โน้ต"+(filter.equals("trash")?"ในถังขยะ":""),13,MUTED));
        for(ContentValues note:rows) {
            long id=note.getAsLong("id");boolean deleted=note.getAsInteger("deleted")==1;
            LinearLayout c=card(list);c.addView(text((note.getAsInteger("pinned")==1?"ปักหมุด  ·  ":"")+note.getAsString("folder"),12,GREEN));
            c.addView(strong(note.getAsString("title"),21));TextView preview=text(note.getAsString("body"),15,MUTED);preview.setMaxLines(3);preview.setEllipsize(TextUtils.TruncateAt.END);c.addView(preview);
            int count=store.attachments(id).size();c.addView(text(date(note.getAsLong("updated"))+(count>0?"  ·  "+count+" ไฟล์แนบ":""),12,MUTED));
            LinearLayout buttons=horizontal(c);
            if(deleted) {
                action(buttons,"กู้คืน",() -> {store.flag(id,"deleted",0);refresh();},false);
                action(buttons,"ลบถาวร",() -> confirm("ลบโน้ตถาวร?","ไฟล์แนบยังคงอยู่ในเอกสาร แต่ข้อความจะกู้คืนไม่ได้",() -> {store.eraseNote(id);refresh();}),false);
            } else {
                c.setOnClickListener(v -> edit(id));action(buttons,"เปิดโน้ต",() -> edit(id),false);
                action(buttons,"ตัวเลือก",() -> noteMenu(id),false);
            }
        }
        if(rows.isEmpty()) empty(query.isEmpty()?(filter.equals("trash")?"ถังขยะว่าง":"พื้นที่สำหรับความคิดใหม่"):"ไม่พบโน้ต","แตะเขียนโน้ตเพื่อเริ่ม หรือเปลี่ยนคำค้นหา");
    }
    private void empty(String heading,String hint) { LinearLayout c=card(list);c.addView(strong(heading,21));c.addView(text(hint,15,MUTED)); }
    private String date(long time) { return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time)); }
    private void confirm(String heading,String message,Runnable action) { new AlertDialog.Builder(this).setTitle(heading).setMessage(message).setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w) -> action.run()).show(); }
    private void noteMenu(long id) {
        ContentValues n=store.note(id);if(n==null) return;
        new AlertDialog.Builder(this).setTitle(n.getAsString("title")).setItems(new String[]{n.getAsInteger("pinned")==1?"เลิกปักหมุด":"ปักหมุด","ย้ายเข้าถังขยะ"},(d,w) -> {
            if(w==0) {store.flag(id,"pinned",n.getAsInteger("pinned")==1?0:1);refresh();}
            else confirm("ย้ายเข้าถังขยะ?","สามารถกู้คืนได้จากตัวกรองถังขยะ",() -> {store.flag(id,"deleted",1);refresh();});
        }).show();
    }
    private void edit(long id) {
        closePdf();pdfReturnNote=-1;editor=false;loading=true;noteId=id;dirty=false;
        ContentValues note=id<0?null:store.note(id);
        if(id>=0&&(note==null||note.getAsInteger("deleted")==1)) {home();toast("ไม่พบโน้ตนี้");return;}
        folder=note==null?"ทั่วไป":note.getAsString("folder");screen();
        titleBar("เขียนโน้ต",() -> {if(saveNote(false)) {keyboardOff();home();}});
        LinearLayout tools=horizontal(root);
        action(tools,"แนบไฟล์",this::attachmentMenu,false);
        action(tools,"โฟลเดอร์",this::chooseFolder,false);
        action(tools,"แชร์",() -> {
            if(!saveNote(false)) return;
            Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,title.getText()+"\n\n"+body.getText());startActivity(Intent.createChooser(i,"แชร์ข้อความโน้ต"));
        },false);
        savedLabel=text("บันทึกอัตโนมัติ  ·  "+folder,12,MUTED);root.addView(savedLabel);gap(root,8);
        LinearLayout page=scroll(root,1);
        title=input("ชื่อโน้ต",true);title.setTextSize(25);title.setTypeface(null,Typeface.BOLD);page.addView(title);gap(page,10);
        LinearLayout format=horizontal(page);
        action(format,"ตัวหนา",() -> style(Typeface.BOLD),false);action(format,"เอียง",() -> style(Typeface.ITALIC),false);
        action(format,"☐ รายการ",() -> insertChecklist(),false);
        body=input("เขียนสิ่งที่อยากจดจำ…",false);body.setSingleLine(false);body.setGravity(Gravity.TOP);body.setMinHeight(dp(280));body.setTextSize(18);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        page.addView(body,new LinearLayout.LayoutParams(-1,-2));gap(page,12);
        page.addView(strong("ไฟล์แนบ",16));attachments=vertical();page.addView(attachments);
        if(note!=null) {title.setText(note.getAsString("title"));String rich=note.getAsString("rich");if(rich.isEmpty()) body.setText(note.getAsString("body"));else {
            Spanned formatted=Html.fromHtml(rich,Html.FROM_HTML_MODE_LEGACY);String plain=note.getAsString("body");
            body.setText(formatted.toString().startsWith(plain)?formatted.subSequence(0,plain.length()):formatted);
        }}
        editor=true;loading=false;refreshAttachments();
        TextWatcher change=watcher(this::changed);title.addTextChangedListener(change);body.addTextChangedListener(change);
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void changed() {
        if(loading||!editor) return;dirty=true;if(savedLabel!=null) savedLabel.setText("กำลังบันทึก…  ·  "+folder);
        handler.removeCallbacks(autosave);handler.postDelayed(autosave,650);
    }
    private boolean saveNote(boolean force) {
        handler.removeCallbacks(autosave);
        if(!editor||title==null||body==null) return true;
        if(!dirty&&noteId>=0) return true;
        String t=title.getText().toString(),b=body.getText().toString();
        if(noteId<0&&!force&&t.trim().isEmpty()&&b.trim().isEmpty()) return true;
        try {
            noteId=store.save(noteId,t,b,Html.toHtml(body.getText(),Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE),folder);dirty=false;
            savedLabel.setText("บันทึกแล้ว  ·  "+folder);return true;
        } catch(Exception e) {savedLabel.setText("บันทึกไม่สำเร็จ — ตรวจสอบพื้นที่ว่าง");toast("ยังบันทึกไม่ได้ กรุณาอย่าเพิ่งปิดโน้ต");return false;}
    }
    private void style(int type) {
        int start=body.getSelectionStart(),end=body.getSelectionEnd();
        if(start<0||start==end) {toast("เลือกข้อความที่ต้องการจัดรูปแบบก่อน");body.requestFocus();return;}
        int lo=Math.min(start,end),hi=Math.max(start,end);Editable e=body.getText();boolean removed=false;
        for(StyleSpan span:e.getSpans(lo,hi,StyleSpan.class)) if(span.getStyle()==type&&e.getSpanStart(span)<=lo&&e.getSpanEnd(span)>=hi) {
            int a=e.getSpanStart(span),b=e.getSpanEnd(span);e.removeSpan(span);
            if(a<lo) e.setSpan(new StyleSpan(type),a,lo,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(b>hi) e.setSpan(new StyleSpan(type),hi,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);removed=true;
        }
        if(!removed) e.setSpan(new StyleSpan(type),lo,hi,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);changed();
    }
    private void insertChecklist() {
        int cursor=Math.max(0,body.getSelectionStart());Editable e=body.getText();int start=e.toString().lastIndexOf('\n',Math.max(-1,cursor-1))+1;
        String prefix=e.subSequence(start,Math.min(e.length(),start+2)).toString();
        if(prefix.equals("☐ ")) e.replace(start,start+2,"☑ ");else if(prefix.equals("☑ ")) e.replace(start,start+2,"☐ ");else e.insert(start,"☐ ");
        body.requestFocus();
    }
    private void chooseFolder() {
        EditText field=input("ชื่อโฟลเดอร์",true);field.setText(folder);
        new AlertDialog.Builder(this).setTitle("โฟลเดอร์ของโน้ต").setView(field).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w) -> {
            String value=field.getText().toString().trim();folder=value.isEmpty()?"ทั่วไป":value;changed();saveNote(true);
        }).show();
    }
    private void attachmentMenu() {
        if(!saveNote(true)) return;keyboardOff();
        new AlertDialog.Builder(this).setTitle("แนบเอกสารกับโน้ต").setItems(new String[]{"นำเข้าไฟล์จากเครื่อง","เลือกจากเอกสารที่เก็บไว้"},(d,w) -> {
            if(w==0) pickDocument(noteId);
            else {
                List<ContentValues> docs=store.query("SELECT * FROM docs ORDER BY rowid DESC");
                if(docs.isEmpty()) {toast("ยังไม่มีเอกสาร กรุณานำเข้าจากเครื่องก่อน");return;}
                String[] names=new String[docs.size()];for(int i=0;i<names.length;i++) names[i]=docs.get(i).getAsString("name");
                new AlertDialog.Builder(this).setTitle("เลือกไฟล์แนบ").setItems(names,(dialog,index) -> {store.attach(noteId,docs.get(index).getAsString("id"));refreshAttachments();}).show();
            }
        }).show();
    }
    private void refreshAttachments() {
        if(attachments==null) return;attachments.removeAllViews();List<ContentValues> docs=store.attachments(noteId);
        if(docs.isEmpty()) attachments.addView(text("แนบ PDF รูปภาพ หรือเอกสารประกอบได้",14,MUTED));
        for(ContentValues doc:docs) documentCard(attachments,doc,true);
    }
    private void documentCard(LinearLayout parent,ContentValues doc,boolean attached) {
        String id=doc.getAsString("id"),name=doc.getAsString("name"),mime=doc.getAsString("mime");
        LinearLayout c=card(parent);c.addView(text(mime.equals("application/pdf")?"PDF  ·  อ่านและจดประกอบ":"เอกสารแนบ",12,GREEN));
        TextView heading=strong(name,17);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);c.addView(heading);
        c.addView(text(android.text.format.Formatter.formatFileSize(this,doc.getAsLong("size")),12,MUTED));
        LinearLayout buttons=horizontal(c);
        action(buttons,"เปิด",() -> openDocument(id,name,mime),false);
        action(buttons,"ส่งออก",() -> exportDocument(id,name,mime),false);
        action(buttons,attached?"เอาออก":"ลบ",() -> {
            if(attached) confirm("เอาไฟล์แนบออก?","ไฟล์ยังอยู่ในแท็บเอกสาร",() -> {store.detach(noteId,id);refreshAttachments();});
            else confirm("ลบเอกสารถาวร?","ไฟล์ บันทึกประกอบหน้า และการแนบกับทุกโน้ตจะถูกลบ กรุณาสำรองข้อมูลก่อน",() -> {store.eraseDoc(id);new File(getFilesDir(),id).delete();refresh();});
        },false);
    }
    private void openDocument(String id,String name,String mime) {
        if(!mime.equals("application/pdf")&&!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            new AlertDialog.Builder(this).setTitle(name).setMessage("ไฟล์นี้เก็บไว้แล้ว สามารถส่งออกเพื่อเปิดด้วยแอปที่รองรับได้").setNegativeButton("ปิด",null).setPositiveButton("ส่งออก",(d,w) -> exportDocument(id,name,mime)).show();return;
        }
        if(!saveNote(false)) return;long back=editor?noteId:-1;keyboardOff();openPdf(id,name,store.lastPage(id),back);
    }
    private void openPdf(String id,String name,int page,long backNote) {
        handler.removeCallbacks(autosave);editor=false;closePdf();pdfReturnNote=backNote;
        try {
            pdfFd=ParcelFileDescriptor.open(new File(getFilesDir(),id),ParcelFileDescriptor.MODE_READ_ONLY);pdf=new PdfRenderer(pdfFd);if(pdf.getPageCount()<1) throw new IOException();
            pdfId=id;pdfName=name;screen();titleBar(name,this::returnFromPdf);
            LinearLayout tools=horizontal(root);
            action(tools,"−",() -> pageView.zoomBy(0.8f),false);action(tools,"＋",() -> pageView.zoomBy(1.25f),false);
            action(tools,"บุ๊กมาร์ก",this::bookmarkMenu,false);
            pageView=new PdfPageView(this);root.addView(pageView,new LinearLayout.LayoutParams(-1,0,1));gap(root,8);
            LinearLayout navigation=horizontal(root);
            action(navigation,"‹ ก่อนหน้า",() -> renderPage(pageIndex-1),false);
            pageLabel=text("",14,INK);pageLabel.setGravity(Gravity.CENTER);pageLabel.setOnClickListener(v -> jumpPage());navigation.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(48),1));
            action(navigation,"ถัดไป ›",() -> renderPage(pageIndex+1),false);
            LinearLayout notes=horizontal(root);action(notes,"จดหน้านี้",this::pageNote,true);
            Button bookmark=button("☆ บันทึกหน้า",this::toggleBookmark,false);bookmarkButton=bookmark;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(3),dp(3),dp(3));notes.addView(bookmark,p);
            renderPage(Math.max(0,Math.min(page,pdf.getPageCount()-1)));
        } catch(Exception e) {returnFromPdf();toast("เปิด PDF ไม่ได้ ไฟล์อาจเสียหายหรือมีรหัสผ่าน");}
    }
    private void renderPage(int index) {
        if(pdf==null||index<0||index>=pdf.getPageCount()) return;
        try(PdfRenderer.Page p=pdf.openPage(index)) {
            float scale=Math.min(3f,Math.min(2200f/p.getWidth(),3000f/p.getHeight()));
            Bitmap next=Bitmap.createBitmap(Math.max(1,Math.round(p.getWidth()*scale)),Math.max(1,Math.round(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE);p.render(next,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);pageView.setPage(next);
            if(pageBitmap!=null) pageBitmap.recycle();pageBitmap=next;pageIndex=index;store.remember(pdfId,index);updatePageLabels();
        } catch(Exception|OutOfMemoryError e) {toast("แสดงหน้านี้ไม่ได้ ลองปิดแอปอื่นแล้วเปิดใหม่");}
    }
    private void updatePageLabels() {
        ContentValues page=store.page(pdfId,pageIndex);pageLabel.setText((pageIndex+1)+" / "+pdf.getPageCount());
        bookmarkButton.setText(page.getAsInteger("bookmarked")==1?"★ บันทึกแล้ว":"☆ บันทึกหน้า");
        pageView.setContentDescription("PDF หน้า "+(pageIndex+1)+" จาก "+pdf.getPageCount()+" หน้า ใช้สองนิ้วซูมและลากเลื่อน");
    }
    private void jumpPage() {
        EditText field=input("เลขหน้า",true);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setText(""+(pageIndex+1));
        new AlertDialog.Builder(this).setTitle("ไปที่หน้า").setView(field).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w) -> {
            try {int page=Integer.parseInt(field.getText().toString());if(page<1||page>pdf.getPageCount()) throw new IllegalArgumentException();renderPage(page-1);}catch(Exception e) {toast("กรุณาใส่เลขหน้าที่มีในเอกสาร");}
        }).show();
    }
    private void pageNote() {
        String doc=pdfId;int page=pageIndex;ContentValues record=store.page(doc,page);EditText field=input("สรุปสิ่งที่อ่าน คำถาม หรือสิ่งที่ต้องกลับมาทบทวน…",false);
        field.setMinLines(5);field.setGravity(Gravity.TOP);field.setText(record.getAsString("body"));
        field.addTextChangedListener(watcher(() -> {record.put("body",field.getText().toString());store.savePage(record);}));
        new AlertDialog.Builder(this).setTitle("บันทึกหน้า "+(page+1)+" · บันทึกอัตโนมัติ").setView(field).setPositiveButton("เสร็จ",(d,w) -> {keyboardOff();updatePageLabels();}).show();
    }
    private void toggleBookmark() {ContentValues record=store.page(pdfId,pageIndex);record.put("bookmarked",record.getAsInteger("bookmarked")==1?0:1);store.savePage(record);updatePageLabels();}
    private void bookmarkMenu() {
        List<ContentValues> rows=store.query("SELECT * FROM page_notes WHERE doc_id=? AND (bookmarked=1 OR body!='') ORDER BY page",pdfId);
        if(rows.isEmpty()) {toast("แตะบันทึกหน้าหรือจดหน้านี้ เพื่อกลับมาเปิดได้ง่าย");return;}
        String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++) {ContentValues r=rows.get(i);String b=r.getAsString("body");labels[i]=(r.getAsInteger("bookmarked")==1?"★ ":"")+"หน้า "+(r.getAsInteger("page")+1)+(b.isEmpty()?"":" · "+b.substring(0,Math.min(45,b.length())));}
        new AlertDialog.Builder(this).setTitle("หน้าที่บันทึกและโน้ตประกอบ").setItems(labels,(d,w) -> renderPage(rows.get(w).getAsInteger("page"))).show();
    }
    private void returnFromPdf() {long back=pdfReturnNote;closePdf();pdfReturnNote=-1;if(back>=0) edit(back);else home();}
    private void closePdf() {
        if(pageView!=null) {pageView.setPage(null);pageView=null;}
        if(pageBitmap!=null) {pageBitmap.recycle();pageBitmap=null;}
        if(pdf!=null) {pdf.close();pdf=null;}
        if(pdfFd!=null) {try {pdfFd.close();}catch(IOException ignored) {}pdfFd=null;}pdfId=null;
    }
    private void settings() {
        LinearLayout page=scroll(root,1);LinearLayout about=card(page);
        about.addView(strong("ข้อมูลของคุณอยู่กับคุณ",21));about.addView(text("โน้ตและไฟล์อยู่ในเครื่อง ไม่ต้องสมัครบัญชี และใช้งานได้โดยไม่ต่ออินเทอร์เน็ต",15,MUTED));
        LinearLayout backup=card(page);backup.addView(strong("สำรองและกู้คืน",20));
        backup.addView(text("เก็บโน้ต ไฟล์แนบ บุ๊กมาร์ก และบันทึก PDF เป็นไฟล์เดียว ควรสำรองก่อนเปลี่ยนเครื่องหรือถอนแอป",14,MUTED));gap(backup,8);
        backup.addView(button("สำรองข้อมูลทั้งหมด",() -> {
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"NotePocket-"+new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date())+".zip");startActivityForResult(i,BACKUP);
        },true));gap(backup,8);
        backup.addView(button("กู้คืนจากไฟล์สำรอง",() -> confirm("เพิ่มข้อมูลจากไฟล์สำรอง?","โน้ตเดิมยังคงอยู่ การนำเข้าไฟล์เดิมซ้ำจะเพิ่มสำเนาใหม่",() -> {
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,RESTORE);
        }),false));
        LinearLayout info=card(page);info.addView(strong("NotePocket "+BuildConfig.VERSION_NAME,20));
        info.addView(text("รุ่น "+BuildConfig.VERSION_CODE+" · รองรับ Android 8 ขึ้นไป\nอัปเดตโดยติดตั้ง APK ที่ลงนามด้วยกุญแจเดิมทับได้ ข้อมูลจะยังอยู่",14,MUTED));
        info.addView(button("เปิดหน้าดาวน์โหลดและอัปเดต",() -> startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/pharmdever/NotePocket/actions"))),false));
        LinearLayout help=card(page);help.addView(strong("เกี่ยวกับ PDF",18));help.addView(text("จดหน้านี้ ใช้เขียนบันทึกประกอบเอกสาร โดยเก็บแยกจากต้นฉบับ PDF\nรุ่นนี้ยังไม่มีปากกา ไฮไลต์ หรือแก้ข้อความเดิมใน PDF\nไฟล์สำรองมีข้อมูลส่วนตัว กรุณาเก็บในที่ปลอดภัย",14,MUTED));
    }
    private void pickDocument(long note) {
        pendingAttach=note;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK);
    }
    private void exportDocument(String id,String name,String mime) {
        if(!saveNote(false)) return;exportId=id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,EXPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        long attach=pendingAttach;if(request==PICK) pendingAttach=-1;
        if(result!=RESULT_OK||data==null||data.getData()==null) return;
        Uri uri=data.getData();
        if(request==PICK) runIo("กำลังเก็บไฟล์…",() -> {
            String name="เอกสาร",mime=getContentResolver().getType(uri);
            try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {if(c!=null&&c.moveToFirst()&&!c.isNull(0)) name=c.getString(0);}
            if(mime==null) mime=name.toLowerCase(Locale.ROOT).endsWith(".pdf")?"application/pdf":"application/octet-stream";
            String id=UUID.randomUUID().toString();File file=new File(getFilesDir(),id);
            try {
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)) {if(in==null) throw new IOException();Backup.copy(in,out,512L*1024*1024);}
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try {
                    ContentValues doc=new ContentValues();doc.put("id",id);doc.put("name",name);doc.put("mime",mime);doc.put("size",file.length());db.insertOrThrow("docs",null,doc);
                    if(attach>=0&&store.note(attach)!=null) store.attach(attach,id);db.setTransactionSuccessful();
                } finally {db.endTransaction();}
            } catch(Exception e) {file.delete();throw e;}
            return "เก็บไฟล์แล้ว";
        },() -> {if(editor) refreshAttachments();else {tab="files";query="";home();}});
        else if(request==EXPORT&&exportId!=null) {
            File source=new File(getFilesDir(),exportId);
            runIo("กำลังส่งออก…",() -> {try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {if(out==null) throw new IOException();Backup.copy(in,out,1024L*1024*1024);}return "ส่งออกไฟล์แล้ว";},() -> {});
        } else if(request==BACKUP) runIo("กำลังสำรองข้อมูล…",() -> {
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {if(out==null) throw new IOException();Backup.write(this,store,out);}return "สำรองข้อมูลแล้ว กรุณาเก็บไฟล์นี้ไว้";
        },() -> {});
        else if(request==RESTORE) runIo("กำลังกู้คืนข้อมูล…",() -> {
            try(InputStream in=getContentResolver().openInputStream(uri)) {if(in==null) throw new IOException();return "กู้คืนแล้ว "+Backup.restore(this,store,in)+" โน้ต";}
        },this::home);
    }
    private interface IoTask { String run() throws Exception; }
    private void runIo(String message,IoTask task,Runnable success) {
        if(busy) return;if(!saveNote(false)) return;busy=true;keyboardOff();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        progress=ProgressDialog.show(this,"",message,true,false);
        new Thread(() -> {
            String result;boolean ok;
            try {result=task.run();ok=true;}catch(Exception e) {result="ทำรายการไม่สำเร็จ ตรวจสอบไฟล์และพื้นที่ว่าง (หากเป็นไฟล์ส่งออก อาจต้องลบไฟล์ที่บันทึกไม่ครบแล้วลองใหม่)";ok=false;}
            final String output=result;final boolean completed=ok;
            runOnUiThread(() -> {
                busy=false;if(progress!=null) {progress.dismiss();progress=null;}
                if(isDestroyed()||isFinishing()) {store.close();return;}
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                if(completed) success.run();toast(output);
            });
        },"NotePocket-IO").start();
    }
    @Override protected void onPause() {saveNote(false);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state) {
        saveNote(false);state.putString("tab",tab);state.putString("filter",filter);state.putString("query",query);state.putBoolean("editor",editor);state.putLong("noteId",noteId);
        state.putLong("pendingAttach",pendingAttach);state.putString("exportId",exportId);
        if(pdf!=null) {state.putString("pdfId",pdfId);state.putString("pdfName",pdfName);state.putInt("page",pageIndex);state.putLong("pdfReturnNote",pdfReturnNote);}
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if(busy) return;
        if(pdf!=null) returnFromPdf();else if(editor) {if(saveNote(false)) {keyboardOff();home();}}
        else if(!tab.equals("notes")||!filter.equals("all")) {tab="notes";filter="all";query="";home();}else super.onBackPressed();
    }
    @Override protected void onDestroy() {handler.removeCallbacks(autosave);closePdf();if(progress!=null) {progress.dismiss();progress=null;}if(!busy) store.close();super.onDestroy();}
}
