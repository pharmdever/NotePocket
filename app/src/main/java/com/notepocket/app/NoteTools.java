package com.notepocket.app;

import java.util.*;
import org.json.*;

public final class NoteTools {
    public static final String[] IDS={"bold","italic","check","table","soap","underline","strike","heading","size","color","highlight","bullet","number","clear","find","diagram","photo","preset","pen"};
    public static final String[] NAMES={"ตัวหนา","ตัวเอียง","เช็กลิสต์","แทรกตาราง","ตัวช่วย SOAP note","ขีดเส้นใต้","ขีดฆ่า","หัวข้อ","ขนาดข้อความ","สีข้อความ","ไฮไลต์ข้อความ","รายการหัวข้อ","รายการเลขลำดับ","ล้างรูปแบบ","ค้นหาในโน้ต","แผนภาพ Mermaid","รูปภาพ","พรีเซ็ตรูปแบบข้อความ","เขียนลายมือบนโน้ต"};
    private NoteTools() {}
    public static List<String> order(String json) {List<String> out=new ArrayList<>();try {JSONArray list=new JSONArray(json);for(int i=0;i<list.length();i++) {String key=list.getString(i);if(Arrays.asList(IDS).contains(key)&&!out.contains(key)) out.add(key);}}catch(JSONException ignored) {}for(String key:IDS) if(!out.contains(key)) out.add(key);return out;}
    public static String name(String key) {for(int i=0;i<IDS.length;i++) if(IDS[i].equals(key)) return NAMES[i];return "";}
}
