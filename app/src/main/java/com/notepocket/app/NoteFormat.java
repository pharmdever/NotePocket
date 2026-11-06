package com.notepocket.app;

import android.text.*;
import android.text.style.*;
import org.json.*;

/** Lossless note formatting, including colors and sizes unsupported by Android's HTML reader. */
public final class NoteFormat {
    private NoteFormat() {}
    public static Spanned snapshot(Spanned value) {
        SpannableString copy=new SpannableString(value.toString());
        for(CharacterStyle original:value.getSpans(0,value.length(),CharacterStyle.class)) {CharacterStyle span=copyStyle(original.getUnderlying());if(span==null) continue;int start=value.getSpanStart(original),end=value.getSpanEnd(original);if(start>=0&&end>start) copy.setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        return copy;
    }
    private static CharacterStyle copyStyle(CharacterStyle span) {
        if(span instanceof StyleSpan) return new StyleSpan(((StyleSpan)span).getStyle());
        if(span instanceof UnderlineSpan) return new UnderlineSpan();if(span instanceof StrikethroughSpan) return new StrikethroughSpan();
        if(span instanceof ForegroundColorSpan) return new ForegroundColorSpan(((ForegroundColorSpan)span).getForegroundColor());
        if(span instanceof BackgroundColorSpan) return new BackgroundColorSpan(((BackgroundColorSpan)span).getBackgroundColor());
        if(span instanceof AbsoluteSizeSpan) return new AbsoluteSizeSpan(((AbsoluteSizeSpan)span).getSize(),((AbsoluteSizeSpan)span).getDip());
        if(span instanceof RelativeSizeSpan) return new RelativeSizeSpan(((RelativeSizeSpan)span).getSizeChange());return null;
    }
    public static void toggleStyle(Editable text,int lo,int hi,int bit) {
        StyleSpan[] spans=text.getSpans(lo,hi,StyleSpan.class);java.util.TreeSet<Integer> edges=new java.util.TreeSet<>();edges.add(lo);edges.add(hi);
        for(StyleSpan span:spans) {edges.add(Math.max(lo,text.getSpanStart(span)));edges.add(Math.min(hi,text.getSpanEnd(span)));}
        java.util.List<Integer> points=new java.util.ArrayList<>(edges);int[] styles=new int[points.size()-1];boolean all=true;
        for(int i=0;i<styles.length;i++) {for(StyleSpan span:spans) if(text.getSpanStart(span)<points.get(i+1)&&text.getSpanEnd(span)>points.get(i)) styles[i]|=span.getStyle();if((styles[i]&bit)==0) all=false;}
        for(StyleSpan span:spans) {int a=text.getSpanStart(span),b=text.getSpanEnd(span),style=span.getStyle();text.removeSpan(span);if(a<lo) text.setSpan(new StyleSpan(style),a,lo,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(b>hi) text.setSpan(new StyleSpan(style),hi,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        for(int i=0;i<styles.length;i++) {int style=all?styles[i]&~bit:styles[i]|bit;if(style!=0) text.setSpan(new StyleSpan(style),points.get(i),points.get(i+1),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    }
    public static String encode(Spanned text) {
        try {
            JSONObject out=new JSONObject().put("format","NotePocket-rich-1").put("text",text.toString());JSONArray spans=new JSONArray();
            for(Object original:text.getSpans(0,text.length(),Object.class)) {
                Object span=original instanceof CharacterStyle?((CharacterStyle)original).getUnderlying():original;
                String type;int value=0;
                if(span instanceof StyleSpan) {type="style";value=((StyleSpan)span).getStyle();}
                else if(span instanceof UnderlineSpan) type="underline";
                else if(span instanceof StrikethroughSpan) type="strike";
                else if(span instanceof ForegroundColorSpan) {type="color";value=((ForegroundColorSpan)span).getForegroundColor();}
                else if(span instanceof BackgroundColorSpan) {type="highlight";value=((BackgroundColorSpan)span).getBackgroundColor();}
                else if(span instanceof AbsoluteSizeSpan) {type="size";AbsoluteSizeSpan size=(AbsoluteSizeSpan)span;if(!size.getDip()) continue;value=size.getSize();}
                else if(span instanceof RelativeSizeSpan) {type="relative";value=Math.round(((RelativeSizeSpan)span).getSizeChange()*1000);}
                else continue;
                int start=text.getSpanStart(original),end=text.getSpanEnd(original);if(start<0||end<=start) continue;
                spans.put(new JSONObject().put("type",type).put("value",value).put("start",start).put("end",end));
            }
            return out.put("spans",spans).toString();
        }catch(JSONException e) {throw new IllegalStateException(e);}
    }
    public static SpannableStringBuilder decode(String rich,String plain) {
        if(rich==null||rich.isEmpty()) return new SpannableStringBuilder(plain);
        if(!rich.startsWith("{")) {Spanned old=Html.fromHtml(rich,Html.FROM_HTML_MODE_LEGACY);return new SpannableStringBuilder(old.toString().startsWith(plain)?old.subSequence(0,plain.length()):old);}
        try {
            JSONObject in=new JSONObject(rich);if(!"NotePocket-rich-1".equals(in.getString("format"))) throw new JSONException("Unknown format");
            SpannableStringBuilder text=new SpannableStringBuilder(in.getString("text"));JSONArray spans=in.getJSONArray("spans");
            if(spans.length()>10000) throw new JSONException("Too many spans");
            for(int i=0;i<spans.length();i++) {JSONObject s=spans.getJSONObject(i);int start=s.getInt("start"),end=s.getInt("end"),value=s.optInt("value");Object span=null;
                switch(s.getString("type")) {case "style":if(value>=1&&value<=3) span=new StyleSpan(value);break;case "underline":span=new UnderlineSpan();break;case "strike":span=new StrikethroughSpan();break;case "color":span=new ForegroundColorSpan(value);break;case "highlight":span=new BackgroundColorSpan(value);break;case "size":if(value>=10&&value<=48) span=new AbsoluteSizeSpan(value,true);break;case "relative":if(value>=500&&value<=3000) span=new RelativeSizeSpan(value/1000f);break;}
                if(span!=null&&start>=0&&end>start&&end<=text.length()) text.setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }return text;
        }catch(JSONException e) {return new SpannableStringBuilder(plain);}
    }
}
