package com.notepocket.app;

import org.json.*;
import java.util.*;

/** Coordinates are normalized to the displayed crop box, independent of screen size and zoom. */
public final class Ink {
    public static final int MAX_STROKES=400,MAX_POINTS=20000;
    public final boolean highlight;
    public final int color;
    public final float width;
    public final float[] points;
    public Ink(boolean highlight,int color,float width,float[] points) {
        if(width<=0||width>0.08f||!Float.isFinite(width)||points.length<2||points.length%2!=0||points.length>4000) throw new IllegalArgumentException("Invalid stroke");
        for(float value:points) if(!Float.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Invalid stroke point");
        this.highlight=highlight;this.color=color|0xff000000;this.width=width;this.points=points.clone();
    }
    public static String encode(List<Ink> strokes) {
        try {
            if(strokes.size()>MAX_STROKES) throw new IllegalArgumentException("Page is full");JSONArray result=new JSONArray();int count=0;
            for(Ink stroke:strokes) {
                count+=stroke.points.length/2;if(count>MAX_POINTS) throw new IllegalArgumentException("Page is full");
                JSONObject object=new JSONObject();object.put("highlight",stroke.highlight);object.put("color",stroke.color);object.put("width",stroke.width);
                JSONArray points=new JSONArray();for(float value:stroke.points) points.put(Math.round(value*10000));object.put("points",points);result.put(object);
            }
            return result.toString();
        } catch(JSONException e) {throw new IllegalArgumentException(e);}
    }
    public static List<Ink> decode(String json) {
        try {
            JSONArray values=new JSONArray(json);if(values.length()>MAX_STROKES) throw new IllegalArgumentException("Page is full");List<Ink> result=new ArrayList<>();int count=0;
            for(int i=0;i<values.length();i++) {
                JSONObject object=values.getJSONObject(i);JSONArray xy=object.getJSONArray("points");count+=xy.length()/2;
                if(count>MAX_POINTS||xy.length()>4000) throw new IllegalArgumentException("Page is full");
                float[] points=new float[xy.length()];for(int j=0;j<points.length;j++) points[j]=(float)xy.getDouble(j)/10000f;
                result.add(new Ink(object.getBoolean("highlight"),object.getInt("color"),(float)object.getDouble("width"),points));
            }
            return result;
        } catch(JSONException e) {throw new IllegalArgumentException("Invalid drawing",e);}
    }
}
