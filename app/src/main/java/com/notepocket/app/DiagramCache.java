package com.notepocket.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Derived images are disposable. Original Mermaid stays in the note and is always recoverable. */
final class DiagramCache {
    static String key(String code) {try {byte[] hash=MessageDigest.getInstance("SHA-256").digest(("mermaid-10.9.3-inline-1\n"+code).getBytes(StandardCharsets.UTF_8));char[] out=new char[hash.length*2];String digits="0123456789abcdef";for(int i=0;i<hash.length;i++){int b=hash[i]&255;out[i*2]=digits.charAt(b>>>4);out[i*2+1]=digits.charAt(b&15);}return new String(out);}catch(Exception e){throw new IllegalStateException(e);}}
    static File file(Context context,String key) {if(!key.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();File dir=new File(context.getCacheDir(),"inline-diagrams");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Cache unavailable");return new File(dir,key+".png");}
    static Bitmap decode(File file,int width) {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);if(!"image/png".equals(o.outMimeType)||o.outWidth<1||o.outHeight<1||(long)o.outWidth*o.outHeight>6000000)return null;o.inJustDecodeBounds=false;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>Math.max(320,width)*2||(long)(o.outWidth/o.inSampleSize)*(o.outHeight/o.inSampleSize)>1500000)o.inSampleSize*=2;return BitmapFactory.decodeFile(file.getPath(),o);}
    static void prune(Context context) {File dir=new File(context.getCacheDir(),"inline-diagrams");File[] files=dir.listFiles((d,name)->name.endsWith(".png"));if(files==null)return;java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));long bytes=0;for(File f:files)bytes+=f.length();int count=files.length;for(File f:files) {if(bytes<=64L*1024*1024&&count<=200)break;long length=f.length();if(f.delete()){bytes-=length;count--;}}}
    private DiagramCache(){}
}
