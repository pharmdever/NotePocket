package com.notepocket.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.CancellationSignal;
import java.util.*;

/** Keyset pagination with small projections. Never fetches full notes, rich text or tables for a card. */
public final class LibraryPage {
    public static final int SIZE=50;
    private static final java.util.regex.Pattern INLINE_PREVIEW=java.util.regex.Pattern.compile("(?m)^ {0,3}!?\\[(ตาราง|รูปภาพ)\\]\\(notepocket-(?:table|image):[^\\r\\n]*");
    static String preview(String text){return INLINE_PREVIEW.matcher(text).replaceAll("$1");}
    public final List<ContentValues> rows;public final boolean more;public final long total;
    private LibraryPage(List<ContentValues> rows,boolean more,long total) {this.rows=rows;this.more=more;this.total=total;}
    public static LibraryPage read(Store store,boolean files,String filter,String query,ContentValues after,CancellationSignal cancel) {
        List<String> args=new ArrayList<>();String where=files?"1=1":filter.equals("trash")?"n.deleted=1":"n.deleted=0";
        if(!files&&filter.equals("pinned")) where+=" AND n.pinned=1";
        if(!files&&!Arrays.asList("all","trash","pinned").contains(filter)) {where+=" AND n.folder=?";args.add(filter.startsWith("folder:")?filter.substring(7):filter);}
        long total=-1;if(query.isEmpty()&&after==null) {try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+(files?"docs":"notes n")+" WHERE "+where,args.toArray(new String[0]),cancel)) {cursor.moveToFirst();total=cursor.getLong(0);}}
        if(!query.isEmpty()) {String like=Store.like(query);if(files) {where+=" AND name LIKE ? ESCAPE '\\'";args.add(like);}else {where+=" AND (n.title LIKE ? ESCAPE '\\' OR n.body LIKE ? ESCAPE '\\' OR n.tables LIKE ? ESCAPE '\\')";args.add(like);args.add(like);args.add(like);}}
        if(after!=null) {if(files) {where+=" AND rowid<?";args.add(after.getAsString("_rowid"));}else {where+=" AND (n.pinned<? OR (n.pinned=? AND n.updated<?) OR (n.pinned=? AND n.updated=? AND n.id<?))";String pin=after.getAsString("pinned"),updated=after.getAsString("updated");Collections.addAll(args,pin,pin,updated,pin,updated,after.getAsString("id"));}}
        String sql=files?"SELECT rowid AS _rowid,id,substr(name,1,240) AS name,mime,size FROM docs WHERE "+where+" ORDER BY rowid DESC LIMIT 51":"SELECT n.id,substr(n.title,1,240) AS title,substr(n.body,1,240) AS preview,n.folder,n.pinned,n.deleted,n.updated,(SELECT COUNT(*) FROM note_docs a WHERE a.note_id=n.id) AS attachments FROM notes n WHERE "+where+" ORDER BY n.pinned DESC,n.updated DESC,n.id DESC LIMIT 51";
        List<ContentValues> rows=new ArrayList<>();try(Cursor c=store.getReadableDatabase().rawQuery(sql,args.toArray(new String[0]),cancel)) {while(c.moveToNext()) {ContentValues row=new ContentValues();for(int i=0;i<c.getColumnCount();i++) {if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER) row.put(c.getColumnName(i),c.getLong(i));else row.put(c.getColumnName(i),c.getString(i));}if(!files)row.put("preview",preview(row.getAsString("preview")));rows.add(row);}}
        boolean more=rows.size()>SIZE;if(more) rows.remove(rows.size()-1);return new LibraryPage(rows,more,total);
    }
}
