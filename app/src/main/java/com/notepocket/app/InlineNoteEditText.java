package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.inputmethod.*;
import androidx.core.view.*;
import androidx.core.view.inputmethod.*;
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
    interface PhotoPaste {boolean accept(List<Uri> uris,Runnable release);}
    private PhotoPaste photoPaste;
    Runnable selectionChanged;
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
    Bitmap bitmap(String key){Bitmap b=bitmaps.get(key);if(b==null&&!closing&&!failed.contains(key)&&mediaFile(key).isFile())load(key);return b;}
    void retryFailures(){failed.clear();if(renderer!=null)renderer.clearFailures();}
    InlineDiagramSpan[] spans(){Editable e=getText();return e==null?new InlineDiagramSpan[0]:e.getSpans(0,e.length(),InlineDiagramSpan.class);}
    void refreshDiagrams(){
        if(closing||renderer==null||rebuilding)return;rebuilding=true;
        try{
            Editable text=getText();Map<String,InlineDiagramSpan> old=new HashMap<>();for(InlineDiagramSpan span:spans()){old.put(span.key,span);text.removeSpan(span);}
            for(DiagramSource.Block b:DiagramSource.find(text.toString())){
                String code;try{code=DiagramSource.clean(b.code);}catch(IllegalArgumentException e){continue;}
                String key=DiagramCache.key(code);addSpan(text,old,key,false,b.start,b.end);if(bitmaps.get(key)==null){if(mediaFile(key).isFile())load(key);else if(!failed.contains(key))renderer.request(code);}
            }
            int count=0;for(InlineImages.Block b:InlineImages.find(text.toString())){if(count++>=InlineImages.MAX_IMAGES)break;String key="photo:"+b.id;addSpan(text,old,key,true,b.start,b.end);if(bitmaps.get(key)==null&&!failed.contains(key))load(key);}
        }finally{rebuilding=false;}
        requestLayout();invalidate();
    }
    private void addSpan(Editable text,Map<String,InlineDiagramSpan> old,String key,boolean photo,int start,int end){InlineDiagramSpan span=new InlineDiagramSpan(this,key,photo),previous=old.get(key);Bitmap cached=bitmaps.get(key);if(cached!=null){span.imageWidth=cached.getWidth();span.imageHeight=cached.getHeight();}else if(previous!=null){span.imageWidth=previous.imageWidth;span.imageHeight=previous.imageHeight;}span.failed=failed.contains(key);text.setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    private void load(String key){if(closing||pendingDecode.contains(key))return;pendingDecode.add(key);decodes++;int width=Math.max(dp(320),getWidth());boolean photo=key.startsWith("photo:");File file=mediaFile(key);worker.execute(()->{Bitmap bitmap=null;try{bitmap=photo?InlineImages.decode(file,width,1500000):DiagramCache.decode(file,width);if(!photo){DiagramCache.prune(getContext());if(bitmap==null)file.delete();}}catch(Exception|OutOfMemoryError ignored){}final Bitmap result=bitmap;post(()->{pendingDecode.remove(key);decodes--;if(closing)return;if(result!=null){bitmaps.put(key,result);for(InlineDiagramSpan span:spans())if(span.key.equals(key)){InlineDiagramSpan replacement=new InlineDiagramSpan(this,key,photo);replacement.imageWidth=result.getWidth();replacement.imageHeight=result.getHeight();Editable e=getText();int start=e.getSpanStart(span),end=e.getSpanEnd(span);e.removeSpan(span);if(start>=0&&end>start)e.setSpan(replacement,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}boolean include=getIncludeFontPadding();setIncludeFontPadding(!include);setIncludeFontPadding(include);requestLayout();invalidate();}else{failed.add(key);for(InlineDiagramSpan span:spans())if(span.key.equals(key))span.failed=true;invalidate();}});});}
    private File mediaFile(String key){return key.startsWith("photo:")?InlineImages.file(getContext(),key.substring(6)):DiagramCache.file(getContext(),key);}
    private boolean imageUri(Uri uri){if(uri==null||!"content".equals(uri.getScheme()))return false;try{String type=getContext().getContentResolver().getType(uri);return type!=null&&type.startsWith("image/");}catch(Exception e){return false;}}
    private List<Uri> imageUris(ClipData clip){List<Uri> uris=new ArrayList<>();for(int i=0;i<clip.getItemCount();i++){Uri u=clip.getItemAt(i).getUri();if(imageUri(u))uris.add(u);}return uris;}
    void setPhotoPaste(PhotoPaste listener){photoPaste=listener;ViewCompat.setOnReceiveContentListener(this,new String[]{"image/*"},(view,payload)->{if(photoPaste==null)return payload;android.util.Pair<ContentInfoCompat,ContentInfoCompat> parts=payload.partition(item->imageUri(item.getUri()));if(parts.first==null)return payload;return photoPaste.accept(imageUris(parts.first.getClip()),()->{})?parts.second:payload;});}
    @Override public boolean onTextContextMenuItem(int id){if(photoPaste!=null&&(id==android.R.id.paste||id==android.R.id.pasteAsPlainText)){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();if(clip!=null){List<Uri> uris=imageUris(clip);if(!uris.isEmpty()&&uris.size()==clip.getItemCount()&&photoPaste.accept(uris,()->{}))return true;}}return super.onTextContextMenuItem(id);}
    @SuppressWarnings("deprecation") @Override public InputConnection onCreateInputConnection(EditorInfo info){InputConnection base=super.onCreateInputConnection(info);if(base==null||photoPaste==null)return base;EditorInfoCompat.setContentMimeTypes(info,new String[]{"image/*"});return InputConnectionCompat.createWrapper(base,info,(content,flags,options)->{if(!content.getDescription().hasMimeType("image/*"))return false;boolean permission=(flags&InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION)!=0;try{if(permission)content.requestPermission();}catch(Exception e){return false;}Runnable release=()->{if(permission)content.releasePermission();};boolean accepted=photoPaste.accept(Collections.singletonList(content.getContentUri()),release);if(!accepted)release.run();return accepted;});}
    private CharSequence protectDiagram(CharSequence source,int start,int end,Spanned dest,int dstart,int dend){if(rebuilding||closing)return null;for(InlineDiagramSpan span:dest.getSpans(0,dest.length(),InlineDiagramSpan.class)){int a=dest.getSpanStart(span),b=dest.getSpanEnd(span);boolean all=dstart<=a&&dend>=b;if(!all&&((dstart<b&&dend>a)||(dstart<a&&dend==a)||(dstart==b&&dend>b&&dest.charAt(b)=='\n')))return dest.subSequence(dstart,dend).toString();if(dstart==dend&&end>start){String value=source.subSequence(start,end).toString();if(dstart==a&&!value.endsWith("\n"))return value+"\n";if(dstart==b&&!value.startsWith("\n"))return "\n"+value;}}return null;}
    @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);if(selectionChanged!=null)selectionChanged.run();if(snapping||rebuilding||closing||getText()==null)return;int lo=Math.min(start,end),hi=Math.max(start,end),from=lo,to=hi;for(InlineDiagramSpan span:spans()){int a=getText().getSpanStart(span),b=getText().getSpanEnd(span);if(lo==hi&&lo>a&&lo<b){from=to=lo-a<b-lo?a:b;}else if(lo!=hi){if(lo>a&&lo<b)from=a;if(hi>a&&hi<b)to=b;}}if(from!=lo||to!=hi){snapping=true;try{setSelection(start<=end?from:to,start<=end?to:from);}finally{snapping=false;}}}
    private InlineDiagramSpan hit(float y){android.text.Layout layout=getLayout();if(layout==null)return null;float local=y-getTotalPaddingTop()+getScrollY();for(InlineDiagramSpan span:spans()){int at=getText().getSpanStart(span);if(at<0)continue;int line=layout.getLineForOffset(at),top=layout.getLineTop(line);if(local>=top&&local<layout.getLineBottom(line))return span;}return null;}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downTime=e.getEventTime();}if(e.getAction()==MotionEvent.ACTION_UP&&e.getEventTime()-downTime<400&&Math.abs(e.getX()-downX)<dp(10)&&Math.abs(e.getY()-downY)<dp(10)){InlineDiagramSpan span=hit(e.getY());if(span!=null&&click!=null){performClick();click.open(getText().getSpanStart(span),getText().getSpanEnd(span));return true;}}return super.onTouchEvent(e);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw&&renderer!=null&&!closing)post(this::refreshDiagrams);}
    void trim(){bitmaps.evictAll();}
    @Override public void close(){closing=true;if(renderer!=null)renderer.close();worker.shutdownNow();bitmaps.evictAll();}
}
