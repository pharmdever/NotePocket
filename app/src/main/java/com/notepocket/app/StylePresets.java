package com.notepocket.app;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Small named, device-local presets. Application upgrades retain SharedPreferences. */
final class StylePresets {
    static final int MAX=24;static final String KEY="noteStylePresets";
    static final class Entry {final String name;final TypingStyle style;Entry(String n,TypingStyle s){name=n;style=s.copy();}}
    static List<Entry> read(SharedPreferences prefs){List<Entry> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(KEY,"[]"));for(int i=0;i<Math.min(a.length(),MAX);i++){JSONObject j=a.getJSONObject(i);String n=j.optString("name").trim();TypingStyle s=TypingStyle.parse(j.optJSONObject("style")==null?"":j.getJSONObject("style").toString());if(!n.isEmpty()&&n.length()<=40&&s!=null)out.add(new Entry(n,s));}}catch(Exception ignored){}return out;}
    static void write(SharedPreferences prefs,List<Entry> entries){if(entries.size()>MAX)throw new IllegalArgumentException("Preset limit");try{JSONArray a=new JSONArray();for(Entry e:entries){String n=e.name.trim();if(n.isEmpty()||n.length()>40)throw new IllegalArgumentException("Preset name");a.put(new JSONObject().put("name",n).put("style",new JSONObject(e.style.json())));}prefs.edit().putString(KEY,a.toString()).apply();}catch(JSONException e){throw new IllegalArgumentException(e);}}
}
