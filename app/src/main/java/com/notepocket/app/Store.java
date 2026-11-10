package com.notepocket.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Additive, transactional schema migration; never drops user data. */
public final class Store extends SQLiteOpenHelper {
    public Store(Context context) { this(context, "notepocket.db"); }
    Store(Context context, String name) { super(context,name,null,8);setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) { migrate(db); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { migrate(db); }
    private void migrate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS docs (id TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS note_docs (note_id INTEGER NOT NULL, doc_id TEXT NOT NULL, PRIMARY KEY(note_id,doc_id))");
        addColumn(db,"notes","soap","TEXT NOT NULL DEFAULT '{}'");
        addColumn(db,"notes","tables","TEXT NOT NULL DEFAULT '[]'");
        addColumn(db,"notes","rich","TEXT NOT NULL DEFAULT ''");
        addColumn(db,"notes","folder","TEXT NOT NULL DEFAULT 'ทั่วไป'");
        addColumn(db,"notes","pinned","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"notes","deleted","INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE IF NOT EXISTS page_notes (doc_id TEXT NOT NULL, page INTEGER NOT NULL, body TEXT NOT NULL DEFAULT '', bookmarked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(doc_id,page))");
        db.execSQL("CREATE TABLE IF NOT EXISTS reading (doc_id TEXT PRIMARY KEY, page INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS page_ink (doc_id TEXT NOT NULL, page INTEGER NOT NULL, drawing TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(doc_id,page))");
        db.execSQL("CREATE TABLE IF NOT EXISTS page_text (doc_id TEXT NOT NULL, page INTEGER NOT NULL, boxes TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(doc_id,page))");
        db.execSQL("CREATE TABLE IF NOT EXISTS fonts (id TEXT PRIMARY KEY, name TEXT NOT NULL, size INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS folders(name TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO folders(name) VALUES('ทั่วไป')");db.execSQL("INSERT OR IGNORE INTO folders(name) SELECT DISTINCT folder FROM notes");
        addColumn(db,"notes","sync_id","TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS notes_sync_id ON notes(sync_id) WHERE sync_id!=''");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_config(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_ops(rev TEXT PRIMARY KEY,entity TEXT NOT NULL,kind TEXT NOT NULL,json TEXT NOT NULL,label TEXT NOT NULL,time INTEGER NOT NULL,deleted INTEGER NOT NULL,uploaded INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_entity ON sync_ops(entity)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_parents(rev TEXT NOT NULL,parent TEXT NOT NULL,PRIMARY KEY(rev,parent))");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_parent ON sync_parents(parent)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_bindings(entity TEXT PRIMARY KEY,kind TEXT NOT NULL,local_key TEXT NOT NULL,fingerprint TEXT NOT NULL,head TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_seen(file_id TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_blob_local(id TEXT PRIMARY KEY,sha TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL)");
        db.execSQL("DROP INDEX IF EXISTS notes_order");
        db.execSQL("CREATE INDEX IF NOT EXISTS notes_page ON notes(deleted,pinned DESC,updated DESC,id DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS notes_folder_page ON notes(folder,deleted,pinned DESC,updated DESC,id DESC)");
    }
    private void addColumn(SQLiteDatabase db,String table,String name,String type) {
        try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)) {
            while(c.moveToNext()) if(c.getString(1).equals(name)) return;
        }
        db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+name+" "+type);
    }
    public List<ContentValues> query(String sql,String...args) {
        List<ContentValues> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)) {
            while(c.moveToNext()) {
                ContentValues v=new ContentValues();
                for(int i=0;i<c.getColumnCount();i++) {
                    if(c.isNull(i)) v.putNull(c.getColumnName(i));
                    else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER) v.put(c.getColumnName(i),c.getLong(i));
                    else v.put(c.getColumnName(i),c.getString(i));
                }
                rows.add(v);
            }
        }
        return rows;
    }
    public ContentValues note(long id) {
        List<ContentValues> rows=query("SELECT * FROM notes WHERE id=?",""+id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private long saveRecord(long id,ContentValues values,String folder) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransactionNonExclusive();try {String previous="";if(id>=0){try(Cursor c=db.rawQuery("SELECT body FROM notes WHERE id=?",new String[]{""+id})){if(c.moveToFirst())previous=c.getString(0);}}createFolder(folder);values.put("folder",folder);values.put("updated",System.currentTimeMillis());if(id<0) id=db.insertOrThrow("notes",null,values);else if(db.update("notes",values,"id=?",new String[]{""+id})!=1) throw new IllegalStateException("Note missing");InlineImages.reconcile(db,id,previous,values.getAsString("body"));db.setTransactionSuccessful();return id;}finally {db.endTransaction();}
    }
    private ContentValues values(String title,String body,String rich) {ContentValues v=new ContentValues();v.put("title",title.trim().isEmpty()?"ไม่มีชื่อ":title.trim());v.put("body",body);v.put("rich",rich);return v;}
    public long save(long id,String title,String body,String rich,String folder) {return saveRecord(id,values(title,body,rich),folder);}
    public long save(long id,String title,String body,String rich,String folder,String tables) {ContentValues v=values(title,body,rich);v.put("tables",NoteTable.encode(NoteTable.decode(tables)));return saveRecord(id,v,folder);}
    public long save(long id,String title,String body,String rich,String folder,String tables,String soap) {ContentValues v=values(title,body,rich);v.put("tables",NoteTable.encode(NoteTable.decode(tables)));v.put("soap",new SoapDraft(soap).json());return saveRecord(id,v,folder);}
    long saveSnapshot(long id,NoteSnapshot snapshot) {ContentValues v=values(snapshot.title,snapshot.body.toString(),NoteInk.rich(NoteFormat.encode(snapshot.body),NoteInk.parse(snapshot.ink)));v.put("tables",NoteTable.encode(snapshot.tables));v.put("soap",snapshot.soap);return saveRecord(id,v,snapshot.folder);}
    public void createFolder(String name) {if(name==null||name.trim().isEmpty()||name.length()>80) throw new IllegalArgumentException("Invalid folder");ContentValues v=new ContentValues();v.put("name",name.trim());getWritableDatabase().insertWithOnConflict("folders",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public List<ContentValues> folders() {return query("SELECT f.name,COUNT(n.id) AS count FROM folders f LEFT JOIN notes n ON n.folder=f.name AND n.deleted=0 GROUP BY f.name ORDER BY f.name COLLATE NOCASE");}
    public void renameFolder(String old,String name) {if(old.equals("ทั่วไป")||name==null||name.trim().isEmpty()||name.trim().length()>80) throw new IllegalArgumentException("Invalid folder");name=name.trim();if(old.equals(name)) return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {if(!query("SELECT name FROM folders WHERE name=?",name).isEmpty()) throw new IllegalArgumentException("Folder exists");ContentValues v=new ContentValues();v.put("name",name);if(db.update("folders",v,"name=?",new String[]{old})!=1) throw new IllegalArgumentException("Missing folder");v.clear();v.put("folder",name);db.update("notes",v,"folder=?",new String[]{old});db.setTransactionSuccessful();}finally {db.endTransaction();}}
    public void deleteFolder(String name) {if(name.equals("ทั่วไป")) throw new IllegalArgumentException();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {createFolder("ทั่วไป");ContentValues v=new ContentValues();v.put("folder","ทั่วไป");db.update("notes",v,"folder=?",new String[]{name});db.delete("folders","name=?",new String[]{name});db.setTransactionSuccessful();}finally {db.endTransaction();}}
    public void flag(long id,String column,int value) {
        if(!column.equals("deleted")&&!column.equals("pinned")) throw new IllegalArgumentException();
        ContentValues v=new ContentValues(); v.put(column,value); getWritableDatabase().update("notes",v,"id=?",new String[]{""+id});
    }
    public void attach(long note,String doc) {
        if(note(note)==null || query("SELECT id FROM docs WHERE id=?",doc).isEmpty()) throw new IllegalArgumentException("Attachment missing");
        ContentValues v=new ContentValues();v.put("note_id",note);v.put("doc_id",doc);getWritableDatabase().insertWithOnConflict("note_docs",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<ContentValues> attachments(long note) { return query("SELECT d.* FROM docs d JOIN note_docs n ON d.id=n.doc_id WHERE n.note_id=? ORDER BY d.rowid",""+note); }
    public void detach(long note,String doc) { getWritableDatabase().delete("note_docs","note_id=? AND doc_id=?",new String[]{""+note,doc}); }
    public void eraseNote(long id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { db.delete("note_docs","note_id=?",new String[]{""+id});db.delete("notes","id=?",new String[]{""+id});db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    public void eraseDoc(String id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            for(ContentValues note:query("SELECT id,body,rich FROM notes WHERE instr(body,?)>0", "](notepocket-image:"+id+")")){String body=note.getAsString("body");if(!InlineImages.ids(body).contains(id))continue;InlineImages.Mapped mapped=InlineImages.remove(body,note.getAsString("rich"),id);ContentValues v=new ContentValues();v.put("body",mapped.body);v.put("rich",mapped.rich);v.put("updated",System.currentTimeMillis());db.update("notes",v,"id=?",new String[]{note.getAsString("id")});}
            db.delete("note_docs","doc_id=?",new String[]{id});db.delete("page_notes","doc_id=?",new String[]{id});
            db.delete("page_text","doc_id=?",new String[]{id});db.delete("page_ink","doc_id=?",new String[]{id});db.delete("reading","doc_id=?",new String[]{id});db.delete("docs","id=?",new String[]{id});db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public ContentValues page(String doc,int page) {
        List<ContentValues> rows=query("SELECT * FROM page_notes WHERE doc_id=? AND page=?",doc,""+page);
        if(!rows.isEmpty()) return rows.get(0);
        ContentValues v=new ContentValues(); v.put("doc_id",doc);v.put("page",page);v.put("body","");v.put("bookmarked",0);return v;
    }
    public void savePage(ContentValues v) { getWritableDatabase().insertWithOnConflict("page_notes",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    public void remember(String doc,int page) { ContentValues v=new ContentValues();v.put("doc_id",doc);v.put("page",page);getWritableDatabase().insertWithOnConflict("reading",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    public int lastPage(String doc) { List<ContentValues> r=query("SELECT page FROM reading WHERE doc_id=?",doc);return r.isEmpty()?0:r.get(0).getAsInteger("page"); }
    public String drawing(String doc,int page) {
        List<ContentValues> rows=query("SELECT drawing FROM page_ink WHERE doc_id=? AND page=?",doc,""+page);
        return rows.isEmpty()?"[]":rows.get(0).getAsString("drawing");
    }
    public void saveDrawing(String doc,int page,String drawing) {
        String validated=Ink.encode(Ink.decode(drawing));
        ContentValues v=new ContentValues();v.put("doc_id",doc);v.put("page",page);v.put("drawing",validated);
        if(getWritableDatabase().insertWithOnConflict("page_ink",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("Drawing could not be saved");
    }
    public String textBoxes(String doc,int page) {List<ContentValues> rows=query("SELECT boxes FROM page_text WHERE doc_id=? AND page=?",doc,""+page);return rows.isEmpty()?"[]":rows.get(0).getAsString("boxes");}
    public void saveText(String doc,int page,String boxes) {ContentValues v=new ContentValues();v.put("doc_id",doc);v.put("page",page);v.put("boxes",TextBox.encode(TextBox.decode(boxes)));if(getWritableDatabase().insertWithOnConflict("page_text",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0) throw new IllegalStateException("Text could not be saved");}
    public static String like(String q) { return "%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
}
