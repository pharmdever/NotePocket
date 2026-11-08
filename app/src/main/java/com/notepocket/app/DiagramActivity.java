package com.notepocket.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Isolated offline Mermaid viewer. Pasted source is data, never executable JavaScript. */
public final class DiagramActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private static final int PNG=31;
    private String token;private JSONObject draft;private EditText code;private WebView web;
    private LinearLayout page;private FrameLayout stage;private TextView status;private Button codeTab,previewTab;
    private ReaderIconButton export;private boolean preview,loaded,ready;private volatile boolean destroyed,exporting;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Bridge bridge=new Bridge();
    private long frameGeneration;
    private final Runnable exportTimeout=()->{if(exporting&&!destroyed) {exporting=false;bridge.abort();status.setText("สร้างรูปนานเกินไป กรุณาลองอีกครั้ง");export.setEnabled(ready&&preview);}};
    private final Runnable timeout=()->{if(!ready&&preview&&!destroyed) {status.setText("แผนภาพใช้เวลานาน กรุณาลดจำนวนกล่องหรือเส้น แล้วเปิดใหม่");if(web!=null)web.stopLoading();}};
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);SyncRuntime.enterEditor();token=state==null?getIntent().getStringExtra("draft"):state.getString("draft");
        try {draft=DiagramDraft.read(this,token);}catch(Exception e) {Toast.makeText(this,"เปิดร่างแผนภาพไม่ได้ กรุณากลับไปเปิดจากโน้ตอีกครั้ง",Toast.LENGTH_LONG).show();finish();return;}
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(PocketTheme.CREAM);page.setPadding(dp(12),dp(8),dp(12),dp(8));setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{int bottom=insets.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=30)bottom=Math.max(insets.getInsets(WindowInsets.Type.ime()).bottom,insets.getInsets(WindowInsets.Type.systemBars()).bottom);v.setPadding(dp(12),insets.getSystemWindowInsetTop()+dp(4),dp(12),bottom+dp(4));return insets;});page.requestApplyInsets();
        LinearLayout header=row();addIcon(header,"back","กลับจากแผนภาพ",null,this::finish);TextView title=text("แผนภาพ Mermaid",20);title.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));addIcon(header,"check","บันทึกแผนภาพในโน้ต",null,this::saveToNote);page.addView(header);
        LinearLayout tabs=row();codeTab=button("โค้ด",()->showMode(false));previewTab=button("แผนภาพ",()->showMode(true));tabs.addView(codeTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(previewTab,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(tabs);
        status=text("วางโค้ด Mermaid จาก ChatGPT หรือใช้ตัวอย่าง",13);page.addView(status);
        stage=new FrameLayout(this);page.addView(stage,new LinearLayout.LayoutParams(-1,0,1));code=new EditText(this);code.setContentDescription("โค้ด Mermaid");code.setHint("flowchart TD\n    A[เริ่มต้น] --> B[สิ้นสุด]");code.setGravity(Gravity.TOP);code.setTextColor(PocketTheme.INK);code.setBackgroundColor(android.graphics.Color.WHITE);code.setPadding(dp(12),dp(12),dp(12),dp(12));code.setTextSize(15);code.setTypeface(android.graphics.Typeface.MONOSPACE);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DiagramSource.MAX_CODE+100)});code.setSaveEnabled(false);code.setText(draft.optString("code"));stage.addView(code,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout actions=row();addIcon(actions,"zoom-out","ย่อแผนภาพ",null,()->{if(web!=null&&preview)web.zoomOut();});addIcon(actions,"zoom-in","ขยายแผนภาพ",null,()->{if(web!=null&&preview)web.zoomIn();});addIcon(actions,"full","แสดงแผนภาพพอดีจอ",null,()->{if(web!=null&&preview) {ready=false;web.reload();}});export=addIcon(actions,"share","บันทึกแผนภาพเป็น PNG","PNG",this::exportPng);actions.addView(button("ตัวอย่าง",this::sample),new LinearLayout.LayoutParams(0,dp(52),1));page.addView(actions);
        code.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){ready=false;export.setEnabled(false);}public void afterTextChanged(Editable e){}});
        showMode(state==null?!draft.optString("code").trim().isEmpty():state.getBoolean("preview"));
    }
    private LinearLayout row() {LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private TextView text(String value,int size) {TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(PocketTheme.INK);t.setPadding(dp(4),dp(6),dp(4),dp(6));return t;}
    private Button button(String title,Runnable run) {Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(PocketTheme.FOREST);b.setBackground(PocketTheme.ripple(this,PocketTheme.SAGE,12));b.setOnClickListener(v->{if(!exporting)run.run();});return b;}
    private ReaderIconButton addIcon(LinearLayout parent,String icon,String label,String caption,Runnable run) {ReaderIconButton b=new ReaderIconButton(this,icon,label,caption,()->{if(!exporting)run.run();});parent.addView(b,new LinearLayout.LayoutParams(dp(52),dp(52)));return b;}
    private void sample() {new PocketDialog.Builder(this).setTitle("ใช้ตัวอย่างผังการเจริญของพืช?").setMessage("แทนที่เฉพาะร่างแผนภาพนี้ ยังไม่เปลี่ยนโน้ตจนกดบันทึก").setNegativeButton("ยกเลิก",null).setPositiveButton("ใช้ตัวอย่าง",(d,w)->{code.setText(DiagramSource.SAMPLE);showMode(true);}).show();}
    private void showMode(boolean showPreview) {
        if(exporting)return;preview=showPreview;codeTab.setSelected(!preview);previewTab.setSelected(preview);codeTab.setTextColor(!preview?Color.WHITE:PocketTheme.FOREST);previewTab.setTextColor(preview?Color.WHITE:PocketTheme.FOREST);codeTab.setBackground(PocketTheme.ripple(this,!preview?PocketTheme.FOREST:PocketTheme.SAGE,12));previewTab.setBackground(PocketTheme.ripple(this,preview?PocketTheme.FOREST:PocketTheme.SAGE,12));code.setVisibility(preview?View.GONE:View.VISIBLE);
        if(!preview) {handler.removeCallbacks(timeout);if(web!=null)web.setVisibility(View.GONE);status.setText("แก้โค้ดแล้วกดแผนภาพ · กด ✓ เพื่อบันทึกลงโน้ต");export.setEnabled(false);return;}
        code.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(code.getWindowToken(),0);
        try {DiagramSource.clean(code.getText().toString());}catch(IllegalArgumentException e) {showMode(false);status.setText(e.getMessage());return;}
        if(web==null)createWeb();web.setVisibility(View.VISIBLE);if(loaded)render();
    }
    @SuppressWarnings("SetJavaScriptEnabled") private void createWeb() {
        loaded=false;ready=false;web=new WebView(this);web.setContentDescription("พื้นที่แผนภาพ Mermaid");web.setBackgroundColor(Color.WHITE);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDomStorageEnabled(false);settings.setDatabaseEnabled(false);settings.setBlockNetworkLoads(true);settings.setBlockNetworkImage(false); // CSP and request interception block external images; local SVG blobs must decode for PNG.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setSupportZoom(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);web.addJavascriptInterface(bridge,"NotePocketDiagram");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {String path=request.getUrl().getPath();String name=path==null?"":path.substring(path.lastIndexOf('/')+1);if("https".equals(request.getUrl().getScheme())&&"appassets.androidplatform.net".equals(request.getUrl().getHost())&&path.equals("/diagram/"+name)&&Arrays.asList("index.html","viewer.js","mermaid.min.js").contains(name))try {return new WebResourceResponse(name.endsWith("html")?"text/html":"application/javascript","UTF-8",getAssets().open("diagram/"+name));}catch(IOException ignored) {}return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {return true;}
            @Override public void onPageFinished(WebView v,String url) {if(!destroyed&&url.equals(ORIGIN+"/diagram/index.html")) {loaded=true;if(preview)render();}}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {ready=false;loaded=false;stage.removeView(v);v.destroy();web=null;status.setText("ตัวแสดงแผนภาพปิดลง กรุณาลดขนาดแผนภาพแล้วลองใหม่");export.setEnabled(false);return true;}
        });stage.addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl(ORIGIN+"/diagram/index.html");
    }
    private void render() {frameGeneration++;try {String value=DiagramSource.clean(code.getText().toString());ready=false;export.setEnabled(false);status.setText("กำลังสร้างแผนภาพ…");handler.removeCallbacks(timeout);handler.postDelayed(timeout,15000);web.evaluateJavascript("window.drawDiagram("+JSONObject.quote(value)+")",null);}catch(IllegalArgumentException e) {status.setText(e.getMessage());}}
    private boolean persist() {try {draft.put("code",code.getText().toString());DiagramDraft.write(this,token,draft);return true;}catch(Exception e) {Toast.makeText(this,"เก็บร่างไม่ได้ ตรวจสอบพื้นที่ว่าง",Toast.LENGTH_LONG).show();return false;}}
    private void saveToNote() {try {String value=DiagramSource.clean(code.getText().toString());code.setText(value);if(!persist())return;setResult(RESULT_OK,new Intent().putExtra("draft",token));finish();}catch(IllegalArgumentException e) {showMode(false);status.setText(e.getMessage());}}
    private void exportPng() {if(!ready||web==null||exporting)return;exporting=true;android.util.Log.i("NotePocketDiagram","PNG requested");export.setEnabled(false);status.setText("กำลังสร้างรูป PNG…");handler.postDelayed(exportTimeout,15000);web.evaluateJavascript("window.exportDiagram()",null);}
    private File pngFile() {return new File(DiagramDraft.file(this,token).getParentFile(),token+".png");}
    private final class Bridge {
        private OutputStream out;private File pending;private int next;private long size;
        @JavascriptInterface public void status(String value) {runOnUiThread(()->{if(destroyed)return;if(value.equals("ready")) {handler.removeCallbacks(timeout);if(web==null)return;long generation=frameGeneration;web.postVisualStateCallback(generation,new WebView.VisualStateCallback(){public void onComplete(long id){if(destroyed||generation!=frameGeneration)return;ready=true;web.invalidate();status.setText("แผนภาพพร้อมแล้ว · ใช้สองนิ้วซูม และลากเพื่อเลื่อน");export.setEnabled(!exporting&&preview);}});}else if(value.equals("error")) {ready=false;handler.removeCallbacks(timeout);status.setText("แสดงแผนภาพไม่ได้ ตรวจไวยากรณ์ในแท็บโค้ด");export.setEnabled(false);}else if(value.equals("export-error")) {handler.removeCallbacks(exportTimeout);exporting=false;status.setText("สร้างรูปไม่ได้ ลองลดขนาดแผนภาพแล้วส่งออกอีกครั้ง");export.setEnabled(ready&&preview);}});}
        @JavascriptInterface public synchronized void beginPng() {abort();if(!exporting||destroyed)return;try {pending=new File(pngFile().getPath()+".tmp");out=new FileOutputStream(pending);next=0;size=0;}catch(IOException e) {abort();status("export-error");}}
        @JavascriptInterface public synchronized void pngChunk(int index,String chunk) {if(out==null||index!=next||chunk==null||chunk.length()>48000){abort();status("export-error");return;}try {byte[] bytes=android.util.Base64.decode(chunk,android.util.Base64.NO_WRAP);size+=bytes.length;if(size>12*1024*1024)throw new IOException();out.write(bytes);next++;}catch(Exception e) {abort();status("export-error");}}
        @JavascriptInterface public synchronized void endPng() {try {if(out==null||size<32)throw new IOException();out.close();out=null;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(pending.getPath(),bounds);if(!"image/png".equals(bounds.outMimeType)||bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>3072||bounds.outHeight>3072||(long)bounds.outWidth*bounds.outHeight>6000000)throw new IOException();if(!pending.renameTo(pngFile()))throw new IOException();pending=null;runOnUiThread(()->{if(destroyed)return;handler.removeCallbacks(exportTimeout);exporting=false;export.setEnabled(ready&&preview);status.setText("เลือกตำแหน่งบันทึกรูป PNG");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/png");i.putExtra(Intent.EXTRA_TITLE,"NotePocket-diagram.png");startActivityForResult(i,PNG);});}catch(Exception e) {abort();status("export-error");}}
        synchronized void abort() {if(out!=null)try{out.close();}catch(IOException ignored){}out=null;if(pending!=null)pending.delete();pending=null;}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {super.onActivityResult(request,result,data);if(request!=PNG||result!=RESULT_OK||data==null||data.getData()==null)return;Uri destination=data.getData();exporting=true;status.setText("กำลังบันทึกรูป…");worker.execute(()->{boolean ok=false;try(InputStream in=new FileInputStream(pngFile());OutputStream out=getContentResolver().openOutputStream(destination,"wt")) {if(out==null)throw new IOException();Backup.copy(in,out,12L*1024*1024);ok=true;}catch(Exception ignored){}final boolean success=ok;runOnUiThread(()->{if(destroyed)return;exporting=false;export.setEnabled(ready&&preview);status.setText(success?"บันทึกรูป PNG แล้ว":"บันทึกไม่ได้ ตรวจสอบพื้นที่และตำแหน่งที่เลือก");});});}
    @Override protected void onSaveInstanceState(Bundle state) {persist();state.putString("draft",token);state.putBoolean("preview",preview);super.onSaveInstanceState(state);}
    @Override protected void onPause() {if(code!=null)persist();super.onPause();}
    @Override public void onBackPressed() {if(!exporting)finish();}
    @Override protected void onDestroy() {destroyed=true;handler.removeCallbacksAndMessages(null);bridge.abort();if(web!=null) {web.removeJavascriptInterface("NotePocketDiagram");web.stopLoading();stage.removeView(web);web.destroy();web=null;}worker.shutdown();SyncRuntime.leaveEditor();super.onDestroy();}
}
