package com.notepocket.app;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable archive: JSON plus document copies. Restore adds data and never replaces existing notes. */
public final class Backup {
    private static final long LIMIT=1024L*1024*1024;
    private static final int MANIFEST_LIMIT=8*1024*1024;
    private static final String[] TABLES={"notes","docs","note_docs","page_notes","reading"};
    private Backup() {}
    public static void write(Context context,Store store,OutputStream stream) throws Exception {
        JSONObject manifest=new JSONObject();manifest.put("format","NotePocket");manifest.put("version",2);
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try(ZipOutputStream zip=new ZipOutputStream(stream)) {
            for(String table:TABLES) {
                JSONArray rows=new JSONArray();
                for(ContentValues row:store.query("SELECT * FROM "+table)) {
                    JSONObject object=new JSONObject();
                    for(String key:row.keySet()) object.put(key,row.get(key));
                    rows.put(object);
                }
                manifest.put(table,rows);
            }
            byte[] json=manifest.toString().getBytes(StandardCharsets.UTF_8);
            if(json.length>MANIFEST_LIMIT) throw new IOException("Backup metadata too large");
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(json);zip.closeEntry();
            for(ContentValues doc:store.query("SELECT * FROM docs")) {
                String id=doc.getAsString("id");validId(id);
                File file=new File(context.getFilesDir(),id);
                if(!file.isFile()) throw new IOException("Document missing");
                zip.putNextEntry(new ZipEntry("docs/"+id));
                try(InputStream in=new FileInputStream(file)) { copy(in,zip,LIMIT); }
                zip.closeEntry();
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static int restore(Context context,Store store,InputStream stream) throws Exception {
        File stage=new File(context.getCacheDir(),"restore-"+UUID.randomUUID());
        if(!stage.mkdir()) throw new IOException("No staging space");
        List<File> created=new ArrayList<>();
        try {
            JSONObject manifest=null;Set<String> seen=new HashSet<>();long total=0;int count=0;
            try(ZipInputStream zip=new ZipInputStream(stream)) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    String name=entry.getName();
                    if(++count>10000||!seen.add(name)) throw new IOException("Invalid archive entries");
                    if(name.equals("manifest.json")) {
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();total+=copy(zip,bytes,MANIFEST_LIMIT);
                        manifest=new JSONObject(bytes.toString("UTF-8"));
                    } else if(name.startsWith("docs/")) {
                        String id=name.substring(5);validId(id);
                        try(OutputStream out=new FileOutputStream(new File(stage,id))) { total+=copy(zip,out,LIMIT-total); }
                    } else throw new IOException("Unexpected archive entry");
                    if(total>LIMIT) throw new IOException("Archive too large");zip.closeEntry();
                }
            }
            if(manifest==null||!manifest.optString("format").equals("NotePocket")||manifest.optInt("version")!=2) throw new IOException("Unsupported backup");
            for(String table:TABLES) if(manifest.getJSONArray(table).length()>10000) throw new IOException("Too many records");
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try {
                Map<Long,Long> notes=new HashMap<>();Map<String,String> docs=new HashMap<>();
                JSONArray input=manifest.getJSONArray("notes");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);long old=row.getLong("id");
                    if(notes.containsKey(old)) throw new IOException("Duplicate note");
                    ContentValues v=new ContentValues();
                    for(String key:new String[]{"title","body","rich","folder"}) v.put(key,row.getString(key));
                    v.put("updated",row.getLong("updated"));v.put("pinned",row.getInt("pinned")==0?0:1);v.put("deleted",row.getInt("deleted")==0?0:1);
                    notes.put(old,db.insertOrThrow("notes",null,v));
                }
                input=manifest.getJSONArray("docs");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);String old=row.getString("id");validId(old);
                    if(docs.containsKey(old)) throw new IOException("Duplicate document");
                    File source=new File(stage,old);if(!source.isFile()||source.length()!=row.getLong("size")) throw new IOException("Document incomplete");
                    String id=UUID.randomUUID().toString();File dest=new File(context.getFilesDir(),id);created.add(dest);
                    try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest)) { copy(in,out,LIMIT); }
                    ContentValues v=new ContentValues();v.put("id",id);v.put("name",row.getString("name"));v.put("mime",row.getString("mime"));v.put("size",dest.length());db.insertOrThrow("docs",null,v);docs.put(old,id);
                }
                input=manifest.getJSONArray("note_docs");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);Long note=notes.get(row.getLong("note_id"));String doc=docs.get(row.getString("doc_id"));
                    if(note==null||doc==null) throw new IOException("Broken attachment");
                    ContentValues v=new ContentValues();v.put("note_id",note);v.put("doc_id",doc);db.insertOrThrow("note_docs",null,v);
                }
                for(String table:new String[]{"page_notes","reading"}) {
                    input=manifest.getJSONArray(table);
                    for(int i=0;i<input.length();i++) {
                        JSONObject row=input.getJSONObject(i);String doc=docs.get(row.getString("doc_id"));int page=row.getInt("page");
                        if(doc==null||page<0) throw new IOException("Broken page record");
                        ContentValues v=new ContentValues();v.put("doc_id",doc);v.put("page",page);
                        if(table.equals("page_notes")) {v.put("body",row.getString("body"));v.put("bookmarked",row.getInt("bookmarked")==0?0:1);}
                        db.insertOrThrow(table,null,v);
                    }
                }
                db.setTransactionSuccessful();created.clear();return notes.size();
            } finally { db.endTransaction(); }
        } finally { for(File file:created) file.delete();deleteTree(stage); }
    }
    private static void validId(String id) throws IOException { if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")) throw new IOException("Invalid document ID"); }
    static long copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buffer=new byte[65536];long total=0;int n;
        while((n=in.read(buffer))!=-1) { total+=n;if(total>limit) throw new IOException("File too large");out.write(buffer,0,n); }
        return total;
    }
    private static void deleteTree(File file) { File[] children=file.listFiles();if(children!=null) for(File child:children) deleteTree(child);file.delete(); }
}
