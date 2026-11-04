package com.notepocket.app;

import android.content.*;
import android.graphics.Typeface;
import android.util.LruCache;
import java.io.*;
import java.util.*;

public final class Fonts {
    private static final LruCache<String,Typeface> CACHE=new LruCache<>(12);
    private Fonts() {}
    public static Typeface typeface(Context context,String id) {
        if("serif".equals(id)) return Typeface.SERIF;if("mono".equals(id)) return Typeface.MONOSPACE;if("sans".equals(id)) return Typeface.DEFAULT;
        synchronized(CACHE) {Typeface font=CACHE.get(id);if(font!=null) return font;try {font=Typeface.createFromFile(new File(context.getFilesDir(),id));CACHE.put(id,font);return font;}catch(RuntimeException error) {return Typeface.DEFAULT;}}
    }
    public static String importFont(Context context,Store store,InputStream source,String name) throws Exception {
        if(store.query("SELECT id FROM fonts").size()>=30) throw new IOException("Font library full");
        if(!name.toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf)$")) throw new IOException("Use TTF or OTF");
        String id="font-"+UUID.randomUUID();File file=new File(context.getFilesDir(),id);
        try {
            try(OutputStream output=new FileOutputStream(file)) {Backup.copy(source,output,20L*1024*1024);}
            try(DataInputStream input=new DataInputStream(new FileInputStream(file))) {int magic=input.readInt();if(magic!=0x00010000&&magic!=0x4f54544f&&magic!=0x74727565) throw new IOException("Unsupported font");}
            Typeface font=Typeface.createFromFile(file);synchronized(CACHE) {CACHE.put(id,font);}
            ContentValues row=new ContentValues();row.put("id",id);row.put("name",name);row.put("size",file.length());store.getWritableDatabase().insertOrThrow("fonts",null,row);return id;
        } catch(Exception error) {file.delete();throw error;}
    }
    public static void remove(Context context,Store store,String id) {
        for(ContentValues row:store.query("SELECT * FROM page_text")) {
            List<TextBox> boxes=TextBox.decode(row.getAsString("boxes"));boolean changed=false;
            for(int i=0;i<boxes.size();i++) {TextBox box=boxes.get(i);if(box.font.equals(id)) {boxes.set(i,new TextBox(box.id,box.text,"sans",box.x,box.y,box.width,box.size,box.color));changed=true;}}
            if(changed) store.saveText(row.getAsString("doc_id"),row.getAsInteger("page"),TextBox.encode(boxes));
        }
        store.getWritableDatabase().delete("fonts","id=?",new String[]{id});new File(context.getFilesDir(),id).delete();synchronized(CACHE) {CACHE.remove(id);}
        SharedPreferences prefs=context.getSharedPreferences("reader",0);if(prefs.getString("font","sans").equals(id)) prefs.edit().putString("font","sans").apply();
    }
}
