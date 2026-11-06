package com.notepocket.app;

import org.json.*;

/** A writing template. Only user-entered facts and assessments appear in the generated note. */
public final class SoapDraft {
    public static final String[] KEYS={"problem","reference","date","s","o","a","p"};
    public static final String[] LABELS={"ประเด็น / ปัญหาที่ติดตาม","รหัสอ้างอิง (ไม่บังคับ)","วันที่และเวลา","S • Subjective","O • Objective","A • Assessment","P • Plan"};
    public static final String[] HINTS={"เช่น ติดตามผลการใช้ยา / ปัญหาที่ประเมิน","รหัสที่คุณใช้แยกแต่ละเคส","ระบุวันที่และเวลาของข้อมูล","อาการที่ผู้ป่วยเล่า ระยะเวลา ผลกระทบ การใช้ยาและความร่วมมือ","ข้อมูลที่ตรวจวัดได้ พร้อมวันเวลา หน่วย แหล่งข้อมูล และรายการยาที่เกี่ยวข้อง","ปัญหาที่ประเมิน เหตุผลสนับสนุน เป้าหมาย และสิ่งที่ยังไม่ทราบ","แผนที่ประเมินแล้ว สิ่งที่จะติดตาม ตัวชี้วัด กรอบเวลา และวันนัด"};
    private final JSONObject data;
    public SoapDraft() {data=new JSONObject();}
    public SoapDraft(String json) {try {data=new JSONObject(json==null||json.isEmpty()?"{}":json);for(String key:KEYS) if(data.optString(key).length()>20000) throw new IllegalArgumentException("SOAP field too long");if(data.optString("applied").length()>150000) throw new IllegalArgumentException("SOAP block too long");}catch(JSONException e) {throw new IllegalArgumentException("Invalid SOAP draft",e);}}
    public String get(String key) {return data.optString(key,"");}
    public void put(String key,String value) {try {data.put(key,value);}catch(JSONException e) {throw new IllegalArgumentException(e);}}
    public boolean isEmpty() {for(String key:KEYS) if(!get(key).trim().isEmpty()) return false;return true;}
    public String json() {return data.toString();}
    public String render() {StringBuilder out=new StringBuilder("SOAP NOTE");if(!get("problem").trim().isEmpty()) out.append(" • ").append(get("problem").trim());for(String key:new String[]{"reference","date"}) if(!get(key).trim().isEmpty()) out.append('\n').append(key.equals("date")?"วันที่: ":"อ้างอิง: ").append(get(key).trim());for(int i=3;i<KEYS.length;i++) out.append("\n\n").append(LABELS[i]).append('\n').append(get(KEYS[i]).trim().isEmpty()?"[ยังไม่ได้ระบุ]":get(KEYS[i]).trim());return out.toString();}
}
