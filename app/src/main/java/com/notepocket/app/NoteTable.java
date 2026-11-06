package com.notepocket.app;

import org.json.*;
import java.util.*;

/** Real editable note grids; stored separately from prose and included in portable backups. */
public final class NoteTable {
    public static final int MAX_TABLES=10,MAX_ROWS=30,MAX_COLS=8;
    public final String id;
    public final List<Integer> widths=new ArrayList<>();
    public final List<List<String>> cells=new ArrayList<>();
    public NoteTable(int rows,int cols) {id=UUID.randomUUID().toString();if(rows<1||rows>MAX_ROWS||cols<1||cols>MAX_COLS) throw new IllegalArgumentException();for(int c=0;c<cols;c++) widths.add(150);for(int r=0;r<rows;r++) {List<String> line=new ArrayList<>();for(int c=0;c<cols;c++) line.add("");cells.add(line);}}
    private NoteTable(String id) {this.id=id;}
    public int columns() {return cells.get(0).size();}
    public int width(int column) {return column<widths.size()?widths.get(column):150;}
    public void insertRow(int at) {if(cells.size()>=MAX_ROWS||at<0||at>cells.size()) throw new IllegalArgumentException();cells.add(at,new ArrayList<>(Collections.nCopies(columns(),"")));}
    public void insertColumn(int at) {if(columns()>=MAX_COLS||at<0||at>columns()) throw new IllegalArgumentException();while(widths.size()<columns()) widths.add(150);for(List<String> row:cells) row.add(at,"");widths.add(at,150);}
    public void removeColumn(int at) {for(List<String> row:cells) row.remove(at);if(at<widths.size()) widths.remove(at);}
    public void paste(List<List<String>> input,int row,int col) {int rows=input.size(),cols=input.get(0).size();if(row<0||col<0||row+rows>MAX_ROWS||col+cols>MAX_COLS) throw new IllegalArgumentException("Paste exceeds limits");while(columns()<col+cols) insertColumn(columns());while(cells.size()<row+rows) insertRow(cells.size());for(int r=0;r<rows;r++) for(int c=0;c<cols;c++) cells.get(row+r).set(col+c,input.get(r).get(c));}
    public static String encode(List<NoteTable> tables) {
        try {JSONArray out=new JSONArray();for(NoteTable table:tables) {JSONArray rows=new JSONArray();for(List<String> row:table.cells) rows.put(new JSONArray(row));out.put(new JSONObject().put("id",table.id).put("cells",rows).put("widths",new JSONArray(columnWidths(table))));}return out.toString();}catch(JSONException e) {throw new IllegalArgumentException(e);}
    }
    public static List<NoteTable> decode(String value) {
        try {JSONArray in=new JSONArray(value==null?"[]":value);if(in.length()>MAX_TABLES) throw new JSONException("Too many tables");List<NoteTable> result=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(int i=0;i<in.length();i++) {JSONObject object=in.getJSONObject(i);String id=object.getString("id");if(!id.matches("[A-Za-z0-9-]{1,80}")||!ids.add(id)) throw new JSONException("Invalid table id");NoteTable table=new NoteTable(id);JSONArray rows=object.getJSONArray("cells");if(rows.length()<1||rows.length()>MAX_ROWS) throw new JSONException("Invalid rows");int cols=-1;
                for(int r=0;r<rows.length();r++) {JSONArray line=rows.getJSONArray(r);if(cols<0) cols=line.length();if(cols<1||cols>MAX_COLS||line.length()!=cols) throw new JSONException("Invalid columns");List<String> row=new ArrayList<>();for(int c=0;c<cols;c++) {String text=line.getString(c);if(text.length()>4000) throw new JSONException("Cell too long");row.add(text);}table.cells.add(row);}JSONArray widths=object.optJSONArray("widths");for(int c=0;c<cols;c++) {int width=widths==null?150:widths.getInt(c);if(width<80||width>360) throw new JSONException("Invalid column width");table.widths.add(width);}result.add(table);
            }return result;
        }catch(JSONException e) {throw new IllegalArgumentException("Invalid note tables",e);}
    }
    private static List<Integer> columnWidths(NoteTable table) {List<Integer> out=new ArrayList<>();for(int c=0;c<table.columns();c++) out.add(table.width(c));return out;}
    public static String plain(List<NoteTable> tables) {StringBuilder out=new StringBuilder();for(NoteTable table:tables) {out.append("\n\n");for(List<String> row:table.cells) {for(int c=0;c<row.size();c++) {if(c>0) out.append("\t");out.append(row.get(c));}out.append('\n');}}return out.toString();}
}
