package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import org.json.*;
import java.util.*;

/** Editable text stickers in normalized page coordinates. Android shapes Thai and imported fonts. */
public final class TextBox {
    public final String id,text,font;
    public final float x,y,width,size;
    public final int color;
    public TextBox(String id,String text,String font,float x,float y,float width,float size,int color) {
        if(id==null||!id.matches("[A-Za-z0-9-]{1,80}")||text==null||text.length()>2000||font==null||!font.matches("[A-Za-z0-9-]{1,80}")) throw new IllegalArgumentException("Invalid text box");
        for(float value:new float[]{x,y,width,size}) if(!Float.isFinite(value)) throw new IllegalArgumentException();
        if(x<0||x>=1||y<0||y>=1||width<0.05f||width>1||size<0.006f||size>0.12f) throw new IllegalArgumentException();
        this.id=id;this.text=text;this.font=font;this.x=x;this.y=y;this.width=width;this.size=size;this.color=color|0xff000000;
    }
    public TextBox move(float x,float y) {return new TextBox(id,text,font,Math.max(0,Math.min(.95f,x)),Math.max(0,Math.min(.95f,y)),width,size,color);}
    public static String encode(List<TextBox> boxes) {
        if(boxes.size()>100) throw new IllegalArgumentException("Too many text boxes");
        try {JSONArray array=new JSONArray();for(TextBox box:boxes) {JSONObject o=new JSONObject();o.put("id",box.id);o.put("text",box.text);o.put("font",box.font);o.put("x",box.x);o.put("y",box.y);o.put("width",box.width);o.put("size",box.size);o.put("color",box.color);array.put(o);}return array.toString();}
        catch(JSONException e) {throw new IllegalArgumentException(e);}
    }
    public static List<TextBox> decode(String json) {
        try {JSONArray array=new JSONArray(json);if(array.length()>100) throw new IllegalArgumentException();List<TextBox> result=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(int i=0;i<array.length();i++) {JSONObject o=array.getJSONObject(i);TextBox box=new TextBox(o.getString("id"),o.getString("text"),o.getString("font"),(float)o.getDouble("x"),(float)o.getDouble("y"),(float)o.getDouble("width"),(float)o.getDouble("size"),o.getInt("color"));if(!ids.add(box.id)) throw new IllegalArgumentException();result.add(box);}return result;}
        catch(JSONException e) {throw new IllegalArgumentException(e);}
    }
    public StaticLayout layout(Context context,float pageWidth,float pageHeight) {
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(color);paint.setTextSize(size*Math.min(pageWidth,pageHeight));paint.setTypeface(Fonts.typeface(context,font));
        int pixels=Math.max(1,Math.round(Math.min(width,1-x)*pageWidth));return StaticLayout.Builder.obtain(text,0,text.length(),paint,pixels).setIncludePad(false).setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY).build();
    }
    public RectF bounds(Context context,RectF page) {StaticLayout layout=layout(context,page.width(),page.height());return new RectF(page.left+x*page.width(),page.top+y*page.height(),page.left+x*page.width()+layout.getWidth(),page.top+y*page.height()+layout.getHeight());}
    public void draw(Context context,Canvas canvas,RectF page) {StaticLayout layout=layout(context,page.width(),page.height());canvas.save();canvas.translate(page.left+x*page.width(),page.top+y*page.height());layout.draw(canvas);canvas.restore();}
}
