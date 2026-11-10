package com.notepocket.app;

import org.json.*;
import java.util.*;

/** Vector strokes anchored to text offsets; Y offsets use page width, never total note height. */
final class NoteInk {
    static final int MAX_STROKES=400,MAX_POINTS=20000,MAX_SPACE=20000;
    static final class Stroke {
        int anchor;final int color;final float width;final boolean highlight;final float[] xy;
        Stroke(int anchor,int color,float width,boolean highlight,float[] xy){if(anchor<0||anchor>1000000||width<=0||width>.08f||!Float.isFinite(width)||xy.length<2||xy.length>4000||xy.length%2!=0)throw new IllegalArgumentException("Invalid note stroke");for(int i=0;i<xy.length;i++)if(!Float.isFinite(xy[i])||(i%2==0?(xy[i]<0||xy[i]>1):Math.abs(xy[i])>100))throw new IllegalArgumentException("Invalid note point");this.anchor=anchor;this.color=color|0xff000000;this.width=width;this.highlight=highlight;this.xy=xy.clone();}
    }
    int space;final List<Stroke> strokes=new ArrayList<>();
    void shift(int start,int before,int count){for(Stroke s:strokes)if(s.anchor>=start)s.anchor=Math.min(1000000,s.anchor>=start+before?s.anchor+count-before:start+count);}
    int points(){int n=0;for(Stroke s:strokes)n+=s.xy.length/2;return n;}
    String json(){try{JSONObject out=new JSONObject().put("v",1).put("space",space);JSONArray list=new JSONArray();if(strokes.size()>MAX_STROKES)throw new IllegalArgumentException("Note ink full");int n=0;for(Stroke s:strokes){n+=s.xy.length/2;if(n>MAX_POINTS)throw new IllegalArgumentException("Note ink full");JSONArray xy=new JSONArray();for(float f:s.xy)xy.put(Math.round(f*10000));list.put(new JSONObject().put("at",s.anchor).put("color",s.color).put("width",s.width).put("highlight",s.highlight).put("xy",xy));}return out.put("strokes",list).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static NoteInk parse(String json){try{JSONObject in=new JSONObject(json);if(in.getInt("v")!=1)throw new JSONException("Note ink version");NoteInk result=new NoteInk();result.space=in.optInt("space");if(result.space<0||result.space>MAX_SPACE)throw new JSONException("Note ink space");JSONArray list=in.getJSONArray("strokes");if(list.length()>MAX_STROKES)throw new JSONException("Note ink full");int n=0;for(int i=0;i<list.length();i++){JSONObject s=list.getJSONObject(i);JSONArray xy=s.getJSONArray("xy");n+=xy.length()/2;if(n>MAX_POINTS||xy.length()>4000)throw new JSONException("Note ink full");float[] points=new float[xy.length()];for(int j=0;j<points.length;j++)points[j]=(float)xy.getDouble(j)/10000f;result.strokes.add(new Stroke(s.getInt("at"),s.getInt("color"),(float)s.getDouble("width"),s.getBoolean("highlight"),points));}return result;}catch(JSONException e){throw new IllegalArgumentException("Invalid note ink",e);}}
    static NoteInk fromRich(String rich){if(rich==null||!rich.startsWith("{"))return new NoteInk();try{JSONObject j=new JSONObject(rich);return j.has("noteInk")?parse(j.getJSONObject("noteInk").toString()):new NoteInk();}catch(JSONException e){return new NoteInk();}}
    static String rich(String rich,NoteInk ink){if(ink.strokes.isEmpty()&&ink.space==0)return rich;try{return new JSONObject(rich).put("noteInk",new JSONObject(ink.json())).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    static void validateRich(String rich){if(rich==null||!rich.startsWith("{"))return;try{JSONObject j=new JSONObject(rich);if(j.has("noteInk"))parse(j.getJSONObject("noteInk").toString());}catch(JSONException e){throw new IllegalArgumentException(e);}}
}
