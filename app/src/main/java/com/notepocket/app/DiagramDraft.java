package com.notepocket.app;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Private bounded drafts avoid putting note/code bodies into Binder or saved-state bundles. */
final class DiagramDraft {
    static File file(Context c,String token) {if(token==null||!token.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();File dir=new File(c.getCacheDir(),"diagrams");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Draft directory unavailable");return new File(dir,token+".json");}
    static JSONObject read(Context c,String token) throws Exception {
        File source=file(c,token);if(source.length()>1024*1024)throw new IOException("Draft too large");byte[] bytes=new AtomicFile(source).readFully();if(bytes.length>1024*1024)throw new IOException("Draft too large");return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
    }
    static void write(Context c,String token,JSONObject value) throws Exception {
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IOException("Draft too large");AtomicFile f=new AtomicFile(file(c,token));FileOutputStream out=f.startWrite();try {out.write(bytes);f.finishWrite(out);}catch(Exception e) {f.failWrite(out);throw e;}
    }
    static void cleanOld(Context c) {File dir=new File(c.getCacheDir(),"diagrams");File[] files=dir.listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-7L*24*3600*1000;for(File file:files)if(file.lastModified()<cutoff)file.delete();}
}
