package com.notepocket.app;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.LinearLayout;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

/** One bounded local renderer per open editor, serializing work without replacing editor content. */
final class InlineDiagramRenderer implements AutoCloseable {
    interface Listener {void complete(String key,boolean success);}
    private final Context context;private final LinearLayout host;private final Listener listener;private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,String> queue=new LinkedHashMap<>();private final Set<String> failed=new HashSet<>();
    private WebView web;private boolean loaded,exportStarted;private volatile boolean closed;private volatile String active;private final Bridge bridge=new Bridge();
    private final Runnable timeout=()->{handler.removeCallbacksAndMessages(null);if(active!=null){String key=active;active=null;failed.add(key);bridge.abort();destroyWeb();listener.complete(key,false);next();}else if(!queue.isEmpty()){List<String> keys=new ArrayList<>(queue.keySet());queue.clear();destroyWeb();for(String key:keys){failed.add(key);listener.complete(key,false);}}};
    InlineDiagramRenderer(Context context,LinearLayout host,Listener listener){this.context=context;this.host=host;this.listener=listener;}
    void clearFailures(){failed.clear();}
    boolean isIdle(){return active==null&&queue.isEmpty();}
    void request(String code){String key=DiagramCache.key(code);if(closed||failed.contains(key)||key.equals(active)||queue.containsKey(key))return;if(DiagramCache.file(context,key).isFile()){listener.complete(key,true);return;}if(queue.size()<DiagramSource.MAX_BLOCKS){queue.put(key,code);next();}}
    private void next(){if(closed||active!=null||queue.isEmpty())return;if(web==null){createWeb();return;}if(!loaded)return;Map.Entry<String,String> job=queue.entrySet().iterator().next();active=job.getKey();String code=job.getValue();queue.remove(active);exportStarted=false;handler.postDelayed(timeout,20000);String k=JSONObject.quote(active);web.evaluateJavascript("(function(k){window.NotePocketDiagram={status:function(v){NativeInline.status(k,v)},beginPng:function(){NativeInline.beginPng(k)},pngChunk:function(i,s){NativeInline.pngChunk(k,i,s)},endPng:function(){NativeInline.endPng(k)}};})("+k+");window.drawDiagram("+JSONObject.quote(code)+")",null);}
    @SuppressWarnings("SetJavaScriptEnabled") private void createWeb(){
        loaded=false;handler.postDelayed(timeout,15000);web=new WebView(context);web.setAlpha(.01f);web.setFocusable(false);web.setClickable(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setDomStorageEnabled(false);s.setBlockNetworkLoads(true);s.setBlockNetworkImage(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.addJavascriptInterface(bridge,"NativeInline");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){String path=request.getUrl().getPath();String name=path==null?"":path.substring(path.lastIndexOf('/')+1);if("https".equals(request.getUrl().getScheme())&&"appassets.androidplatform.net".equals(request.getUrl().getHost())&&("/diagram/"+name).equals(path)&&Arrays.asList("index.html","viewer.js","mermaid.min.js").contains(name))try{return new WebResourceResponse(name.endsWith("html")?"text/html":"application/javascript","UTF-8",context.getAssets().open("diagram/"+name));}catch(IOException ignored){}return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView v,String url){if(!closed&&!loaded&&url.equals("https://appassets.androidplatform.net/diagram/index.html")){loaded=true;handler.removeCallbacks(timeout);next();}}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){if(closed)return true;if(active!=null)handler.post(timeout);else handler.post(timeout);return true;}
        });host.addView(web,new LinearLayout.LayoutParams(-1,1));web.loadUrl("https://appassets.androidplatform.net/diagram/index.html");
    }
    private void finished(String expected,boolean success){if(closed||active==null||!active.equals(expected))return;handler.removeCallbacks(timeout);String key=active;active=null;if(!success)failed.add(key);listener.complete(key,success);next();}
    private final class Bridge {
        private File temp;private OutputStream out;private int nextChunk;private long bytes;
        @JavascriptInterface public void status(String key,String value){handler.post(()->{if(closed||!key.equals(active))return;if(value.equals("ready")&&!exportStarted){exportStarted=true;web.evaluateJavascript("window.exportDiagram()",null);}else if(value.equals("error")||value.equals("export-error")){abort();finished(key,false);}});}
        @JavascriptInterface public synchronized void beginPng(String key){if(closed||!key.equals(active))return;abort();try{temp=new File(DiagramCache.file(context,active).getPath()+".tmp");out=new FileOutputStream(temp);nextChunk=0;bytes=0;}catch(Exception e){abort();handler.post(()->finished(key,false));}}
        @JavascriptInterface public synchronized void pngChunk(String key,int index,String chunk){if(closed||!key.equals(active))return;if(out==null||chunk==null||chunk.length()>48000||index!=nextChunk){abort();handler.post(()->finished(key,false));return;}try{byte[] data=android.util.Base64.decode(chunk,android.util.Base64.NO_WRAP);bytes+=data.length;if(bytes>12L*1024*1024)throw new IOException();out.write(data);nextChunk++;}catch(Exception e){abort();handler.post(()->finished(key,false));}}
        @JavascriptInterface public synchronized void endPng(String key){if(closed||!key.equals(active))return;try{if(out==null||bytes<32)throw new IOException();out.close();out=null;BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),b);if(!"image/png".equals(b.outMimeType)||b.outWidth<1||b.outHeight<1||b.outWidth>3072||b.outHeight>3072||(long)b.outWidth*b.outHeight>6000000)throw new IOException();if(!temp.renameTo(DiagramCache.file(context,active)))throw new IOException();temp=null;handler.post(()->finished(key,true));}catch(Exception e){abort();handler.post(()->finished(key,false));}}
        synchronized void abort(){if(out!=null)try{out.close();}catch(IOException ignored){}out=null;if(temp!=null)temp.delete();temp=null;}
    }
    private void destroyWeb(){loaded=false;if(web!=null){web.removeJavascriptInterface("NativeInline");web.stopLoading();host.removeView(web);web.destroy();web=null;}}
    @Override public void close(){closed=true;handler.removeCallbacksAndMessages(null);queue.clear();bridge.abort();destroyWeb();}
}
