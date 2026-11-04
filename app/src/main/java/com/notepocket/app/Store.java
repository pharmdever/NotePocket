package com.notepocket.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/** Additive, transactional schema migration; never drops user data. */
public final class Store extends SQLiteOpenHelper {
    public Store(Context context) { this(context, "notepocket.db"); }
    Store(Context context, String name) { super(context,name,null,2); }
    @Override public void onCreate(SQLiteDatabase db) { migrate(db); }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { migrate(db); }
    private void migrate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS docs (id TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS note_docs (note_id INTEGER NOT NULL, doc_id TEXT NOT NULL, PRIMARY KEY(note_id,doc_id))");
        addColumn(db,"notes","rich","TEXT NOT NULL DEFAULT ''");
        addColumn(db,"notes","folder","TEXT NOT NULL DEFAULT 'ทั่วไป'");
        addColumn(db,"notes","pinned","INTEGER NOT NULL DEFAULT 0");
        addColumn(db,"notes","deleted","INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE IF NOT EXISTS page_notes (doc_id TEXT NOT NULL, page INTEGER NOT NULL, body TEXT NOT NULL DEFAULT '', bookmarked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(doc_id,page))");
        db.execSQL("CREATE TABLE IF NOT EXISTS reading (doc_id TEXT PRIMARY KEY, page INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS notes_order ON notes(deleted,pinned,updated)");
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
    public long save(long id,String title,String body,String rich,String folder) {
        ContentValues v=new ContentValues(); v.put("title",title.trim().isEmpty()?"ไม่มีชื่อ":title.trim());
        v.put("body",body); v.put("rich",rich); v.put("folder",folder); v.put("updated",System.currentTimeMillis());
        if(id<0) return getWritableDatabase().insertOrThrow("notes",null,v);
        if(getWritableDatabase().update("notes",v,"id=?",new String[]{""+id})!=1) throw new IllegalStateException("Note missing");
        return id;
    }
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
            db.delete("note_docs","doc_id=?",new String[]{id});db.delete("page_notes","doc_id=?",new String[]{id});
            db.delete("reading","doc_id=?",new String[]{id});db.delete("docs","id=?",new String[]{id});db.setTransactionSuccessful();
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
    public static String like(String q) { return "%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
}
