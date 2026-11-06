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
    private static final int GREEN=PocketTheme.FOREST,INK=PocketTheme.INK,MUTED=PocketTheme.MUTED,BG=PocketTheme.CREAM,LINE=PocketTheme.LINE;
    private static final int PICK=10,EXPORT=11,BACKUP=12,RESTORE=13,ANNOTATED=14,FONT=15,CSV=16;
    private Store store;
    private LibraryList library;
    private boolean libraryReleased;
    private EditorSaver noteSaver;
    private long noteRevision,editorEpoch;
    private String expandedTableId="";
    private final Map<String,Integer> tablePages=new HashMap<>();
    private final Runnable cursorFollower=this::followNoteCursorNow;
    private LinearLayout root,list,attachments;
    private EditText title,body;
    private LinearLayout noteHeader,noteToolbar,noteTables,noteToolsRow;
    private ReaderIconButton recentTool;
    private SoapDraft soapDraft=new SoapDraft();
    private AlertDialog activeSoapDialog;
    private String focusedTable="";
    private int focusedRow,focusedColumn;
    private ScrollView noteScroll;
    private TextView noteToolStatus;
    private ReaderIconButton noteUndo,noteRedo;
    private List<NoteTable> tables=new ArrayList<>();
    private NoteHistory noteHistory=new NoteHistory();
    private final Map<String,ReaderIconButton> noteToolIcons=new HashMap<>();
    private TextView savedLabel,pageLabel,bookmarkButton,inkStatus;
    private Button readMode,penMode,highlightMode;
    private String annotatedExportId;
    private LinearLayout readerHeader,readerFooter;
    private FrameLayout pdfStage;
    private PopupWindow paletteWindow;
    private ActionMode selectionAction;
    private boolean fullscreen=false;
    private java.util.function.Supplier<TextBox> textDraft;
    private AlertDialog activeTextDialog;
    private LinearLayout toolDock,historyStrip;
    private TextView toolStatus;
    private ReaderIconButton[] modeIcons;
    private ReaderIconButton undoIcon,redoIcon,previousIcon,nextIcon,moreIcon,collapsedTools;
    private String tab="notes",filter="all",query="",folder="ทั่วไป",pdfId,pdfName,exportId;
    private long noteId=-1,pendingAttach=-1,pdfReturnNote=-1;
    private boolean editor=false,loading=false,dirty=false,busy=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable autosave=this::queueSave;
    private PdfRenderer pdf;
    private ParcelFileDescriptor pdfFd;
    private Bitmap pageBitmap;
    private PdfPageView pageView;
    private int pageIndex,pdfPages;
    private boolean pdfRendering,pdfImageReleased;
    private volatile int pdfGeneration;
    private final java.util.concurrent.ExecutorService pdfWorker=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"NotePocket-PDF"));
    private ProgressDialog progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);if(Build.VERSION.SDK_INT>=29) {android.view.contentcapture.ContentCaptureManager capture=getSystemService(android.view.contentcapture.ContentCaptureManager.class);if(capture!=null) capture.setContentCaptureEnabled(false);}store=new Store(getApplicationContext());store.getWritableDatabase();
        if(state!=null) {
            tab=state.getString("tab","notes");filter=state.getString("filter","all");query=state.getString("query","");
            pendingAttach=state.getLong("pendingAttach",-1);exportId=state.getString("exportId");annotatedExportId=state.getString("annotatedExportId");
            if(state.getString("pdfId")!=null) {pdfReturnNote=state.getLong("pdfReturnNote",-1);openPdf(state.getString("pdfId"),state.getString("pdfName","PDF"),state.getInt("page",0),pdfReturnNote);if(pdf!=null) {inkMode(state.getInt("pdfMode",0)==PdfPageView.SELECT?PdfPageView.READ:state.getInt("pdfMode",0));setFullscreen(state.getBoolean("fullscreen"));String draft=state.getString("textDraft");if(draft!=null) handler.post(()->{TextBox box=TextBox.decode(draft).get(0);editTextBox(box,box.x,box.y);});}return;}
            if(state.getBoolean("editor")) {edit(state.getLong("noteId",-1));
                if(editor) {int start=Math.max(0,Math.min(state.getInt("noteStart",0),body.length())),end=Math.max(0,Math.min(state.getInt("noteEnd",start),body.length()));body.setSelection(start,end);expandedTableId=state.getString("expandedTable",expandedTableId);tablePages.put(expandedTableId,state.getInt("tablePage",0));renderNoteTables();final String focusTag=state.getString("tableFocus");if(focusTag!=null) noteTables.post(()->{View field=noteTables.findViewWithTag(focusTag);if(field!=null) field.requestFocus();});final int y=state.getInt("noteScroll",0);noteScroll.post(()->noteScroll.scrollTo(0,y));if(state.getBoolean("soapOpen")) handler.post(this::soapEditor);}return; }
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
        b.setBackground(PocketTheme.ripple(this,primary?GREEN:PocketTheme.SAGE,16));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setStateListAnimator(null);
        b.setOnClickListener(v -> { if(!busy) action.run(); });return b;
    }
    private void gap(LinearLayout parent,int size) { Space v=new Space(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size))); }
    private LinearLayout vertical() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout horizontal(LinearLayout parent) { LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);if(parent!=null) parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v; }
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
        LinearLayout c=vertical();c.setPadding(dp(17),dp(13),dp(17),dp(13));c.setBackground(PocketTheme.shape(this,PocketTheme.SURFACE,22,true));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));parent.addView(c,p);return c;
    }
    /** Note fields are documents, not autofill forms. NO alone is advisory on Android 15. */
    private static final class PocketInput extends EditText {
        PocketInput(Context context) {super(context);setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);if(Build.VERSION.SDK_INT>=30) setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);}
        @Override public int getAutofillType() {return View.AUTOFILL_TYPE_NONE;}
        @Override public android.view.autofill.AutofillValue getAutofillValue() {return null;}
    }
    private EditText input(String hint,boolean single) {
        EditText e=new PocketInput(this);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(17);e.setSingleLine(single);e.setMinHeight(dp(52));e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(PocketTheme.field(this));return e;
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void keyboardOff() { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0); }
    private void titleBar(String heading,Runnable back) {
        LinearLayout row=horizontal(root);row.addView(button("‹",back,false),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView t=strong(heading,20);t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(12),0,0,0);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));gap(root,12);
    }
    private void home() {
        if(editor&&!saveNote(false)) return;
        handler.removeCallbacks(autosave);if(library!=null) {library.close();library=null;}closePdf();editor=false;releaseEditorViews();pdfReturnNote=-1;screen();
        LinearLayout heading=horizontal(root);ReaderIconButton brand=icon("read","NotePocket",null,()->{});brand.setSelected(true);brand.setClickable(false);brand.setFocusable(false);brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);heading.addView(brand,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView wordmark=strong("NotePocket",26);wordmark.setPadding(dp(10),0,0,0);heading.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(pill("● ออฟไลน์",PocketTheme.SAGE,GREEN));
        root.addView(text(tab.equals("files")?"เอกสารพร้อมอ่าน ใกล้โน้ตของคุณ":tab.equals("settings")?"พื้นที่ของคุณ ในแบบที่คุณเลือก":"ทุกความคิด อยู่ใกล้มือ",14,MUTED));gap(root,12);
        if(tab.equals("settings")) settings();
        else {
            LinearLayout searchBox=horizontal(root);searchBox.setPadding(dp(6),0,dp(8),0);searchBox.setBackground(PocketTheme.shape(this,PocketTheme.SURFACE,18,true));ReaderIconButton searchGlyph=icon("find","ค้นหา",null,()->{});searchGlyph.setClickable(false);searchGlyph.setFocusable(false);searchGlyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchBox.addView(searchGlyph,new LinearLayout.LayoutParams(dp(40),dp(40)));
            EditText search=input(tab.equals("files")?"ค้นหาเอกสาร…":"ค้นหาในชื่อและเนื้อหา…",true);search.setTextSize(15);search.setBackgroundColor(Color.TRANSPARENT);search.setText(query);searchBox.addView(search,new LinearLayout.LayoutParams(0,dp(52),1));gap(root,10);
            LinearLayout commands=horizontal(root);
            if(tab.equals("files")) action(commands,"＋ นำเข้าไฟล์",() -> pickDocument(-1),true);
            else {
                action(commands,"＋ เขียนโน้ต",() -> edit(-1),true);
                action(commands,filter.equals("all")?"ทั้งหมด ▾":filter.equals("pinned")?"ปักหมุด ▾":filter.equals("trash")?"ถังขยะ ▾":(filter.startsWith("folder:")?filter.substring(7):filter)+" ▾",this::chooseFilter,false);
            }
            if(tab.equals("notes")) {LinearLayout helpers=horizontal(root);quickAction(helpers,"folder","โฟลเดอร์",PocketTheme.BUTTER,()->manageFolders(false));quickAction(helpers,"soap","＋ SOAP note",PocketTheme.LAVENDER,()->{edit(-1);soapEditor();});}
            library=new LibraryList(this,tab.equals("files"),filter,query,this::bindLibraryRow);root.addView(library,new LinearLayout.LayoutParams(-1,0,1));
            search.addTextChangedListener(watcher(()->{query=search.getText().toString();if(library!=null) library.search(query);}));
        }
        gap(root,8);LinearLayout nav=horizontal(root);nav.setPadding(dp(4),dp(4),dp(4),dp(4));nav.setBackground(PocketTheme.shape(this,PocketTheme.SURFACE,24,true));
        for(String key:new String[]{"notes","files","settings"}) {
            String label=key.equals("notes")?"โน้ต":key.equals("files")?"เอกสาร":"ตั้งค่า";LinearLayout slot=vertical();slot.setGravity(Gravity.CENTER);slot.setPadding(0,dp(4),0,dp(4));if(tab.equals(key)) slot.setBackground(background(PocketTheme.SAGE,18));
            ReaderIconButton glyph=icon(key.equals("notes")?"read":key.equals("files")?"files":"settings",label,null,()->{});glyph.setClickable(false);glyph.setFocusable(false);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);slot.addView(glyph,new LinearLayout.LayoutParams(dp(36),dp(32)));
            TextView caption=text(label,12,tab.equals(key)?GREEN:MUTED);caption.setGravity(Gravity.CENTER);caption.setTypeface(null,tab.equals(key)?Typeface.BOLD:Typeface.NORMAL);slot.addView(caption);slot.setOnClickListener(v->{if(!busy) {tab=key;query="";home();}});slot.setFocusable(true);slot.setContentDescription(label);caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);nav.addView(slot,new LinearLayout.LayoutParams(0,dp(64),1));
        }
    }
    private TextView pill(String label,int color,int ink) {TextView view=text(label,12,ink);view.setPadding(dp(10),dp(5),dp(10),dp(5));view.setBackground(background(color,12));view.setSingleLine();view.setEllipsize(TextUtils.TruncateAt.END);return view;}
    private void quickAction(LinearLayout parent,String symbol,String label,int color,Runnable action) {
        LinearLayout row=horizontal(null);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(6),dp(3),dp(10),dp(3));row.setBackground(PocketTheme.ripple(this,color,18));ReaderIconButton glyph=icon(symbol,label,null,()->{});glyph.setClickable(false);glyph.setFocusable(false);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(glyph,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView text=text(label,14,INK);text.setTypeface(null,Typeface.BOLD);text.setGravity(Gravity.CENTER);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->{if(!busy) action.run();});row.setContentDescription(label);row.setFocusable(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(row,p);
    }
    private TextWatcher watcher(Runnable r) { return new TextWatcher() {
        public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
        public void onTextChanged(CharSequence s,int start,int before,int count) { r.run(); }
        public void afterTextChanged(Editable e) {}
    }; }
    private void chooseFilter() {
        List<String> values=new ArrayList<>(Arrays.asList("all","pinned","trash"));List<String> labels=new ArrayList<>(Arrays.asList("โน้ตทั้งหมด","ปักหมุด","ถังขยะ"));
        for(ContentValues r:store.folders()) {values.add("folder:"+r.getAsString("name"));labels.add("โฟลเดอร์: "+r.getAsString("name")+" ("+r.getAsInteger("count")+")");}
        new PocketDialog.Builder(this).setTitle("แสดงโน้ต").setItems(labels.toArray(new String[0]),(d,w) -> {filter=values.get(w);home();}).show();
    }
    private void refresh() {if(library!=null) library.reload();}
    private static final class LibraryRow {
        boolean files;ContentValues data;LinearLayout outer,panel,actions;TextView tag,title,preview,meta;ReaderIconButton more;
    }
    private View bindLibraryRow(ContentValues data,View recycled,boolean files) {
        LibraryRow row=recycled!=null&&recycled.getTag() instanceof LibraryRow?(LibraryRow)recycled.getTag():null;
        if(row==null) {row=new LibraryRow();row.files=files;row.outer=vertical();row.panel=card(row.outer);row.panel.setPadding(dp(16),dp(10),dp(16),dp(12));LinearLayout top=horizontal(row.panel);row.tag=pill("",PocketTheme.SURFACE,GREEN);row.tag.setMaxWidth(dp(200));top.addView(row.tag,new LinearLayout.LayoutParams(-2,-2));top.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));LibraryRow holder=row;
            row.more=icon("more","ตัวเลือกโน้ต",null,()->noteMenu(holder.data.getAsLong("id")));top.addView(row.more,new LinearLayout.LayoutParams(dp(48),dp(48)));
            row.title=strong("",20);row.title.setMaxLines(2);row.title.setEllipsize(TextUtils.TruncateAt.END);row.panel.addView(row.title);row.preview=text("",15,MUTED);row.preview.setMaxLines(2);row.preview.setEllipsize(TextUtils.TruncateAt.END);row.panel.addView(row.preview);row.meta=text("",11,MUTED);row.panel.addView(row.meta);row.actions=horizontal(row.panel);
            if(files) {action(row.actions,"เปิด",()->openDocument(holder.data.getAsString("id"),holder.data.getAsString("name"),holder.data.getAsString("mime")),false);action(row.actions,"ส่งออก",()->exportDocument(holder.data.getAsString("id"),holder.data.getAsString("name"),holder.data.getAsString("mime")),false);action(row.actions,"ลบ",()->confirm("ลบเอกสารถาวร?","ไฟล์ บันทึกประกอบหน้า และการแนบกับทุกโน้ตจะถูกลบ กรุณาสำรองข้อมูลก่อน",()->{String id=holder.data.getAsString("id");store.eraseDoc(id);new File(getFilesDir(),id).delete();refresh();}),false);}
            else {action(row.actions,"กู้คืน",()->{store.flag(holder.data.getAsLong("id"),"deleted",0);refresh();},false);action(row.actions,"ลบถาวร",()->confirm("ลบโน้ตถาวร?","ไฟล์แนบยังคงอยู่ในเอกสาร แต่ข้อความจะกู้คืนไม่ได้",()->{store.eraseNote(holder.data.getAsLong("id"));refresh();}),false);row.panel.setOnClickListener(v->{if(!busy&&holder.data.getAsInteger("deleted")==0) edit(holder.data.getAsLong("id"));});}
            row.outer.setTag(row);
        }
        row.data=data;row.more.setVisibility(files||data.getAsInteger("deleted")==1?View.GONE:View.VISIBLE);
        if(files) {row.panel.setBackground(PocketTheme.shape(this,PocketTheme.SURFACE,22,true));row.tag.setText(data.getAsString("mime").equals("application/pdf")?"PDF  ·  อ่านและจดประกอบ":"เอกสารแนบ");row.title.setText(data.getAsString("name"));row.preview.setVisibility(View.GONE);row.meta.setText(android.text.format.Formatter.formatFileSize(this,data.getAsLong("size")));row.actions.setVisibility(View.VISIBLE);}
        else {boolean deleted=data.getAsInteger("deleted")==1;int tint=PocketTheme.NOTES[Math.floorMod(Long.hashCode(data.getAsLong("id")),PocketTheme.NOTES.length)];row.panel.setBackground(background(deleted?PocketTheme.SURFACE:tint,22));row.tag.setText((data.getAsInteger("pinned")==1?"★ ":"")+data.getAsString("folder"));row.title.setText(data.getAsString("title"));row.preview.setVisibility(View.VISIBLE);row.preview.setText(data.getAsString("preview"));int count=data.getAsInteger("attachments");row.meta.setText(date(data.getAsLong("updated"))+(count>0?"  ·  "+count+" ไฟล์แนบ":""));row.more.setContentDescription("ตัวเลือกโน้ต: "+data.getAsString("title"));row.actions.setVisibility(deleted?View.VISIBLE:View.GONE);}
        return row.outer;
    }
    private String date(long time) { return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time)); }
    private void confirm(String heading,String message,Runnable action) { new PocketDialog.Builder(this).setTitle(heading).setMessage(message).setNegativeButton("ยกเลิก",null).setPositiveButton("ยืนยัน",(d,w) -> action.run()).show(); }
    private void noteMenu(long id) {
        List<ContentValues> metadata=store.query("SELECT id,substr(title,1,240) AS title,pinned,deleted FROM notes WHERE id=?",""+id);if(metadata.isEmpty()) return;ContentValues n=metadata.get(0);
        new PocketDialog.Builder(this).setTitle(n.getAsString("title")).setItems(new String[]{n.getAsInteger("pinned")==1?"เลิกปักหมุด":"ปักหมุด","ย้ายเข้าถังขยะ"},(d,w) -> {
            if(w==0) {store.flag(id,"pinned",n.getAsInteger("pinned")==1?0:1);refresh();}
            else confirm("ย้ายเข้าถังขยะ?","สามารถกู้คืนได้จากตัวกรองถังขยะ",() -> {store.flag(id,"deleted",1);refresh();});
        }).show();
    }
    private void releaseEditorViews() {if(noteSaver!=null) {noteSaver.close();noteSaver=null;}handler.removeCallbacks(cursorFollower);title=body=null;noteHeader=noteToolbar=noteTables=noteToolsRow=null;noteScroll=null;noteToolStatus=null;noteUndo=noteRedo=recentTool=null;noteToolIcons.clear();noteHistory.clear();tables=new ArrayList<>();soapDraft=new SoapDraft();}
    private void edit(long id) {
        if(editor&&!saveNote(false)) return;if(library!=null) {library.close();library=null;}if(noteSaver!=null) noteSaver.close();noteRevision=0;final long epoch=++editorEpoch;expandedTableId="";tablePages.clear();
        noteSaver=new EditorSaver(store,id,(revision,saved,failure)->{if(isDestroyed()||epoch!=editorEpoch||!editor) return;if(failure==null) {noteId=saved;if(revision==noteRevision) {dirty=false;savedLabel.setText("บันทึกแล้ว  ·  "+folder);}}else {savedLabel.setText("บันทึกไม่สำเร็จ — ตรวจสอบพื้นที่ว่าง");}});
        closePdf();pdfReturnNote=-1;editor=false;loading=true;noteId=id;dirty=false;
        ContentValues note=id<0?null:store.note(id);
        if(id>=0&&(note==null||note.getAsInteger("deleted")==1)) {home();toast("ไม่พบโน้ตนี้");return;}
        folder=note==null?(filter.startsWith("folder:")?filter.substring(7):"ทั่วไป"):note.getAsString("folder");soapDraft=note==null?new SoapDraft():new SoapDraft(note.getAsString("soap"));screen();root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);if(Build.VERSION.SDK_INT>=30) root.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        noteHistory=new NoteHistory();noteToolIcons.clear();tables=note==null?new ArrayList<>():NoteTable.decode(note.getAsString("tables"));
        noteHeader=horizontal(root);noteHeader.addView(icon("back","กลับจากโน้ต",null,()->{if(saveNote(false)) {keyboardOff();home();}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=strong("เขียนโน้ต",20);noteHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        noteHeader.addView(icon("attach","แนบไฟล์",null,this::attachmentMenu),new LinearLayout.LayoutParams(dp(48),dp(48)));
        noteHeader.addView(icon("folder","โฟลเดอร์",null,this::chooseFolder),new LinearLayout.LayoutParams(dp(48),dp(48)));
        noteHeader.addView(icon("share","แชร์",null,this::shareNote),new LinearLayout.LayoutParams(dp(48),dp(48)));
        savedLabel=text("บันทึกอัตโนมัติ  ·  "+folder,12,MUTED);root.addView(savedLabel);
        noteScroll=new ScrollView(this);noteScroll.setFillViewport(true);LinearLayout page=vertical();noteScroll.addView(page);root.addView(noteScroll,new LinearLayout.LayoutParams(-1,0,1));
        title=input("ชื่อโน้ต",true);title.setTextSize(25);title.setTypeface(null,Typeface.BOLD);page.addView(title);gap(page,10);
        body=input("เขียนสิ่งที่อยากจดจำ…",false);body.setSingleLine(false);body.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);body.setGravity(Gravity.TOP);body.setMinHeight(dp(280));body.setTextSize(18);body.setTypeface(Fonts.typeface(this,readerPrefs().getString("font","sans")));
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        page.addView(body,new LinearLayout.LayoutParams(-1,-2));gap(page,12);
        noteTables=vertical();page.addView(noteTables);renderNoteTables();
        page.addView(strong("ไฟล์แนบ",16));attachments=vertical();page.addView(attachments);
        if(note!=null) {title.setText(note.getAsString("title"));body.setText(NoteFormat.decode(note.getAsString("rich"),note.getAsString("body")));}
        createNoteToolbar();editor=true;loading=false;refreshAttachments();updateRecentTool();
        TextWatcher change=new TextWatcher() {
            private int at,before,count;
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {rememberNote(true);}
            public void onTextChanged(CharSequence value,int start,int before,int count) {at=start;this.before=before;this.count=count;changed();}
            public void afterTextChanged(Editable value) {if(!loading&&value==body.getText()&&count==1&&before==0) {loading=true;try {NoteLists.afterEnter(value,at,before,count);}finally {loading=false;}}updateNoteTools();followNoteCursor();}
        };title.addTextChangedListener(change);body.addTextChangedListener(change);
        body.setCustomSelectionActionModeCallback(new ActionMode.Callback() {public boolean onCreateActionMode(ActionMode m,Menu menu) {return true;}public boolean onPrepareActionMode(ActionMode m,Menu menu) {updateNoteTools();return false;}public boolean onActionItemClicked(ActionMode m,MenuItem item) {return false;}public void onDestroyActionMode(ActionMode m) {handler.post(MainActivity.this::updateNoteTools);}});
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int bottom=insets.getSystemWindowInsetBottom();boolean keyboard=false;
            if(android.os.Build.VERSION.SDK_INT>=30) {bottom=Math.max(insets.getInsets(WindowInsets.Type.ime()).bottom,insets.getInsets(WindowInsets.Type.systemBars()).bottom);keyboard=insets.isVisible(WindowInsets.Type.ime());}
            else keyboard=bottom>dp(100);
            view.setPadding(dp(10),insets.getSystemWindowInsetTop()+dp(4),dp(10),bottom+dp(4));
            boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            noteHeader.setVisibility(keyboard&&landscape?View.GONE:View.VISIBLE);savedLabel.setVisibility(keyboard?View.GONE:View.VISIBLE);
            if(noteToolStatus!=null) noteToolStatus.setVisibility(keyboard&&landscape?View.GONE:View.VISIBLE);
            followNoteCursor();return insets;
        });root.requestApplyInsets();updateNoteTools();
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void changed() {
        if(loading||!editor) return;noteRevision++;dirty=true;if(savedLabel!=null) savedLabel.setText("กำลังบันทึก…  ·  "+folder);
        handler.removeCallbacks(autosave);handler.postDelayed(autosave,650);
    }
    private NoteSnapshot captureNote() {return new NoteSnapshot(title.getText().toString(),body.getText(),folder,tables,soapDraft.json(),noteRevision);}
    private boolean blankNewNote() {return noteSaver!=null&&noteSaver.noteId<0&&!noteSaver.isRunning()&&title.getText().toString().trim().isEmpty()&&body.length()==0&&tables.isEmpty()&&soapDraft.isEmpty();}
    private void queueSave() {if(!editor||loading||!dirty||noteSaver==null||blankNewNote()) return;noteSaver.offer(captureNote());}
    private boolean saveNote(boolean force) {
        handler.removeCallbacks(autosave);if(!editor||title==null||body==null||noteSaver==null) return true;if(!dirty&&noteSaver.noteId>=0) {noteId=noteSaver.noteId;return true;}if(!force&&blankNewNote()) return true;
        if(noteSaver.flush(captureNote())) {noteId=noteSaver.noteId;dirty=false;savedLabel.setText("บันทึกแล้ว  ·  "+folder);return true;}
        savedLabel.setText("บันทึกไม่สำเร็จ — ตรวจสอบพื้นที่ว่าง");toast("ยังบันทึกไม่ได้ กรุณาอย่าเพิ่งปิดโน้ต");return false;
    }
    private void style(int type) {
        int start=body.getSelectionStart(),end=body.getSelectionEnd();
        if(!body.hasFocus()||start<0||start==end) {toast("เลือกข้อความที่ต้องการจัดรูปแบบก่อน");body.requestFocus();return;}
        rememberNote(false);NoteFormat.toggleStyle(body.getText(),Math.min(start,end),Math.max(start,end),type);changed();updateNoteTools();
    }

    private void insertChecklist() {
        body.requestFocus();int cursor=Math.max(0,body.getSelectionStart());Editable e=body.getText();int start=e.toString().lastIndexOf('\n',Math.max(-1,cursor-1))+1;
        String prefix=e.subSequence(start,Math.min(e.length(),start+2)).toString();
        if(prefix.equals("☐ ")) e.replace(start,start+2,"☑ ");else if(prefix.equals("☑ ")) e.replace(start,start+2,"☐ ");else e.insert(start,"☐ ");
        body.requestFocus();
    }
    private void shareNote() {
        if(!saveNote(false)) return;Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,title.getText()+"\n\n"+body.getText()+NoteTable.plain(tables));startActivity(Intent.createChooser(intent,"แชร์ข้อความโน้ต"));
    }
    private NoteHistory.State noteState() {return new NoteHistory.State(title.getText().toString(),body.getText(),tables,soapDraft.json(),body.getSelectionStart(),body.getSelectionEnd());}
    private void rememberNote(boolean typing) {if(editor&&!loading&&body!=null) noteHistory.remember(this::noteState,typing);}
    private void restoreNoteHistory(boolean redo) {
        NoteHistory.State state=redo?noteHistory.redo(noteState()):noteHistory.undo(noteState());if(state==null) return;
        loading=true;title.setText(state.title);body.setText(state.body);tables=NoteTable.copy(state.tables);soapDraft=new SoapDraft(state.soap);renderNoteTables();
        body.setSelection(Math.max(0,Math.min(state.start,body.length())),Math.max(0,Math.min(state.end,body.length())));loading=false;changed();updateNoteTools();body.requestFocus();followNoteCursor();
    }
    private void createNoteToolbar() {
        noteToolbar=vertical();noteToolbar.setBackground(background(Color.WHITE,16));noteToolbar.setPadding(dp(4),dp(3),dp(4),dp(3));noteToolbar.setElevation(dp(6));root.addView(noteToolbar,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout status=horizontal(noteToolbar);noteToolStatus=text("เลือกข้อความเพื่อจัดรูปแบบ · เลื่อนเครื่องมือ ↔",12,MUTED);noteToolStatus.setSingleLine();status.addView(noteToolStatus,new LinearLayout.LayoutParams(0,-2,1));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(true);scroll.setHorizontalFadingEdgeEnabled(true);LinearLayout row=horizontal(null);noteToolsRow=row;scroll.addView(row);noteToolbar.addView(scroll,new LinearLayout.LayoutParams(-1,dp(58)));
        noteUndo=noteTool(row,"undo","ย้อนกลับข้อความ","ย้อนกลับ",()->restoreNoteHistory(false));noteRedo=noteTool(row,"redo","ทำซ้ำข้อความ","ทำซ้ำ",()->restoreNoteHistory(true));
        noteTool(row,"bold","ตัวหนา","หนา",()->style(Typeface.BOLD));noteTool(row,"italic","ตัวเอียง","เอียง",()->style(Typeface.ITALIC));
        noteTool(row,"check","เช็กลิสต์","เช็กลิสต์",this::insertChecklist);noteTool(row,"table","แทรกตาราง","ตาราง",this::insertNoteTable);
        noteTool(row,"soap","ตัวช่วย SOAP note","SOAP",this::soapEditor);
        noteTool(row,"underline","ขีดเส้นใต้","เส้นใต้",()->formatNote("underline",0));noteTool(row,"strike","ขีดฆ่า","ขีดฆ่า",()->formatNote("strike",0));
        noteTool(row,"heading","หัวข้อ","หัวข้อ",()->formatNote("size",26));noteTool(row,"size","ขนาดข้อความ","ขนาด",this::noteSize);
        noteTool(row,"color","สีข้อความ","สี",()->noteColor(false));noteTool(row,"highlight","ไฮไลต์ข้อความ","ไฮไลต์",()->noteColor(true));
        noteTool(row,"bullet","รายการหัวข้อ","หัวข้อย่อย",()->prefixNoteLines("• "));noteTool(row,"number","รายการเลขลำดับ","ลำดับ",()->prefixNoteLines("number"));
        noteTool(row,"clear","ล้างรูปแบบ","ล้างรูปแบบ",()->formatNote("clear",0));noteTool(row,"find","ค้นหาในโน้ต","ค้นหา",this::findInNote);
        noteTool(row,"tools","จัดลำดับเครื่องมือ","จัดแถบ",this::arrangeNoteTools);applyNoteToolOrder();updateRecentTool();
    }
    private ReaderIconButton noteTool(LinearLayout row,String symbol,String label,String caption,Runnable action) {
        ReaderIconButton button=icon(symbol,label,caption,()->{noteToolStatus.setText(label);if(!Arrays.asList("undo","redo","tools","find").contains(symbol)) readerPrefs().edit().putString("recentNoteTool",symbol).apply();action.run();updateNoteTools();handler.post(this::updateRecentTool);});row.addView(button,new LinearLayout.LayoutParams(dp(64),dp(58)));noteToolIcons.put(symbol,button);return button;
    }
    private void updateNoteTools() {
        if(!editor||body==null||noteUndo==null) return;noteUndo.setEnabled(noteHistory.canUndo());noteRedo.setEnabled(noteHistory.canRedo());
        int start=Math.max(0,Math.min(body.getSelectionStart(),body.getSelectionEnd())),end=Math.max(start,Math.max(body.getSelectionStart(),body.getSelectionEnd()));
        for(String key:new String[]{"bold","italic","underline","strike","color","highlight","heading"}) {ReaderIconButton icon=noteToolIcons.get(key);if(icon!=null) icon.setSelected(false);}
        if(end<=start) return;Editable text=body.getText();for(Object span:text.getSpans(start,end,Object.class)) {if(text.getSpanStart(span)>start||text.getSpanEnd(span)<end) continue;String key=null;
            if(span instanceof StyleSpan) {int style=((StyleSpan)span).getStyle();if((style&Typeface.BOLD)!=0) noteToolIcons.get("bold").setSelected(true);if((style&Typeface.ITALIC)!=0) noteToolIcons.get("italic").setSelected(true);}
            else if(span instanceof UnderlineSpan) key="underline";else if(span instanceof StrikethroughSpan) key="strike";else if(span instanceof ForegroundColorSpan) key="color";else if(span instanceof BackgroundColorSpan) key="highlight";else if(span instanceof AbsoluteSizeSpan&&((AbsoluteSizeSpan)span).getSize()==26) key="heading";
            if(key!=null) noteToolIcons.get(key).setSelected(true);
        }
    }
    private int[] noteSelection() {int start=body.getSelectionStart(),end=body.getSelectionEnd();if(!body.hasFocus()||start<0||start==end) {toast("เลือกข้อความในเนื้อหาโน้ตก่อน แล้วแตะเครื่องมือที่ต้องการ");return null;}return new int[]{Math.min(start,end),Math.max(start,end)};}
    private void formatNote(String type,int value) {
        int[] range=noteSelection();if(range==null) return;rememberNote(false);Editable text=body.getText();Object replacement=null;Class<?> kind;
        switch(type) {case "underline":kind=UnderlineSpan.class;replacement=new UnderlineSpan();break;case "strike":kind=StrikethroughSpan.class;replacement=new StrikethroughSpan();break;case "color":kind=ForegroundColorSpan.class;replacement=new ForegroundColorSpan(value);break;case "highlight":kind=BackgroundColorSpan.class;if(value!=0) replacement=new BackgroundColorSpan(value);break;case "size":kind=AbsoluteSizeSpan.class;replacement=new AbsoluteSizeSpan(value,true);break;default:kind=CharacterStyle.class;}
        boolean covered=false;for(Object span:text.getSpans(range[0],range[1],kind)) {int a=text.getSpanStart(span),b=text.getSpanEnd(span);if(a<=range[0]&&b>=range[1]) covered=true;text.removeSpan(span);
            if(span instanceof CharacterStyle) {if(a<range[0]) text.setSpan(copyNoteStyle((CharacterStyle)span),a,range[0],Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(b>range[1]) text.setSpan(copyNoteStyle((CharacterStyle)span),range[1],b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        }
        if(replacement!=null&&(!covered||(!type.equals("underline")&&!type.equals("strike")))) text.setSpan(replacement,range[0],range[1],Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        changed();updateNoteTools();
    }
    private CharacterStyle copyNoteStyle(CharacterStyle style) {
        CharacterStyle span=style.getUnderlying();
        if(span instanceof StyleSpan) return new StyleSpan(((StyleSpan)span).getStyle());
        if(span instanceof UnderlineSpan) return new UnderlineSpan();if(span instanceof StrikethroughSpan) return new StrikethroughSpan();
        if(span instanceof ForegroundColorSpan) return new ForegroundColorSpan(((ForegroundColorSpan)span).getForegroundColor());
        if(span instanceof BackgroundColorSpan) return new BackgroundColorSpan(((BackgroundColorSpan)span).getBackgroundColor());
        if(span instanceof AbsoluteSizeSpan) return new AbsoluteSizeSpan(((AbsoluteSizeSpan)span).getSize(),((AbsoluteSizeSpan)span).getDip());
        if(span instanceof RelativeSizeSpan) return new RelativeSizeSpan(((RelativeSizeSpan)span).getSizeChange());
        return CharacterStyle.wrap(span);
    }
    private void noteSize() {if(noteSelection()==null) return;String[] labels={"เล็ก 14","ปกติ 18","กลาง 22","หัวข้อ 26","ใหญ่ 32"};int[] sizes={14,18,22,26,32};new PocketDialog.Builder(this).setTitle("ขนาดข้อความที่เลือก").setItems(labels,(d,i)->formatNote("size",sizes[i])).show();}
    private void noteColor(boolean highlight) {
        if(noteSelection()==null) return;int[] colors=highlight?new int[]{0xffFFF19A,0xffC3E8C5,0xffFFC8DF,0xffBFE7F7,0}:new int[]{INK,0xff216B58,0xff245BCC,0xffD12D39,0xff7B3FB0};
        String[] labels=highlight?new String[]{"เหลือง","เขียว","ชมพู","ฟ้า","ลบไฮไลต์"}:new String[]{"ดำ","เขียว","น้ำเงิน","แดง","ม่วง"};
        new PocketDialog.Builder(this).setTitle(highlight?"สีไฮไลต์ข้อความ":"สีข้อความที่เลือก").setItems(labels,(d,i)->formatNote(highlight?"highlight":"color",colors[i])).show();
    }
    private void prefixNoteLines(String prefix) {
        body.requestFocus();int lo=Math.max(0,Math.min(body.getSelectionStart(),body.getSelectionEnd())),hi=Math.max(lo,Math.max(body.getSelectionStart(),body.getSelectionEnd()));rememberNote(false);loading=true;try {NoteLists.toggle(body.getText(),lo,hi,prefix.equals("number")?"number":"bullet");}finally {loading=false;}changed();updateNoteTools();followNoteCursor();
    }
    private void followNoteCursor() {handler.removeCallbacks(cursorFollower);if(editor) handler.post(cursorFollower);}
    private void followNoteCursorNow() {if(!editor||body==null||!body.hasFocus()||body.getLayout()==null) return;int cursor=Math.max(0,body.getSelectionEnd());android.text.Layout layout=body.getLayout();int line=layout.getLineForOffset(Math.min(cursor,body.length()));Rect rect=new Rect(0,layout.getLineTop(line)+body.getTotalPaddingTop(),body.getWidth(),layout.getLineBottom(line)+body.getTotalPaddingTop()+dp(24));body.requestRectangleOnScreen(rect,false);}
    private void findInNote() {
        EditText field=input("ข้อความที่ต้องการหา",true);new PocketDialog.Builder(this).setTitle("ค้นหาในโน้ต").setView(field).setNegativeButton("ยกเลิก",null).setPositiveButton("ค้นหาถัดไป",(d,w)->{String term=field.getText().toString();if(term.isEmpty()) return;String text=body.getText().toString();int start=Math.max(0,body.getSelectionEnd());int index=text.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT),start);if(index<0) index=text.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));if(index<0) toast("ไม่พบข้อความนี้ในเนื้อหาโน้ต");else {body.requestFocus();body.setSelection(index,index+term.length());followNoteCursor();}}).show();
    }
    private void insertNoteTable() {
        if(tables.size()>=NoteTable.MAX_TABLES) {toast("เพิ่มได้สูงสุด 10 ตารางต่อโน้ต");return;}LinearLayout form=vertical();form.setPadding(dp(18),dp(8),dp(18),dp(8));form.addView(text("แทรกตารางต่อจากเนื้อหาโน้ต · เลื่อนตารางซ้าย–ขวาได้",14,MUTED));
        Spinner rows=new Spinner(this,Spinner.MODE_DIALOG),cols=new Spinner(this,Spinner.MODE_DIALOG);String[] counts={"1","2","3","4","5","6","7","8"};rows.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,counts));cols.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,counts));rows.setSelection(2);cols.setSelection(2);rows.setContentDescription("จำนวนแถว");cols.setContentDescription("จำนวนคอลัมน์");form.addView(text("แถว",14,INK));form.addView(rows);form.addView(text("คอลัมน์",14,INK));form.addView(cols);
        new PocketDialog.Builder(this).setTitle("สร้างตาราง").setView(form).setNegativeButton("ยกเลิก",null).setPositiveButton("แทรก",(d,w)->{rememberNote(false);NoteTable table=new NoteTable(rows.getSelectedItemPosition()+1,cols.getSelectedItemPosition()+1);tables.add(table);expandedTableId=table.id;renderNoteTables();changed();updateNoteTools();handler.post(()->{View first=noteTables.findViewWithTag(table.id+":0:0");if(first!=null) {first.requestFocus();first.requestRectangleOnScreen(new Rect(0,0,first.getWidth(),first.getHeight()),false);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(first,InputMethodManager.SHOW_IMPLICIT);}});}).show();
    }
    private void renderNoteTables() {
        if(noteTables==null) return;noteTables.removeAllViews();if(!tables.isEmpty()&&tables.stream().noneMatch(t->t.id.equals(expandedTableId))) expandedTableId=tables.get(0).id;
        for(int index=0;index<tables.size();index++) {NoteTable table=tables.get(index);final int number=index+1;LinearLayout panel=card(noteTables);LinearLayout top=horizontal(panel);top.addView(strong("ตาราง "+number+" · "+table.cells.size()+" × "+table.columns(),16),new LinearLayout.LayoutParams(0,-2,1));top.addView(icon("more","ตัวเลือกตาราง "+number,null,()->noteTableMenu(table)),new LinearLayout.LayoutParams(dp(48),dp(48)));
            if(!table.id.equals(expandedTableId)) {panel.addView(button("เปิดแก้ไขตาราง",()->{expandedTableId=table.id;renderNoteTables();},false));continue;}
            int start=Math.max(0,Math.min(tablePages.getOrDefault(table.id,0),Math.max(0,(table.cells.size()-1)/8)))*8,end=Math.min(table.cells.size(),start+8);
            if(table.cells.size()>8) {LinearLayout pages=horizontal(panel);action(pages,"‹ แถวก่อนหน้า",()->{tablePages.put(table.id,Math.max(0,start/8-1));finishTableChange(false);},false);pages.addView(text((start+1)+"–"+end+" / "+table.cells.size(),12,MUTED));action(pages,"แถวถัดไป ›",()->{tablePages.put(table.id,Math.min((table.cells.size()-1)/8,start/8+1));finishTableChange(false);},false);}
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(true);LinearLayout grid=vertical();scroll.addView(grid);panel.addView(scroll);
            for(int r=start;r<end;r++) {LinearLayout row=horizontal(grid);for(int c=0;c<table.columns();c++) {final int rr=r,cc=c;EditText cell=input("แถว "+(r+1)+" ช่อง "+(c+1),false);cell.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);cell.setTextSize(16);cell.setMinHeight(dp(54));cell.setGravity(Gravity.TOP);cell.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);cell.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});cell.setTag(table.id+":"+r+":"+c);cell.setContentDescription("ตาราง "+number+" แถว "+(r+1)+" คอลัมน์ "+(c+1));cell.setText(table.cells.get(r).get(c));
                GradientDrawable border=background(r==0?0xffEDF3E9:Color.WHITE,0);border.setStroke(dp(1),0xffC8D6C6);cell.setBackground(border);row.addView(cell,new LinearLayout.LayoutParams(dp(table.width(c)),-2));cell.setOnFocusChangeListener((v,focus)->{if(focus) {focusedTable=table.id;focusedRow=rr;focusedColumn=cc;}});
                cell.addTextChangedListener(new TextWatcher() {public void beforeTextChanged(CharSequence s,int a,int count,int after) {rememberNote(true);}public void onTextChanged(CharSequence s,int a,int before,int count) {table.cells.get(rr).set(cc,s.toString());changed();}public void afterTextChanged(Editable e) {updateNoteTools();cell.post(()->cell.requestRectangleOnScreen(new Rect(0,0,cell.getWidth(),cell.getHeight()),false));}});
            }}
        }
    }
    private void noteTableMenu(NoteTable table) {
        new PocketDialog.Builder(this).setTitle("จัดการตาราง").setItems(new String[]{"เพิ่มแถวด้านล่าง","เพิ่มคอลัมน์ด้านขวา","ลบแถว…","ลบคอลัมน์…","ลบตาราง","แทรกแถว…","แทรกคอลัมน์…","ความกว้างคอลัมน์…","วางจาก Excel / Sheets","คัดลอกตาราง","ส่งออก CSV"},(d,index)->handler.post(()->{
            if(index==0) {if(table.cells.size()>=NoteTable.MAX_ROWS) {toast("สูงสุด 30 แถวต่อตาราง");return;}rememberNote(false);table.insertRow(table.cells.size());finishTableChange();}
            else if(index==1) {if(table.columns()>=NoteTable.MAX_COLS) {toast("สูงสุด 8 คอลัมน์ต่อตาราง");return;}rememberNote(false);table.insertColumn(table.columns());finishTableChange();}
            else if(index==2||index==3) {boolean rows=index==2;int count=rows?table.cells.size():table.columns();if(count<=1) {toast("ต้องเหลืออย่างน้อย 1 แถวและ 1 คอลัมน์");return;}String[] labels=new String[count];for(int i=0;i<count;i++) labels[i]=(rows?"แถว ":"คอลัมน์ ")+(i+1);new PocketDialog.Builder(this).setTitle(rows?"เลือกแถวที่จะลบ":"เลือกคอลัมน์ที่จะลบ").setItems(labels,(dialog,which)->confirm("ลบข้อมูลใน"+labels[which]+"?","ย้อนกลับได้ด้วยปุ่มย้อนกลับข้อความ",()->{rememberNote(false);if(rows) table.cells.remove(which);else table.removeColumn(which);finishTableChange();})).show();}
            else if(index==5||index==6) {insertTablePart(table,index==5);}
            else if(index==7) tableColumnWidth(table);
            else if(index==8) pasteTable(table);
            else if(index==9) {((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NotePocket table",TableTransfer.write(table,false)));toast("คัดลอกตารางแล้ว วางใน Excel หรือ Sheets ได้");}
            else if(index==10) exportTableCsv(table);
            else confirm("ลบตารางนี้?","ย้อนกลับได้ด้วยปุ่มย้อนกลับข้อความ",()->{rememberNote(false);tables.remove(table);finishTableChange();});
        })).show();
    }
    private void finishTableChange() {finishTableChange(true);}
    private void finishTableChange(boolean edited) {keyboardOff();renderNoteTables();if(edited) changed();updateNoteTools();}

    private void chooseFolder() {manageFolders(true);}
    private void applyNoteToolOrder() {
        if(noteToolsRow==null) return;noteToolsRow.removeAllViews();recentTool=null;
        for(String key:new String[]{"undo","redo"}) noteToolsRow.addView(noteToolIcons.get(key),new LinearLayout.LayoutParams(dp(64),dp(58)));
        for(String key:NoteTools.order(readerPrefs().getString("noteToolOrder","[]"))) noteToolsRow.addView(noteToolIcons.get(key),new LinearLayout.LayoutParams(dp(64),dp(58)));
        noteToolsRow.addView(noteToolIcons.get("tools"),new LinearLayout.LayoutParams(dp(64),dp(58)));
    }
    private void updateRecentTool() {
        if(!editor||noteToolsRow==null) return;if(recentTool!=null) {noteToolsRow.removeView(recentTool);recentTool=null;}
        String key=readerPrefs().getString("recentNoteTool","");ReaderIconButton target=noteToolIcons.get(key);if(target==null||NoteTools.name(key).isEmpty()) return;
        recentTool=icon(key,"ใช้เครื่องมือล่าสุด: "+NoteTools.name(key),"ล่าสุด",()->target.performClick());noteToolsRow.addView(recentTool,2,new LinearLayout.LayoutParams(dp(64),dp(58)));
    }
    private void arrangeNoteTools() {
        List<String> order=NoteTools.order(readerPrefs().getString("noteToolOrder","[]"));String[] labels=new String[order.size()];for(int i=0;i<labels.length;i++) labels[i]=(i+1)+". "+NoteTools.name(order.get(i));
        new PocketDialog.Builder(this).setTitle("จัดลำดับเครื่องมือ").setItems(labels,(d,which)->handler.post(()->moveNoteTool(order,which))).setNeutralButton("ค่าเริ่มต้น",(d,w)->{readerPrefs().edit().remove("noteToolOrder").apply();if(editor) {applyNoteToolOrder();updateRecentTool();}}).setPositiveButton("เสร็จ",null).show();
    }
    private void moveNoteTool(List<String> order,int which) {
        new PocketDialog.Builder(this).setTitle(NoteTools.name(order.get(which))).setItems(new String[]{"ย้ายไปหน้าแรก","เลื่อนขึ้นหนึ่งตำแหน่ง","เลื่อนลงหนึ่งตำแหน่ง"},(dialog,move)->handler.post(()->{int destination=move==0?0:move==1?Math.max(0,which-1):Math.min(order.size()-1,which+1);String key=order.remove(which);order.add(destination,key);readerPrefs().edit().putString("noteToolOrder",new org.json.JSONArray(order).toString()).apply();if(editor) {applyNoteToolOrder();updateRecentTool();}arrangeNoteTools();})).show();
    }
    private void manageFolders(boolean choose) {
        keyboardOff();List<ContentValues> folders=store.folders();String[] labels=new String[folders.size()];for(int i=0;i<labels.length;i++) labels[i]=folders.get(i).getAsString("name")+" ("+folders.get(i).getAsInteger("count")+")";
        new PocketDialog.Builder(this).setTitle(choose?"ย้ายโน้ตเข้าโฟลเดอร์":"โฟลเดอร์ของฉัน").setItems(labels,(d,index)->handler.post(()->{String name=folders.get(index).getAsString("name");if(choose) {folder=name;changed();saveNote(true);toast("ย้ายเข้า "+name+" แล้ว");}else folderActions(name);})).setPositiveButton("สร้างโฟลเดอร์",(d,w)->handler.post(()->folderNameDialog(null,choose))).setNegativeButton("ปิด",null).show();
    }
    private void folderActions(String name) {
        String[] choices=name.equals("ทั่วไป")?new String[]{"เปิดโฟลเดอร์"}:new String[]{"เปิดโฟลเดอร์","เปลี่ยนชื่อ","ลบโฟลเดอร์"};
        new PocketDialog.Builder(this).setTitle(name).setItems(choices,(d,index)->handler.post(()->{if(index==0) {filter="folder:"+name;query="";tab="notes";home();}else if(index==1) folderNameDialog(name,false);else confirm("ลบโฟลเดอร์ "+name+"?","โน้ตทั้งหมดจะย้ายไปทั่วไป และยังเปิดอ่านได้",()->{store.deleteFolder(name);if(filter.equals("folder:"+name)) filter="all";home();});})).show();
    }
    private void folderNameDialog(String old,boolean choose) {
        EditText field=input("ชื่อโฟลเดอร์",true);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});if(old!=null) field.setText(old);
        AlertDialog dialog=new PocketDialog.Builder(this).setTitle(old==null?"สร้างโฟลเดอร์":"เปลี่ยนชื่อโฟลเดอร์").setView(field).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=field.getText().toString().trim();if(name.isEmpty()) {field.setError("กรอกชื่อโฟลเดอร์");return;}if(!name.equals(old)&&!store.query("SELECT name FROM folders WHERE name=?",name).isEmpty()) {field.setError("มีโฟลเดอร์ชื่อนี้แล้ว");return;}try {if(old==null) store.createFolder(name);else store.renameFolder(old,name);dialog.dismiss();if(choose) {folder=name;changed();saveNote(true);}else {if(old!=null&&filter.equals("folder:"+old)) filter="folder:"+name;home();}}catch(IllegalArgumentException e) {field.setError("ใช้ชื่อโฟลเดอร์นี้ไม่ได้");}}));dialog.show();
    }
    private void insertTablePart(NoteTable table,boolean rows) {
        int count=rows?table.cells.size():table.columns(),max=rows?NoteTable.MAX_ROWS:NoteTable.MAX_COLS;if(count>=max) {toast(rows?"สูงสุด 30 แถว":"สูงสุด 8 คอลัมน์");return;}String[] labels=new String[count+1];for(int i=0;i<=count;i++) labels[i]=i==count?"ต่อท้าย":(rows?"ก่อนแถว ":"ก่อนคอลัมน์ ")+(i+1);
        new PocketDialog.Builder(this).setTitle(rows?"ตำแหน่งแถวใหม่":"ตำแหน่งคอลัมน์ใหม่").setItems(labels,(d,at)->{rememberNote(false);if(rows) table.insertRow(at);else table.insertColumn(at);finishTableChange();}).show();
    }
    private void tableColumnWidth(NoteTable table) {
        String[] labels=new String[table.columns()];for(int i=0;i<labels.length;i++) labels[i]="คอลัมน์ "+(i+1)+" · "+table.width(i);
        new PocketDialog.Builder(this).setTitle("ปรับความกว้างคอลัมน์").setItems(labels,(d,column)->handler.post(()->{LinearLayout form=vertical();form.setPadding(dp(18),dp(8),dp(18),dp(8));TextView size=text("ความกว้าง "+table.width(column),16,INK);form.addView(size);SeekBar bar=new SeekBar(this);bar.setMax(280);bar.setProgress(table.width(column)-80);bar.setContentDescription("ความกว้างคอลัมน์");form.addView(bar);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {public void onProgressChanged(SeekBar v,int progress,boolean user) {size.setText("ความกว้าง "+(progress+80));}public void onStartTrackingTouch(SeekBar v) {}public void onStopTrackingTouch(SeekBar v) {}});new PocketDialog.Builder(this).setTitle("คอลัมน์ "+(column+1)).setView(form).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(dialog,w)->{rememberNote(false);while(table.widths.size()<table.columns()) table.widths.add(150);table.widths.set(column,bar.getProgress()+80);finishTableChange();}).show();})).show();
    }
    private void pasteTable(NoteTable table) {
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0) {toast("คัดลอกช่องจาก Excel หรือ Sheets ก่อน แล้วเลือกวาง");return;}
        final List<List<String>> incoming;try {CharSequence value=clip.getItemAt(0).coerceToText(this);incoming=TableTransfer.parseClipboard(value==null?"":value.toString());}catch(IllegalArgumentException e) {toast("วางไม่ได้ ตรวจข้อมูลไม่เกิน 30 แถว × 8 คอลัมน์ และช่องละ 4,000 ตัวอักษร");return;}
        LinearLayout form=vertical();form.setPadding(dp(18),dp(8),dp(18),dp(8));form.addView(text("ข้อมูล "+incoming.size()+" แถว × "+incoming.get(0).size()+" คอลัมน์\nจะแทนที่ช่องในช่วงที่วาง และเพิ่มแถว/คอลัมน์ให้เมื่อจำเป็น",14,INK));Spinner row=new Spinner(this,Spinner.MODE_DIALOG),col=new Spinner(this,Spinner.MODE_DIALOG);String[] rows=new String[table.cells.size()],cols=new String[table.columns()];for(int i=0;i<rows.length;i++) rows[i]="แถว "+(i+1);for(int i=0;i<cols.length;i++) cols[i]="คอลัมน์ "+(i+1);row.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rows));col.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cols));row.setContentDescription("แถวเริ่มวาง");col.setContentDescription("คอลัมน์เริ่มวาง");if(focusedTable.equals(table.id)) {row.setSelection(Math.min(focusedRow,rows.length-1));col.setSelection(Math.min(focusedColumn,cols.length-1));}form.addView(row);form.addView(col);
        AlertDialog dialog=new PocketDialog.Builder(this).setTitle("วางจาก Excel / Sheets").setView(form).setNegativeButton("ยกเลิก",null).setPositiveButton("วาง",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int r=row.getSelectedItemPosition(),c=col.getSelectedItemPosition();if(r+incoming.size()>NoteTable.MAX_ROWS||c+incoming.get(0).size()>NoteTable.MAX_COLS) {toast("ตำแหน่งนี้เกินขนาดตารางสูงสุด กรุณาเลือกจุดเริ่มใหม่");return;}rememberNote(false);table.paste(incoming,r,c);dialog.dismiss();finishTableChange();}));dialog.show();
    }
    private void exportTableCsv(NoteTable table) {
        if(!saveNote(false)) return;try {try(OutputStream out=new FileOutputStream(new File(getCacheDir(),"table-export.csv"))) {out.write(TableTransfer.write(table,true).getBytes(java.nio.charset.StandardCharsets.UTF_8));}Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType("text/csv");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,"NotePocket-table.csv");startActivityForResult(intent,CSV);}catch(IOException e) {toast("เตรียมไฟล์ตารางไม่ได้ ตรวจสอบพื้นที่ว่าง");}
    }
    private void soapEditor() {
        if(!editor) return;if(activeSoapDialog!=null&&activeSoapDialog.isShowing()) return;keyboardOff();
        ScrollView scroll=new ScrollView(this);LinearLayout form=vertical();form.setPadding(dp(12),dp(4),dp(12),dp(12));scroll.addView(form);form.addView(text("เรียบเรียงจากข้อมูลที่คุณกรอก · เก็บร่างอัตโนมัติ\nกรอกเฉพาะข้อมูลและการประเมินที่มี ส่วนที่ว่างจะแสดงว่ายังไม่ได้ระบุ",14,MUTED));
        LinearLayout metadata=card(form);metadata.setPadding(dp(14),dp(10),dp(14),dp(10));metadata.setBackground(background(PocketTheme.CREAM,20));metadata.addView(text("ข้อมูลบันทึก",12,GREEN));
        for(int i=0;i<SoapDraft.KEYS.length;i++) {final String key=SoapDraft.KEYS[i];LinearLayout section=metadata;if(i>=3) {section=card(form);section.setPadding(dp(14),dp(10),dp(14),dp(14));section.setBackground(background(PocketTheme.NOTES[i-3],20));}LinearLayout line=horizontal(section);line.addView(strong(SoapDraft.LABELS[i],16),new LinearLayout.LayoutParams(0,-2,1));if(i>=3) {final int sectionIndex=i;line.addView(icon("help","แนวทาง "+key.toUpperCase(Locale.ROOT),null,()->soapGuide(sectionIndex)),new LinearLayout.LayoutParams(dp(48),dp(48)));}
            EditText field=input(SoapDraft.HINTS[i],i<3);field.setContentDescription("SOAP "+key.toUpperCase(Locale.ROOT));field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(i<3?200:20000)});if(i>=3) {field.setMinLines(3);field.setGravity(Gravity.TOP);}field.setText(soapDraft.get(key));section.addView(field,new LinearLayout.LayoutParams(-1,-2));gap(section,8);field.addTextChangedListener(watcher(()->{String old=soapDraft.get(key),value=field.getText().toString();soapDraft.put(key,value);if(key.equals("problem")&&body.length()==0&&(title.getText().toString().trim().isEmpty()||title.getText().toString().equals("ไม่มีชื่อ")||title.getText().toString().equals("SOAP • "+old.trim()))&&!value.trim().isEmpty()) title.setText("SOAP • "+value.trim());changed();}));
        }
        AlertDialog dialog=new PocketDialog.Builder(this).setTitle("ตัวช่วย SOAP note").setView(scroll).setNegativeButton("ปิด เก็บร่าง",(d,w)->saveNote(true)).setNeutralButton("ดูตัวอย่าง",null).setPositiveButton("แทรก / อัปเดต",null).create();activeSoapDialog=dialog;
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{TextView preview=text(soapDraft.render(),16,INK);preview.setTextIsSelectable(true);preview.setPadding(dp(18),dp(12),dp(18),dp(12));ScrollView sc=new ScrollView(this);sc.addView(preview);new PocketDialog.Builder(this).setTitle("ตัวอย่าง SOAP").setView(sc).setPositiveButton("กลับไปแก้",null).show();});dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{boolean content=false;for(String key:new String[]{"s","o","a","p"}) if(!soapDraft.get(key).trim().isEmpty()) content=true;if(!content) {toast("กรอกข้อมูลใน S / O / A / P อย่างน้อยหนึ่งหัวข้อก่อน");return;}String previous=soapDraft.get("applied"),text=body.getText().toString();int found=previous.isEmpty()?-1:text.indexOf(previous);if(!previous.isEmpty()&&(found<0||text.indexOf(previous,found+previous.length())>=0)) {confirm("เพิ่ม SOAP เป็นส่วนใหม่?","ส่วน SOAP เดิมในโน้ตถูกแก้ไขแล้ว จะเก็บข้อความเดิมไว้และเพิ่มฉบับนี้ต่อท้าย",()->applySoap(true));}else applySoap(false);});});
        dialog.setOnDismissListener(d->{if(activeSoapDialog==dialog) activeSoapDialog=null;if(!isDestroyed()&&editor) saveNote(false);});dialog.show();
    }
    private void soapGuide(int section) {
        String[] guides={"","","","อาการสำคัญและระยะเวลา\nผู้ป่วยเล่าอะไร เปลี่ยนจากเดิมอย่างไร\nการใช้ยา ความร่วมมือ และอาการหลังใช้ยา\nแยกคำบอกเล่าจากค่าที่ตรวจวัด","สัญญาณชีพและผลตรวจที่เกี่ยวข้อง\nระบุวันเวลา หน่วย และแหล่งข้อมูล\nรายการยา ขนาด วิธีใช้ และประวัติแพ้ยาที่ตรวจสอบแล้ว\nถ้าไม่มีข้อมูล ให้เว้นไว้หรือลงว่ายังไม่ทราบ","ระบุปัญหาแยกเป็นข้อและหลักฐานสนับสนุน\nเปรียบเทียบกับเป้าหมายที่กำหนด\nหากเป็นเรื่องยา: ข้อบ่งใช้ ประสิทธิผล ความปลอดภัย และความร่วมมือ\nระบุสิ่งที่ยังไม่แน่ชัดหรือข้อมูลที่ต้องหาเพิ่ม","แผนสำหรับแต่ละปัญหาที่ประเมินแล้ว\nสิ่งที่จะดำเนินการ ผู้รับผิดชอบ และกรอบเวลา\nตัวชี้วัดผลและการติดตามความปลอดภัย\nคำแนะนำที่ให้และกำหนดติดตามครั้งถัดไป"};new PocketDialog.Builder(this).setTitle(SoapDraft.LABELS[section]).setMessage(guides[section]).setPositiveButton("กลับไปกรอก",null).show();
    }
    private void applySoap(boolean append) {
        rememberNote(false);String generated=soapDraft.render(),previous=soapDraft.get("applied"),plain=body.getText().toString();int at=append||previous.isEmpty()?-1:plain.indexOf(previous);loading=true;
        try {if(at>=0) body.getText().replace(at,at+previous.length(),generated);else {at=body.length();if(at>0) {body.append("\n\n");at+=2;}body.append(generated);}for(String heading:new String[]{"SOAP NOTE",SoapDraft.LABELS[3],SoapDraft.LABELS[4],SoapDraft.LABELS[5],SoapDraft.LABELS[6]}) {int index=generated.indexOf(heading);if(index>=0) body.getText().setSpan(new StyleSpan(Typeface.BOLD),at+index,at+index+heading.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}soapDraft.put("applied",generated);if(title.getText().toString().trim().isEmpty()||(title.getText().toString().equals("ไม่มีชื่อ")&&plain.trim().isEmpty())) title.setText("SOAP • "+(soapDraft.get("problem").trim().isEmpty()?"บันทึกติดตาม":soapDraft.get("problem").trim()));body.setSelection(Math.min(body.length(),at+generated.length()));}finally {loading=false;}
        changed();updateNoteTools();if(saveNote(true)&&activeSoapDialog!=null) activeSoapDialog.dismiss();body.requestFocus();followNoteCursor();
    }

    private void attachmentMenu() {
        if(!saveNote(true)) return;keyboardOff();
        new PocketDialog.Builder(this).setTitle("แนบเอกสารกับโน้ต").setItems(new String[]{"นำเข้าไฟล์จากเครื่อง","เลือกจากเอกสารที่เก็บไว้"},(d,w) -> {
            if(w==0) pickDocument(noteId);
            else {
                List<ContentValues> docs=store.query("SELECT * FROM docs ORDER BY rowid DESC");
                if(docs.isEmpty()) {toast("ยังไม่มีเอกสาร กรุณานำเข้าจากเครื่องก่อน");return;}
                String[] names=new String[docs.size()];for(int i=0;i<names.length;i++) names[i]=docs.get(i).getAsString("name");
                new PocketDialog.Builder(this).setTitle("เลือกไฟล์แนบ").setItems(names,(dialog,index) -> {store.attach(noteId,docs.get(index).getAsString("id"));refreshAttachments();}).show();
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
            new PocketDialog.Builder(this).setTitle(name).setMessage("ไฟล์นี้เก็บไว้แล้ว สามารถส่งออกเพื่อเปิดด้วยแอปที่รองรับได้").setNegativeButton("ปิด",null).setPositiveButton("ส่งออก",(d,w) -> exportDocument(id,name,mime)).show();return;
        }
        if(!saveNote(false)) return;long back=editor?noteId:-1;keyboardOff();openPdf(id,name,store.lastPage(id),back);
    }
    private void openPdf(String id,String name,int page,long backNote) {
        handler.removeCallbacks(autosave);editor=false;releaseEditorViews();if(library!=null) {library.close();library=null;}closePdf();pdfReturnNote=backNote;
        try {
            pdfFd=ParcelFileDescriptor.open(new File(getFilesDir(),id),ParcelFileDescriptor.MODE_READ_ONLY);pdf=new PdfRenderer(pdfFd);pdfPages=pdf.getPageCount();if(pdfPages<1) throw new IOException();
            pdfId=id;pdfName=name;root=vertical();root.setBackgroundColor(BG);setContentView(root);
            root.setOnApplyWindowInsetsListener((view,insets) -> {view.setPadding(fullscreen?0:insets.getSystemWindowInsetLeft(),fullscreen?0:insets.getSystemWindowInsetTop(),fullscreen?0:insets.getSystemWindowInsetRight(),fullscreen?0:insets.getSystemWindowInsetBottom());return insets;});
            readerHeader=horizontal(root);readerHeader.setPadding(dp(4),0,dp(4),0);
            ReaderIconButton back=icon("back","กลับจาก PDF",null,this::returnFromPdf);readerHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView heading=strong(name,16);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);readerHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
            ReaderIconButton full=icon("full","เต็มจอ",null,() -> setFullscreen(!fullscreen));readerHeader.addView(full,new LinearLayout.LayoutParams(dp(48),dp(48)));
            readerHeader.addView(icon("more","เมนูเอกสาร",null,this::inkTools),new LinearLayout.LayoutParams(dp(48),dp(48)));
            pdfStage=new FrameLayout(this);root.addView(pdfStage,new LinearLayout.LayoutParams(-1,0,1));
            inkStatus=text("",12,MUTED);pageView=new PdfPageView(this);pdfStage.addView(pageView,new FrameLayout.LayoutParams(-1,-1));
            pageView.setListener(drawing -> {try {store.saveDrawing(pdfId,pageIndex,drawing);handler.post(this::updateReaderActions);return true;}catch(Exception error) {toast("บันทึกรอยเขียนไม่ได้ กรุณาตรวจสอบพื้นที่ว่าง");return false;}});
            pageView.setTextListener(new PdfPageView.TextListener() {
                public void edit(TextBox box,float x,float y) {editTextBox(box,x,y);}
                public boolean save(String boxes) {try {store.saveText(pdfId,pageIndex,boxes);return true;}catch(Exception error) {toast("บันทึกข้อความไม่ได้");return false;}}
            });
            pageView.setSelectionListener(new PdfPageView.SelectionListener() {
                public void selected() {selectionActions();}
                public void request(float u,float v) {loadSelection(u,v);}
            });
            createReaderDock();readerFooter=horizontal(root);readerFooter.setPadding(dp(6),0,dp(6),0);readerFooter.setBackgroundColor(Color.WHITE);
            previousIcon=icon("previous","หน้าก่อนหน้า","ก่อนหน้า",()->renderPage(pageIndex-1));readerFooter.addView(previousIcon,new LinearLayout.LayoutParams(dp(76),dp(60)));
            pageLabel=text("",16,INK);pageLabel.setTypeface(null,Typeface.BOLD);pageLabel.setGravity(Gravity.CENTER);pageLabel.setContentDescription("เลือกเลขหน้า PDF");pageLabel.setOnClickListener(v->jumpPage());readerFooter.addView(pageLabel,new LinearLayout.LayoutParams(0,dp(60),1));
            nextIcon=icon("next","หน้าถัดไป","ถัดไป",()->renderPage(pageIndex+1));readerFooter.addView(nextIcon,new LinearLayout.LayoutParams(dp(76),dp(60)));
            applyReaderSettings();renderPage(Math.max(0,Math.min(page,pdfPages-1)));root.requestApplyInsets();
        } catch(Exception e) {returnFromPdf();toast("เปิด PDF ไม่ได้ ไฟล์อาจเสียหายหรือมีรหัสผ่าน");}
    }
    private Button compact(String value,Runnable callback) {Button button=button(value,callback,false);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(5),0,dp(5),0);button.setTextSize(14);return button;}
    private SharedPreferences readerPrefs() {return getSharedPreferences("reader",0);}
    private void applyReaderSettings() {
        if(pageView!=null) {pageView.setFitWidth(readerPrefs().getBoolean("fitWidth",true));pageView.setHighlightOpacity(readerPrefs().getInt("opacity",24));}
        if(readerPrefs().getBoolean("keepAwake",false)&&pdf!=null) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void setFullscreen(boolean value) {
        fullscreen=value;if(readerHeader!=null) readerHeader.setVisibility(value?View.GONE:View.VISIBLE);if(readerFooter!=null) readerFooter.setVisibility(value?View.GONE:View.VISIBLE);
        if(android.os.Build.VERSION.SDK_INT>=30) {
            android.view.WindowInsetsController controller=getWindow().getDecorView().getWindowInsetsController();if(controller==null&&value) getWindow().getDecorView().postDelayed(()->{if(fullscreen&&!isDestroyed()) setFullscreen(true);},32);if(controller!=null) {if(value) {controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(android.view.WindowInsets.Type.systemBars());}else controller.show(android.view.WindowInsets.Type.systemBars());}
        } else getWindow().getDecorView().setSystemUiVisibility(value?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(root!=null) root.requestApplyInsets();
    }
    private ReaderIconButton icon(String symbol,String name,String caption,Runnable callback) {return new ReaderIconButton(this,symbol,name,caption,()->{if(!busy) callback.run();});}
    private void createReaderDock() {
        historyStrip=horizontal(null);historyStrip.setPadding(dp(4),dp(4),dp(4),dp(4));historyStrip.setBackground(background(Color.WHITE,18));historyStrip.setElevation(dp(8));
        undoIcon=icon("undo","ย้อนกลับรอยเขียน","ย้อนกลับ",()->{pageView.undo();updateReaderActions();});redoIcon=icon("redo","ทำซ้ำรอยเขียน","ทำซ้ำ",()->{pageView.redo();updateReaderActions();});
        historyStrip.addView(undoIcon,new LinearLayout.LayoutParams(dp(60),dp(58)));historyStrip.addView(redoIcon,new LinearLayout.LayoutParams(dp(60),dp(58)));
        FrameLayout.LayoutParams history=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);history.setMargins(dp(10),dp(10),dp(10),dp(10));pdfStage.addView(historyStrip,history);
        toolDock=vertical();toolDock.setPadding(dp(6),dp(5),dp(6),dp(5));toolDock.setBackground(background(Color.WHITE,18));toolDock.setElevation(dp(10));
        toolStatus=text("เครื่องมือ: อ่าน",12,GREEN);toolStatus.setGravity(Gravity.CENTER);toolStatus.setSingleLine();toolDock.addView(toolStatus,new LinearLayout.LayoutParams(-1,dp(22)));
        LinearLayout row=horizontal(toolDock);String[] symbols={"read","pen","highlight","select","text"},names={"อ่าน","ปากกา","ไฮไลต์","เลือกข้อความ","ข้อความ"};int[] modes={PdfPageView.READ,PdfPageView.PEN,PdfPageView.HIGHLIGHT,PdfPageView.SELECT,PdfPageView.TEXT};
        modeIcons=new ReaderIconButton[5];for(int i=0;i<names.length;i++) {final int mode=modes[i];modeIcons[i]=icon(symbols[i],names[i],null,()->{if(mode==PdfPageView.SELECT) loadSelection(-1,-1);else inkMode(mode);});row.addView(modeIcons[i],new LinearLayout.LayoutParams(dp(48),dp(48)));}
        moreIcon=icon("more","เครื่องมือเพิ่มเติม",null,this::showReaderMore);row.addView(moreIcon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        FrameLayout.LayoutParams dock=new FrameLayout.LayoutParams(dp(300),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dock.setMargins(dp(8),dp(8),dp(8),dp(12));pdfStage.addView(toolDock,dock);
        collapsedTools=icon("tools","เปิดเครื่องมือลอย",null,this::showPalette);FrameLayout.LayoutParams collapsed=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.END);collapsed.setMargins(dp(12),dp(12),dp(12),dp(12));pdfStage.addView(collapsedTools,collapsed);
        setToolDockVisible(readerPrefs().getBoolean("toolsVisible",true));
    }
    private void setToolDockVisible(boolean visible) {
        if(toolDock==null) return;toolDock.setVisibility(visible?View.VISIBLE:View.GONE);historyStrip.setVisibility(visible?View.VISIBLE:View.GONE);collapsedTools.setVisibility(visible?View.GONE:View.VISIBLE);readerPrefs().edit().putBoolean("toolsVisible",visible).apply();
    }
    private void showPalette() {setToolDockVisible(true);updateReaderActions();}
    private void showReaderMore() {
        PopupMenu menu=new PopupMenu(this,moreIcon);String[] options={"สีปากกา / ไฮไลต์","ขนาดเส้น","จดหน้านี้","บุ๊กมาร์กและหน้าที่จด","บันทึก / เลิกบันทึกหน้านี้",fullscreen?"ออกจากเต็มจอ":"เต็มจอ","ส่งออก PDF ที่เขียนแล้ว","ตั้งค่าตัวอ่าน","จัดการฟอนต์","ซ่อนแถบเครื่องมือ"};
        for(int i=0;i<options.length;i++) menu.getMenu().add(0,i,i,options[i]);menu.setOnMenuItemClickListener(item->{
            int selected=item.getItemId();handler.post(()->{if(pdf==null) return;switch(selected) {case 0:chooseInkColor();break;case 1:chooseInkWidth();break;case 2:pageNote();break;case 3:bookmarkMenu();break;case 4:toggleBookmark();break;case 5:setFullscreen(!fullscreen);break;case 6:exportAnnotated();break;case 7:readerSettings();break;case 8:closePdf();tab="settings";home();manageFonts();break;case 9:setToolDockVisible(false);break;}});return true;
        });menu.show();
    }
    private void updateReaderActions() {
        if(pageView==null||pdf==null) return;
        if(undoIcon!=null) undoIcon.setEnabled(pageView.canUndo());if(redoIcon!=null) redoIcon.setEnabled(pageView.canRedo());
        if(previousIcon!=null) previousIcon.setEnabled(!pdfRendering&&pageIndex>0);if(nextIcon!=null) nextIcon.setEnabled(!pdfRendering&&pageIndex+1<pdfPages);
        String[] names={"อ่าน","ปากกา","ไฮไลต์","ข้อความ","เลือกข้อความ"};if(toolStatus!=null) toolStatus.setText("เครื่องมือ: "+names[pageView.getMode()]);
        int[] modes={PdfPageView.READ,PdfPageView.PEN,PdfPageView.HIGHLIGHT,PdfPageView.SELECT,PdfPageView.TEXT};if(modeIcons!=null) for(int i=0;i<modeIcons.length;i++) modeIcons[i].setSelected(pageView.getMode()==modes[i]);
    }
    private void inkMode(int mode) {if(selectionAction!=null) selectionAction.finish();pageView.setMode(mode);if(mode==PdfPageView.TEXT) toast("แตะพื้นที่ว่างเพื่อเพิ่มข้อความ แตะข้อความเพื่อแก้ และลากเพื่อย้าย");updatePageLabels();}
    private void chooseInkColor() {
        boolean highlight=pageView.getMode()==PdfPageView.HIGHLIGHT;int[] colors=highlight?new int[]{0xffFFD54F,0xff81C784,0xffF48FB1,0xff81D4FA}:new int[]{0xff245BCC,0xffE13A3A,0xff222222,0xff216B58};
        new PocketDialog.Builder(this).setTitle(highlight?"สีไฮไลต์":"สีปากกา").setItems(highlight?new String[]{"เหลือง","เขียว","ชมพู","ฟ้า"}:new String[]{"น้ำเงิน","แดง","ดำ","เขียว"},(dialog,index) -> {pageView.setInkColor(colors[index]);if(pageView.getMode()==PdfPageView.READ) inkMode(PdfPageView.PEN);}).show();
    }
    private void chooseInkWidth() {new PocketDialog.Builder(this).setTitle("ขนาดเส้น").setItems(new String[]{"บาง","กลาง","หนา"},(d,w)->pageView.setWidthFactor(new float[]{.6f,1,1.8f}[w])).show();}
    private void inkTools() {
        pageView.commitGesture();new PocketDialog.Builder(this).setTitle("เครื่องมือ PDF").setItems(new String[]{"เปิดเครื่องมือลอย","ย้อนกลับเส้นล่าสุด","ทำซ้ำเส้นที่ย้อนกลับ","สีปากกา / ไฮไลต์","ขนาดเส้น","ล้างรอยเขียนหน้านี้","บุ๊กมาร์กและหน้าที่จด","ส่งออก PDF ที่เขียนแล้ว","เต็มจอ / ออกจากเต็มจอ","ตั้งค่าตัวอ่าน","จัดการฟอนต์"},(d,index) -> {
            switch(index) {case 0:showPalette();break;case 1:pageView.undo();break;case 2:pageView.redo();break;case 3:chooseInkColor();break;case 4:chooseInkWidth();break;case 5:confirm("ล้างรอยเขียนหน้านี้?","ลบเส้นปากกาและไฮไลต์ในหน้านี้ ข้อความที่เพิ่มและ PDF ต้นฉบับยังอยู่",()->pageView.clearDrawing());break;case 6:bookmarkMenu();break;case 7:exportAnnotated();break;case 8:setFullscreen(!fullscreen);break;case 9:readerSettings();break;case 10:closePdf();tab="settings";home();manageFonts();break;}
        }).show();
    }
    private void loadSelection(float u,float v) {
        String doc=pdfId;int page=pageIndex;final java.util.concurrent.atomic.AtomicReference<List<PdfWords.Glyph>> result=new java.util.concurrent.atomic.AtomicReference<>();
        runIo("กำลังอ่านตำแหน่งข้อความ…",() -> {result.set(PdfWords.read(this,doc,page));return result.get().isEmpty()?"หน้านี้ไม่มีข้อความที่เลือกได้ อาจเป็นภาพสแกน ใช้ไฮไลต์แบบลากได้ครับ":"ลากเลือกข้อความ แล้วแตะไฮไลต์หรือคัดลอก";},() -> {
            if(pdf==null||!doc.equals(pdfId)||page!=pageIndex) return;
            if(result.get().isEmpty()) {inkMode(PdfPageView.HIGHLIGHT);return;}pageView.setWords(result.get());inkMode(PdfPageView.SELECT);if(u>=0) pageView.selectAt(u,v);
        });
    }
    private void selectionActions() {
        if(selectionAction!=null) return;
        selectionAction=startActionMode(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode action,Menu menu) {menu.add(0,1,0,"ไฮไลต์").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);menu.add(0,2,1,"คัดลอก").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);return true;}
            public boolean onPrepareActionMode(ActionMode action,Menu menu) {return false;}
            public boolean onActionItemClicked(ActionMode action,MenuItem item) {if(item.getItemId()==1) pageView.highlightSelection();else ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PDF",pageView.selectedText()));action.finish();return true;}
            public void onDestroyActionMode(ActionMode action) {if(pageView!=null) pageView.clearSelection();selectionAction=null;}
        });
    }
    private void renderPage(int index) {
        if(pdf==null||index<0||index>=pdfPages) return;if(selectionAction!=null) selectionAction.finish();pageView.commitGesture();
        final PdfRenderer renderer=pdf;final String doc=pdfId;final int request=++pdfGeneration;pdfRendering=true;pageView.setEnabled(false);previousIcon.setEnabled(false);nextIcon.setEnabled(false);pageLabel.setText("กำลังเปิดหน้า "+(index+1)+"…");
        pdfWorker.execute(()->{Bitmap rendered=null;String drawing="[]",boxes="[]";Throwable failure=null;try {if(request!=pdfGeneration) return;try(PdfRenderer.Page page=renderer.openPage(index)) {rendered=PdfBudget.render(page);}try(Store reader=new Store(getApplicationContext())) {drawing=reader.drawing(doc,index);boxes=reader.textBoxes(doc,index);}}catch(Exception|OutOfMemoryError error) {failure=error;}
            final Bitmap ready=rendered;final String ink=drawing,text=boxes;final Throwable error=failure;handler.post(()->{if(isDestroyed()||request!=pdfGeneration||pdf!=renderer||pageView==null) {if(ready!=null) ready.recycle();return;}pdfRendering=false;pageView.setEnabled(true);if(error!=null||ready==null) {if(ready!=null) ready.recycle();updatePageLabels();toast("แสดงหน้านี้ไม่ได้ ลองเปิดอีกครั้ง");return;}Bitmap previous=pageBitmap;pageBitmap=ready;pageView.setPage(ready);pageIndex=index;pageView.setDrawing(ink);pageView.setTextBoxes(text);pdfImageReleased=false;if(previous!=null) previous.recycle();store.remember(doc,index);updatePageLabels();});
        });
    }
    private void updatePageLabels() {
        ContentValues page=store.page(pdfId,pageIndex);pageLabel.setText((pageIndex+1)+" / "+pdfPages);
        if(bookmarkButton!=null) bookmarkButton.setText(page.getAsInteger("bookmarked")==1?"★":"☆");
        pageLabel.setText((page.getAsInteger("bookmarked")==1?"★ ":"")+"หน้า "+(pageIndex+1)+" / "+pdfPages);updateReaderActions();
        pageView.setContentDescription("PDF หน้า "+(pageIndex+1)+" จาก "+pdfPages+" หน้า เลือกปากกาหรือไฮไลต์เพื่อเขียน ใช้สองนิ้วซูม");
    }
    private void jumpPage() {
        EditText field=input("เลขหน้า",true);field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setText(""+(pageIndex+1));
        new PocketDialog.Builder(this).setTitle("ไปที่หน้า").setView(field).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w) -> {
            try {int page=Integer.parseInt(field.getText().toString());if(page<1||page>pdfPages) throw new IllegalArgumentException();renderPage(page-1);}catch(Exception e) {toast("กรุณาใส่เลขหน้าที่มีในเอกสาร");}
        }).show();
    }
    private void pageNote() {
        String doc=pdfId;int page=pageIndex;ContentValues record=store.page(doc,page);EditText field=input("สรุปสิ่งที่อ่าน คำถาม หรือสิ่งที่ต้องกลับมาทบทวน…",false);
        field.setMinLines(5);field.setGravity(Gravity.TOP);field.setText(record.getAsString("body"));
        field.addTextChangedListener(watcher(() -> {record.put("body",field.getText().toString());store.savePage(record);}));
        new PocketDialog.Builder(this).setTitle("บันทึกหน้า "+(page+1)+" · บันทึกอัตโนมัติ").setView(field).setPositiveButton("เสร็จ",(d,w) -> {keyboardOff();updatePageLabels();}).show();
    }
    private void toggleBookmark() {ContentValues record=store.page(pdfId,pageIndex);record.put("bookmarked",record.getAsInteger("bookmarked")==1?0:1);store.savePage(record);updatePageLabels();}
    private void bookmarkMenu() {
        List<ContentValues> rows=store.query("SELECT * FROM page_notes WHERE doc_id=? AND (bookmarked=1 OR body!='') ORDER BY page",pdfId);
        if(rows.isEmpty()) {toast("แตะบันทึกหน้าหรือจดหน้านี้ เพื่อกลับมาเปิดได้ง่าย");return;}
        String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++) {ContentValues r=rows.get(i);String b=r.getAsString("body");labels[i]=(r.getAsInteger("bookmarked")==1?"★ ":"")+"หน้า "+(r.getAsInteger("page")+1)+(b.isEmpty()?"":" · "+b.substring(0,Math.min(45,b.length())));}
        new PocketDialog.Builder(this).setTitle("หน้าที่บันทึกและโน้ตประกอบ").setItems(labels,(d,w) -> renderPage(rows.get(w).getAsInteger("page"))).show();
    }
    private void returnFromPdf() {long back=pdfReturnNote;closePdf();pdfReturnNote=-1;if(back>=0) edit(back);else home();}
    private void closePdf() {
        pdfGeneration++;pdfRendering=false;pdfImageReleased=false;
        if(selectionAction!=null) selectionAction.finish();if(paletteWindow!=null) {paletteWindow.dismiss();paletteWindow=null;}
        setFullscreen(false);readerHeader=readerFooter=null;toolDock=historyStrip=null;toolStatus=null;modeIcons=null;undoIcon=redoIcon=previousIcon=nextIcon=moreIcon=collapsedTools=null;pdfStage=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(pageView!=null) {pageView.commitGesture();pageView.setPage(null);pageView=null;}
        if(pageBitmap!=null) {pageBitmap.recycle();pageBitmap=null;}
        PdfRenderer old=pdf;ParcelFileDescriptor fd=pdfFd;pdf=null;pdfFd=null;if(old!=null||fd!=null) pdfWorker.execute(()->{if(old!=null) old.close();if(fd!=null) try {fd.close();}catch(IOException ignored) {}});pdfId=null;
    }
    private void exportAnnotated() {
        pageView.commitGesture();annotatedExportId=pdfId;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
        String name=pdfName.replaceFirst("(?i)\\.pdf$","");intent.putExtra(Intent.EXTRA_TITLE,name+"-annotated.pdf");startActivityForResult(intent,ANNOTATED);
    }
    private List<ContentValues> availableFonts() {
        List<ContentValues> fonts=new ArrayList<>();String[] ids={"sans","serif","mono"},names={"มาตรฐาน","Serif","Monospace"};
        for(int i=0;i<ids.length;i++) {ContentValues row=new ContentValues();row.put("id",ids[i]);row.put("name",names[i]);fonts.add(row);}fonts.addAll(store.query("SELECT * FROM fonts ORDER BY name"));return fonts;
    }
    private void editTextBox(TextBox existing,float x,float y) {
        String doc=pdfId;int page=pageIndex;if(doc==null) return;
        LinearLayout fields=vertical();fields.setPadding(dp(16),0,dp(16),0);EditText value=input("ข้อความบน PDF",false);value.setMinLines(3);value.setMaxLines(7);value.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});if(existing!=null) value.setText(existing.text);fields.addView(value);
        fields.addView(text("ฟอนต์",13,MUTED));Spinner font=new Spinner(this,Spinner.MODE_DIALOG);font.setPrompt("เลือกฟอนต์");font.setContentDescription("ฟอนต์ข้อความ");List<ContentValues> fonts=availableFonts();String[] names=new String[fonts.size()];String selected=existing==null?readerPrefs().getString("font","sans"):existing.font;
        for(int i=0;i<names.length;i++) names[i]=fonts.get(i).getAsString("name");font.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));for(int i=0;i<fonts.size();i++) if(fonts.get(i).getAsString("id").equals(selected)) font.setSelection(i);fields.addView(font);
        fields.addView(text("สีข้อความ",13,MUTED));Spinner color=new Spinner(this,Spinner.MODE_DIALOG);color.setPrompt("เลือกสีข้อความ");color.setContentDescription("สีข้อความ");int[] colors={0xff222222,0xff245BCC,0xffE13A3A,0xff216B58,0xff8E44AD,0xffD36516};String[] colorNames={"ดำ","น้ำเงิน","แดง","เขียว","ม่วง","ส้ม"};color.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,colorNames));int initial=existing==null?readerPrefs().getInt("textColor",0xff245BCC):existing.color;for(int i=0;i<colors.length;i++) if(colors[i]==initial) color.setSelection(i);fields.addView(color);
        TextView sizeLabel=text("",13,MUTED);fields.addView(sizeLabel);SeekBar size=new SeekBar(this);size.setContentDescription("ขนาดข้อความ");size.setMax(66);size.setProgress(Math.round((existing==null?readerPrefs().getInt("textSize",24):existing.size*600))-6);fields.addView(size);
        TextView widthLabel=text("ความกว้างกล่องข้อความ",13,MUTED);fields.addView(widthLabel);SeekBar width=new SeekBar(this);width.setContentDescription("ความกว้างข้อความ");width.setMax(85);width.setProgress(Math.round((existing==null?.6f:existing.width)*100)-10);fields.addView(width);
        Runnable preview=()->{value.setTextColor(colors[color.getSelectedItemPosition()]);value.setTypeface(Fonts.typeface(this,fonts.get(font.getSelectedItemPosition()).getAsString("id")));sizeLabel.setText("ขนาดข้อความ: "+(size.getProgress()+6));widthLabel.setText("ความกว้างกล่อง: "+(width.getProgress()+10)+"%");};
        AdapterView.OnItemSelectedListener onSelect=new AdapterView.OnItemSelectedListener() {public void onItemSelected(AdapterView<?> a,View v,int p,long id) {preview.run();}public void onNothingSelected(AdapterView<?> a) {}};font.setOnItemSelectedListener(onSelect);color.setOnItemSelectedListener(onSelect);
        SeekBar.OnSeekBarChangeListener onSeek=new SeekBar.OnSeekBarChangeListener() {public void onProgressChanged(SeekBar b,int p,boolean user) {preview.run();}public void onStartTrackingTouch(SeekBar b) {}public void onStopTrackingTouch(SeekBar b) {}};size.setOnSeekBarChangeListener(onSeek);width.setOnSeekBarChangeListener(onSeek);preview.run();
        String boxId=existing==null?UUID.randomUUID().toString():existing.id;float bx=existing==null?Math.min(.85f,x):existing.x,by=existing==null?Math.min(.9f,y):existing.y;
        textDraft=()->new TextBox(boxId,value.getText().toString(),fonts.get(font.getSelectedItemPosition()).getAsString("id"),bx,by,(width.getProgress()+10)/100f,(size.getProgress()+6)/600f,colors[color.getSelectedItemPosition()]);
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);AlertDialog.Builder builder=new PocketDialog.Builder(this).setTitle(existing==null?"เพิ่มข้อความบน PDF":"แก้ข้อความบน PDF").setView(scroll).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null);
        if(existing!=null) builder.setNeutralButton("ลบข้อความ",(d,w)->{try {List<TextBox> boxes=TextBox.decode(store.textBoxes(doc,page));boxes.removeIf(b->b.id.equals(boxId));store.saveText(doc,page,TextBox.encode(boxes));if(pageView!=null) pageView.setTextBoxes(store.textBoxes(doc,page));}catch(Exception error) {toast("ลบข้อความไม่สำเร็จ");}});
        activeTextDialog=builder.create();activeTextDialog.setOnDismissListener(d->{textDraft=null;activeTextDialog=null;});activeTextDialog.setOnShowListener(d->activeTextDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value.getText().toString().trim().isEmpty()) {value.setError("กรุณาพิมพ์ข้อความ");return;}
            try {TextBox box=textDraft.get();List<TextBox> boxes=TextBox.decode(store.textBoxes(doc,page));int at=-1;for(int i=0;i<boxes.size();i++) if(boxes.get(i).id.equals(boxId)) at=i;if(at>=0) boxes.set(at,box);else boxes.add(box);store.saveText(doc,page,TextBox.encode(boxes));
                readerPrefs().edit().putString("font",box.font).putInt("textColor",box.color).putInt("textSize",size.getProgress()+6).apply();if(pageView!=null&&doc.equals(pdfId)&&page==pageIndex) pageView.setTextBoxes(store.textBoxes(doc,page));activeTextDialog.dismiss();keyboardOff();
            }catch(Exception error) {toast("บันทึกข้อความไม่ได้ ตรวจสอบพื้นที่ว่างหรือจำนวนกล่องข้อความ");}
        }));activeTextDialog.show();
    }
    private void readerSettings() {
        LinearLayout fields=vertical();fields.setPadding(dp(18),0,dp(18),0);
        CheckBox fit=new CheckBox(this);fit.setText("เปิด PDF ให้พอดีความกว้าง (เลื่อนขึ้น–ลงได้)");fit.setChecked(readerPrefs().getBoolean("fitWidth",true));fields.addView(fit);
        CheckBox awake=new CheckBox(this);awake.setText("เปิดหน้าจอค้างขณะอ่าน PDF");awake.setChecked(readerPrefs().getBoolean("keepAwake",false));fields.addView(awake);
        TextView label=text("ความเข้มไฮไลต์",14,INK);fields.addView(label);SeekBar opacity=new SeekBar(this);opacity.setMax(40);opacity.setProgress(readerPrefs().getInt("opacity",24)-10);fields.addView(opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {public void onProgressChanged(SeekBar b,int progress,boolean user) {label.setText("ความเข้มไฮไลต์: "+(progress+10)+"% · รักษาสีตัวอักษรเดิม");}public void onStartTrackingTouch(SeekBar b) {}public void onStopTrackingTouch(SeekBar b) {}});
        new PocketDialog.Builder(this).setTitle("ตั้งค่าตัวอ่าน PDF").setView(fields).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w)->{readerPrefs().edit().putBoolean("fitWidth",fit.isChecked()).putBoolean("keepAwake",awake.isChecked()).putInt("opacity",opacity.getProgress()+10).apply();applyReaderSettings();}).show();
    }
    private void chooseDefaultFont() {
        List<ContentValues> fonts=availableFonts();String[] names=new String[fonts.size()];for(int i=0;i<names.length;i++) names[i]=fonts.get(i).getAsString("name");
        new PocketDialog.Builder(this).setTitle("ฟอนต์เริ่มต้นสำหรับโน้ตและข้อความใหม่").setItems(names,(d,w)->{readerPrefs().edit().putString("font",fonts.get(w).getAsString("id")).apply();toast("ตั้งฟอนต์เริ่มต้นแล้ว");}).show();
    }
    private void manageFonts() {
        List<ContentValues> fonts=store.query("SELECT * FROM fonts ORDER BY name");String[] names=new String[fonts.size()+1];names[0]="＋ นำเข้าฟอนต์ TTF / OTF (ไม่เกิน 20 MB)";for(int i=0;i<fonts.size();i++) names[i+1]=fonts.get(i).getAsString("name");
        new PocketDialog.Builder(this).setTitle("จัดการฟอนต์ · "+fonts.size()+" / 30").setItems(names,(d,w)->{
            if(w==0) {Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");startActivityForResult(intent,FONT);}
            else {ContentValues font=fonts.get(w-1);new PocketDialog.Builder(this).setTitle(font.getAsString("name")).setItems(new String[]{"ใช้เป็นฟอนต์เริ่มต้น","ลบฟอนต์"},(dialog,index)->{
                if(index==0) {readerPrefs().edit().putString("font",font.getAsString("id")).apply();toast("ตั้งฟอนต์เริ่มต้นแล้ว");}
                else confirm("ลบฟอนต์นี้?","ข้อความที่ใช้ฟอนต์นี้จะกลับไปใช้ฟอนต์มาตรฐาน โดยยังเก็บข้อความไว้",()->{Fonts.remove(this,store,font.getAsString("id"));manageFonts();});
            }).show();}
        }).show();
    }
    private void settings() {
        LinearLayout page=scroll(root,1);LinearLayout about=card(page);
        about.addView(strong("ข้อมูลของคุณอยู่กับคุณ",21));about.addView(text("โน้ตและไฟล์อยู่ในเครื่อง ไม่ต้องสมัครบัญชี และใช้งานได้โดยไม่ต่ออินเทอร์เน็ต",15,MUTED));
        LinearLayout reader=card(page);reader.addView(strong("การอ่านและเครื่องมือ",20));reader.addView(button("จัดลำดับเครื่องมือโน้ต",this::arrangeNoteTools,false));gap(reader,8);reader.addView(button("ตั้งค่าตัวอ่าน PDF",this::readerSettings,false));gap(reader,8);reader.addView(button("จัดการฟอนต์ TTF / OTF",this::manageFonts,false));gap(reader,8);reader.addView(button("เลือกฟอนต์เริ่มต้น",this::chooseDefaultFont,false));gap(reader,8);reader.addView(button("คืนค่าการตั้งค่าเริ่มต้น",()->confirm("คืนค่าการตั้งค่า?","โน้ต เอกสาร รอยเขียน และฟอนต์ที่นำเข้ายังคงอยู่",()->{readerPrefs().edit().clear().apply();applyReaderSettings();toast("คืนค่าการตั้งค่าแล้ว");}),false));
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
        info.addView(button("ใบอนุญาตซอฟต์แวร์",() -> {
            try(InputStream input=getAssets().open("THIRD_PARTY_LICENSES.txt");ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                Backup.copy(input,bytes,256*1024);ScrollView scroll=new ScrollView(this);TextView license=text(bytes.toString("UTF-8"),12,INK);license.setPadding(dp(18),dp(12),dp(18),dp(12));scroll.addView(license);
                new PocketDialog.Builder(this).setTitle("ใบอนุญาตซอฟต์แวร์").setView(scroll).setPositiveButton("ปิด",null).show();
            } catch(Exception error) {toast("เปิดใบอนุญาตไม่ได้");}
        },false));
        LinearLayout help=card(page);help.addView(strong("เกี่ยวกับ PDF",18));help.addView(text("จดหน้านี้ ใช้เขียนบันทึกประกอบเอกสาร โดยเก็บแยกจากต้นฉบับ PDF\nเลือกปากกาหรือไฮไลต์เพื่อเขียนบนหน้า แล้วใช้เครื่องมือ → ส่งออก PDF ที่เขียนแล้ว\nยังไม่รองรับการแก้ข้อความเดิมใน PDF\nไฟล์สำรองมีข้อมูลส่วนตัว กรุณาเก็บในที่ปลอดภัย",14,MUTED));
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
        if(request==CSV) runIo("กำลังส่งออกตาราง…",()->{try(InputStream in=new FileInputStream(new File(getCacheDir(),"table-export.csv"));OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {if(out==null) throw new IOException();Backup.copy(in,out,8L*1024*1024);}return "ส่งออก CSV แล้ว";},()->{});
        else if(request==FONT) runIo("กำลังนำเข้าฟอนต์…",()->{
            String name="font.ttf";try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {if(cursor!=null&&cursor.moveToFirst()) name=cursor.getString(0);}
            try(InputStream input=getContentResolver().openInputStream(uri)) {if(input==null) throw new IOException();Fonts.importFont(this,store,input,name);}return "นำเข้าฟอนต์แล้ว เลือกใช้ได้ในข้อความหรือฟอนต์เริ่มต้น";
        },this::manageFonts);
        else if(request==PICK) runIo("กำลังเก็บไฟล์…",() -> {
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
        else if(request==ANNOTATED&&annotatedExportId!=null) {
            String document=annotatedExportId;
            runIo("กำลังสร้าง PDF พร้อมรอยเขียน…",() -> {
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {if(out==null) throw new IOException();AnnotatedPdf.write(this,store,document,out);}
                return "ส่งออก PDF พร้อมรอยเขียนแล้ว ต้นฉบับในแอปยังคงเดิม";
            },() -> {});
        }
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
            try {result=task.run();ok=true;}catch(Exception|OutOfMemoryError e) {result="ทำรายการไม่สำเร็จ ตรวจสอบไฟล์และพื้นที่ว่าง (หากเป็นไฟล์ส่งออก อาจต้องลบไฟล์ที่บันทึกไม่ครบแล้วลองใหม่)";ok=false;}
            final String output=result;final boolean completed=ok;
            runOnUiThread(() -> {
                busy=false;if(progress!=null) {progress.dismiss();progress=null;}
                if(isDestroyed()||isFinishing()) {store.close();return;}
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                if(completed) success.run();toast(output);
            });
        },"NotePocket-IO").start();
    }
    public boolean isWorkIdle() {return (library==null||editor||pdf!=null||!library.isLoading())&&(noteSaver==null||!noteSaver.isRunning())&&!pdfRendering;}
    @Override protected void onResume() {super.onResume();if(libraryReleased&&library!=null&&!editor&&pdf==null) {libraryReleased=false;library.reload();}if(pdfImageReleased&&pdf!=null) renderPage(pageIndex);}
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);Fonts.trimMemory();noteHistory.clear();if(editor) updateNoteTools();if(pageView!=null) pageView.trimCaches();
        if(level>=TRIM_MEMORY_UI_HIDDEN) {if(library!=null&&!editor&&pdf==null) {library.releaseRows();libraryReleased=true;}if(pdf!=null&&pageView!=null) {pageView.commitGesture();pdfGeneration++;pdfRendering=false;pageView.releaseImage();if(pageBitmap!=null) {pageBitmap.recycle();pageBitmap=null;}pdfImageReleased=true;}}
    }
    @Override protected void onPause() {if(pageView!=null) pageView.commitGesture();saveNote(false);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state) {
        saveNote(false);state.putString("tab",tab);state.putString("filter",filter);state.putString("query",query);state.putBoolean("editor",editor);state.putLong("noteId",noteId);
        state.putBoolean("soapOpen",activeSoapDialog!=null&&activeSoapDialog.isShowing());
        if(editor&&body!=null) {state.putInt("noteStart",body.getSelectionStart());state.putInt("noteEnd",body.getSelectionEnd());state.putInt("noteScroll",noteScroll.getScrollY());state.putString("expandedTable",expandedTableId);state.putInt("tablePage",tablePages.getOrDefault(expandedTableId,0));View focus=getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String) state.putString("tableFocus",(String)focus.getTag());}
        state.putLong("pendingAttach",pendingAttach);state.putString("exportId",exportId);state.putString("annotatedExportId",annotatedExportId);
        if(pdf!=null) {state.putBoolean("fullscreen",fullscreen);state.putInt("pdfMode",pageView.getMode());if(textDraft!=null) state.putString("textDraft",TextBox.encode(java.util.Collections.singletonList(textDraft.get())));state.putString("pdfId",pdfId);state.putString("pdfName",pdfName);state.putInt("page",pageIndex);state.putLong("pdfReturnNote",pdfReturnNote);}
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if(busy) return;
        if(paletteWindow!=null&&paletteWindow.isShowing()) paletteWindow.dismiss();else if(pdf!=null&&fullscreen) setFullscreen(false);else if(pdf!=null) returnFromPdf();else if(editor) {if(saveNote(false)) {keyboardOff();home();}}
        else if(!tab.equals("notes")||!filter.equals("all")) {tab="notes";filter="all";query="";home();}else super.onBackPressed();
    }
    @Override protected void onDestroy() {if(activeSoapDialog!=null) activeSoapDialog.dismiss();if(activeTextDialog!=null) activeTextDialog.dismiss();handler.removeCallbacks(autosave);closePdf();if(progress!=null) {progress.dismiss();progress=null;}if(library!=null) library.close();if(noteSaver!=null) noteSaver.close();handler.removeCallbacks(cursorFollower);pdfWorker.shutdown();if(!busy) store.close();super.onDestroy();}
}
