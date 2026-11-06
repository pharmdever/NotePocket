package com.notepocket.app;

import java.util.*;

/** Excel/Sheets clipboard TSV and RFC 4180 CSV, including quoted multiline cells. */
public final class TableTransfer {
    private TableTransfer() {}
    public static List<List<String>> parse(String value) {return parse(value,false);}
    public static List<List<String>> parseClipboard(String value) {return parse(value,true);}
    private static List<List<String>> parse(String value,boolean clipboard) {
        if(value==null||value.isEmpty()||value.length()>1000000) throw new IllegalArgumentException("Empty or too large");
        if(value.charAt(0)=='\ufeff') value=value.substring(1);char separator=clipboard?'\t':separator(value);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<value.length();i++) {char ch=value.charAt(i);
            if(quoted) {if(ch=='"') {if(i+1<value.length()&&value.charAt(i+1)=='"') {cell.append('"');i++;}else {quoted=false;closed=true;}}else cell.append(ch);}
            else if(ch=='"'&&cell.length()==0&&!closed) quoted=true;
            else if(ch==separator) {row.add(cell.toString());cell.setLength(0);closed=false;}
            else if(ch=='\r'||ch=='\n') {row.add(cell.toString());rows.add(row);row=new ArrayList<>();cell.setLength(0);closed=false;if(ch=='\r'&&i+1<value.length()&&value.charAt(i+1)=='\n') i++;}
            else {if(closed) throw new IllegalArgumentException("Unexpected text after quote");cell.append(ch);}
            if(cell.length()>4000||row.size()>NoteTable.MAX_COLS||rows.size()>NoteTable.MAX_ROWS) throw new IllegalArgumentException("Table exceeds limits");
        }
        if(quoted) throw new IllegalArgumentException("Unclosed quote");if(cell.length()>0||closed||!row.isEmpty()||rows.isEmpty()) {row.add(cell.toString());rows.add(row);}
        int cols=0;for(List<String> r:rows) cols=Math.max(cols,r.size());if(cols<1||cols>NoteTable.MAX_COLS||rows.size()>NoteTable.MAX_ROWS) throw new IllegalArgumentException("Table exceeds limits");for(List<String> r:rows) while(r.size()<cols) r.add("");return rows;
    }
    private static char separator(String value) {boolean quoted=false,start=true,comma=false;for(int i=0;i<value.length();i++) {char c=value.charAt(i);if(quoted) {if(c=='"') {if(i+1<value.length()&&value.charAt(i+1)=='"') i++;else quoted=false;}}else if(c=='"'&&start) {quoted=true;start=false;}else if(c=='\t') return '\t';else if(c==',') {comma=true;start=true;}else if(c=='\r'||c=='\n') start=true;else start=false;}return comma?',':'\t';}
    public static String write(NoteTable table,boolean csv) {StringBuilder out=new StringBuilder(csv?"\ufeff":"");char sep=csv?',':'\t';for(List<String> row:table.cells) {for(int c=0;c<row.size();c++) {if(c>0) out.append(sep);String value=row.get(c);if(csv||value.indexOf('\t')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0||value.indexOf('"')>=0) out.append('"').append(value.replace("\"","\"\"")).append('"');else out.append(value);}out.append("\r\n");}return out.toString();}
}
