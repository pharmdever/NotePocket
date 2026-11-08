package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Native note editor with atomic, derived Mermaid objects between editable paragraphs. */
final class InlineNoteEditText extends EditText implements AutoCloseable {
    interface DiagramClick {void open(int start,int end);}
    private InlineDiagramRenderer renderer;private DiagramClick click;
    private boolean closing,snapping,rebuilding;private int decodes;private float downX,downY;private long downTime;
    private final Set<String> pendingDecode=new HashSet<>(),failed=new HashSet<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    InlineNoteEditText(Context c){super(c);setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);if(Build.VERSION.SDK_INT>=30)setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);setFilters(new InputFilter[]{this::protectDiagram});}
    @Override public int getAutofillType(){return View.AUTOFILL_TYPE_NONE;}
    @Override public android.view.autofill.AutofillValue getAutofillValue(){return null;}
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void attach(LinearLayout host,DiagramClick click){this.click=click;renderer=new InlineDiagramRenderer(getContext(),host,(key,success)->{if(closing)return;if(success)load(key);else{failed.add(key);for(InlineDiagramSpan span:spans())if(span.key.equals(key))span.failed=true;invalidate();}});refreshDiagrams();}
    boolean isIdle(){return !isLayoutRequested()&&getLayout()!=null&&decodes==0&&(renderer==null||renderer.isIdle());}
    Bitmap bitmap(String key){Bitmap b=bitmaps.get(key);if(b==null&&!closing&&!failed.contains(key)&&DiagramCache.file(getContext(),key).isFile())load(key);return b;}
    void retryFailures(){failed.clear();if(renderer!=null)renderer.clearFailures();}
    InlineDiagramSpan[] spans(){Editable e=getText();return e==null?new InlineDiagramSpan[0]:e.getSpans(0,e.length(),InlineDiagramSpan.class);}
    void refreshDiagrams(){if(closing||renderer==null||rebuilding)return;rebuilding=true;try{Editable text=getText();List<DiagramSource.Block> blocks=DiagramSource.find(text.toString());Map<String,InlineDiagramSpan> old=new HashMap<>();for(InlineDiagramSpan span:spans()){old.put(span.key,span);text.removeSpan(span);}for(DiagramSource.Block b:blocks){String code;try{code=DiagramSource.clean(b.code);}catch(IllegalArgumentException e){continue;}String key=DiagramCache.key(code);InlineDiagramSpan previous=old.get(key),span=new InlineDiagramSpan(this,key);if(previous!=null){span.imageWidth=previous.imageWidth;span.imageHeight=previous.imageHeight;}span.failed=failed.contains(key);text.setSpan(span,b.start,b.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(bitmaps.get(key)==null){if(DiagramCache.file(getContext(),key).isFile())load(key);else if(!span.failed)renderer.request(code);}}}finally{rebuilding=false;}requestLayout();invalidate();}
    private void load(String key){if(closing||pendingDecode.contains(key))return;pendingDecode.add(key);decodes++;int width=Math.max(dp(320),getWidth());File file=DiagramCache.file(getContext(),key);worker.execute(()->{Bitmap bitmap=null;try{bitmap=DiagramCache.decode(file,width);DiagramCache.prune(getContext());if(bitmap==null)file.delete();}catch(Exception|OutOfMemoryError ignored){}final Bitmap result=bitmap;post(()->{pendingDecode.remove(key);decodes--;if(closing)return;if(result!=null){bitmaps.put(key,result);for(InlineDiagramSpan span:spans())if(span.key.equals(key)){InlineDiagramSpan replacement=new InlineDiagramSpan(this,key);replacement.imageWidth=result.getWidth();replacement.imageHeight=result.getHeight();Editable e=getText();int start=e.getSpanStart(span),end=e.getSpanEnd(span);e.removeSpan(span);if(start>=0&&end>start)e.setSpan(replacement,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}boolean include=getIncludeFontPadding();setIncludeFontPadding(!include);setIncludeFontPadding(include);requestLayout();invalidate();}else{failed.add(key);for(InlineDiagramSpan span:spans())if(span.key.equals(key))span.failed=true;invalidate();}});});}
    private CharSequence protectDiagram(CharSequence source,int start,int end,Spanned dest,int dstart,int dend){if(rebuilding||closing)return null;for(InlineDiagramSpan span:dest.getSpans(0,dest.length(),InlineDiagramSpan.class)){int a=dest.getSpanStart(span),b=dest.getSpanEnd(span);boolean all=dstart<=a&&dend>=b;if(!all&&((dstart<b&&dend>a)||(dstart<a&&dend==a)||(dstart==b&&dend>b&&dest.charAt(b)=='\n')))return dest.subSequence(dstart,dend).toString();if(dstart==dend&&end>start){String value=source.subSequence(start,end).toString();if(dstart==a&&!value.endsWith("\n"))return value+"\n";if(dstart==b&&!value.startsWith("\n"))return "\n"+value;}}return null;}
    @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);if(snapping||rebuilding||closing||getText()==null)return;int lo=Math.min(start,end),hi=Math.max(start,end),from=lo,to=hi;for(InlineDiagramSpan span:spans()){int a=getText().getSpanStart(span),b=getText().getSpanEnd(span);if(lo==hi&&lo>a&&lo<b){from=to=lo-a<b-lo?a:b;}else if(lo!=hi){if(lo>a&&lo<b)from=a;if(hi>a&&hi<b)to=b;}}if(from!=lo||to!=hi){snapping=true;try{setSelection(start<=end?from:to,start<=end?to:from);}finally{snapping=false;}}}
    private InlineDiagramSpan hit(float y){android.text.Layout layout=getLayout();if(layout==null)return null;float local=y-getTotalPaddingTop()+getScrollY();for(InlineDiagramSpan span:spans()){int at=getText().getSpanStart(span);if(at<0)continue;int line=layout.getLineForOffset(at),top=layout.getLineTop(line);if(local>=top&&local<layout.getLineBottom(line))return span;}return null;}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downTime=e.getEventTime();}if(e.getAction()==MotionEvent.ACTION_UP&&e.getEventTime()-downTime<400&&Math.abs(e.getX()-downX)<dp(10)&&Math.abs(e.getY()-downY)<dp(10)){InlineDiagramSpan span=hit(e.getY());if(span!=null&&click!=null){performClick();click.open(getText().getSpanStart(span),getText().getSpanEnd(span));return true;}}return super.onTouchEvent(e);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw&&renderer!=null&&!closing)post(this::refreshDiagrams);}
    void trim(){bitmaps.evictAll();}
    @Override public void close(){closing=true;if(renderer!=null)renderer.close();worker.shutdownNow();bitmaps.evictAll();}
}
