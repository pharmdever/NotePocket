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
    private static final int MANIFEST_LIMIT=32*1024*1024;
    private static final String[] TABLES={"notes","docs","note_docs","page_notes","reading","page_ink","page_text","fonts","folders"};
    private Backup() {}
    public static void write(Context context,Store store,OutputStream stream) throws Exception {
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransactionNonExclusive();
        try(ZipOutputStream zip=new ZipOutputStream(stream)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));LimitedOutput metadata=new LimitedOutput(zip,MANIFEST_LIMIT);android.util.JsonWriter writer=new android.util.JsonWriter(new OutputStreamWriter(metadata,StandardCharsets.UTF_8));writer.beginObject();writer.name("format").value("NotePocket");writer.name("version").value(6);
            for(String table:TABLES) {writer.name(table).beginArray();int count=0;try(android.database.Cursor cursor=db.rawQuery("SELECT * FROM "+table,null)) {while(cursor.moveToNext()) {if(++count>10000) throw new IOException("Too many records");writer.beginObject();for(int i=0;i<cursor.getColumnCount();i++) {writer.name(cursor.getColumnName(i));if(cursor.isNull(i)) writer.nullValue();else if(cursor.getType(i)==android.database.Cursor.FIELD_TYPE_INTEGER) writer.value(cursor.getLong(i));else writer.value(cursor.getString(i));}writer.endObject();}}writer.endArray();}
            writer.endObject();writer.flush();zip.closeEntry();long total=metadata.count;
            for(String table:new String[]{"docs","fonts"}) for(ContentValues doc:store.query("SELECT * FROM "+table)) {
                String id=doc.getAsString("id");validId(id);
                File file=new File(context.getFilesDir(),id);
                if(!file.isFile()) throw new IOException("Document missing");
                zip.putNextEntry(new ZipEntry(table+"/"+id));
                try(InputStream in=new FileInputStream(file)) { total+=copy(in,zip,LIMIT-total); }
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
                    } else if(name.startsWith("docs/")||name.startsWith("fonts/")) {
                        String[] path=name.split("/",-1);if(path.length!=2) throw new IOException("Invalid archive path");String id=path[1];validId(id);
                        try(OutputStream out=new FileOutputStream(new File(stage,path[0]+"-"+id))) { total+=copy(zip,out,LIMIT-total); }
                    } else throw new IOException("Unexpected archive entry");
                    if(total>LIMIT) throw new IOException("Archive too large");zip.closeEntry();
                }
            }
            if(manifest==null||!manifest.optString("format").equals("NotePocket")||(manifest.optInt("version")!=2&&manifest.optInt("version")!=3&&manifest.optInt("version")!=4&&manifest.optInt("version")!=5&&manifest.optInt("version")!=6)) throw new IOException("Unsupported backup");
            for(String table:TABLES) if(rows(manifest,table).length()>10000) throw new IOException("Too many records");
            if(rows(manifest,"fonts").length()+store.query("SELECT id FROM fonts").size()>30) throw new IOException("Font library full");
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try {
                Map<Long,Long> notes=new HashMap<>();Map<String,String> docs=new HashMap<>();Map<String,String> fonts=new HashMap<>();
                JSONArray input=rows(manifest,"folders");for(int i=0;i<input.length();i++) store.createFolder(input.getJSONObject(i).getString("name"));
                input=manifest.getJSONArray("notes");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);long old=row.getLong("id");
                    if(notes.containsKey(old)) throw new IOException("Duplicate note");
                    ContentValues v=new ContentValues();
                    for(String key:new String[]{"title","body","rich","folder"}) v.put(key,row.getString(key));
                    v.put("soap",new SoapDraft(row.optString("soap","{}")).json());store.createFolder(row.getString("folder"));
                    v.put("tables",NoteTable.encode(NoteTable.decode(row.optString("tables","[]"))));
                    v.put("updated",row.getLong("updated"));v.put("pinned",row.getInt("pinned")==0?0:1);v.put("deleted",row.getInt("deleted")==0?0:1);
                    notes.put(old,db.insertOrThrow("notes",null,v));
                }
                input=manifest.getJSONArray("docs");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);String old=row.getString("id");validId(old);
                    if(docs.containsKey(old)) throw new IOException("Duplicate document");
                    File source=new File(stage,"docs-"+old);if(!source.isFile()||source.length()!=row.getLong("size")) throw new IOException("Document incomplete");
                    String id=UUID.randomUUID().toString();File dest=new File(context.getFilesDir(),id);created.add(dest);
                    try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest)) { copy(in,out,LIMIT); }
                    ContentValues v=new ContentValues();v.put("id",id);v.put("name",row.getString("name"));v.put("mime",row.getString("mime"));v.put("size",dest.length());db.insertOrThrow("docs",null,v);docs.put(old,id);
                }
                input=rows(manifest,"fonts");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);String old=row.getString("id");validId(old);if(fonts.containsKey(old)) throw new IOException("Duplicate font");
                    File source=new File(stage,"fonts-"+old);if(!source.isFile()||source.length()!=row.getLong("size")||source.length()>20L*1024*1024) throw new IOException("Invalid font");
                    String id="font-"+UUID.randomUUID();File dest=new File(context.getFilesDir(),id);created.add(dest);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(dest)) {copy(in,out,20L*1024*1024);}
                    android.graphics.Typeface.createFromFile(dest);ContentValues v=new ContentValues();v.put("id",id);v.put("name",row.getString("name"));v.put("size",dest.length());db.insertOrThrow("fonts",null,v);fonts.put(old,id);
                }
                input=manifest.getJSONArray("note_docs");
                for(int i=0;i<input.length();i++) {
                    JSONObject row=input.getJSONObject(i);Long note=notes.get(row.getLong("note_id"));String doc=docs.get(row.getString("doc_id"));
                    if(note==null||doc==null) throw new IOException("Broken attachment");
                    ContentValues v=new ContentValues();v.put("note_id",note);v.put("doc_id",doc);db.insertOrThrow("note_docs",null,v);
                }
                for(String table:new String[]{"page_notes","reading","page_ink","page_text"}) {
                    input=rows(manifest,table);
                    for(int i=0;i<input.length();i++) {
                        JSONObject row=input.getJSONObject(i);String doc=docs.get(row.getString("doc_id"));int page=row.getInt("page");
                        if(doc==null||page<0) throw new IOException("Broken page record");
                        ContentValues v=new ContentValues();v.put("doc_id",doc);v.put("page",page);
                        if(table.equals("page_notes")) {v.put("body",row.getString("body"));v.put("bookmarked",row.getInt("bookmarked")==0?0:1);}
                        if(table.equals("page_ink")) v.put("drawing",Ink.encode(Ink.decode(row.getString("drawing"))));
                        if(table.equals("page_text")) {
                            List<TextBox> boxes=TextBox.decode(row.getString("boxes"));List<TextBox> mapped=new ArrayList<>();
                            for(TextBox box:boxes) {String font=box.font;if(!Arrays.asList("sans","serif","mono").contains(font)) {font=fonts.get(font);if(font==null) throw new IOException("Text font missing");}mapped.add(new TextBox(box.id,box.text,font,box.x,box.y,box.width,box.size,box.color));}
                            v.put("boxes",TextBox.encode(mapped));
                        }
                        db.insertOrThrow(table,null,v);
                    }
                }
                db.setTransactionSuccessful();created.clear();return notes.size();
            } finally { db.endTransaction(); }
        } finally { for(File file:created) file.delete();deleteTree(stage); }
    }
    private static final class LimitedOutput extends OutputStream {
        final OutputStream target;final long limit;long count;LimitedOutput(OutputStream target,long limit) {this.target=target;this.limit=limit;}
        public void write(int value) throws IOException {if(++count>limit) throw new IOException("Backup metadata too large");target.write(value);}
        public void write(byte[] bytes,int offset,int length) throws IOException {if(count+length>limit) throw new IOException("Backup metadata too large");target.write(bytes,offset,length);count+=length;}
    }
    private static JSONArray rows(JSONObject manifest,String table) throws JSONException {
        if(!manifest.has(table)&&((table.equals("folders")&&manifest.getInt("version")<6)||(table.equals("page_ink")&&manifest.getInt("version")==2)||((table.equals("page_text")||table.equals("fonts"))&&manifest.getInt("version")<4))) return new JSONArray();
        return manifest.getJSONArray(table);
    }
    private static void validId(String id) throws IOException { if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")) throw new IOException("Invalid document ID"); }
    static long copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buffer=new byte[65536];long total=0;int n;
        while((n=in.read(buffer))!=-1) { total+=n;if(total>limit) throw new IOException("File too large");out.write(buffer,0,n); }
        return total;
    }
    private static void deleteTree(File file) { File[] children=file.listFiles();if(children!=null) for(File child:children) deleteTree(child);file.delete(); }
}
