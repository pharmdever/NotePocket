package com.notepocket.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;

/** Shared colors and interactive surfaces for the native app and dialogs. */
public final class PocketTheme {
    public static final int FOREST=0xff245849,INK=0xff203D34,MUTED=0xff59685F,CREAM=0xffF8F6F0,SURFACE=0xffFFFEFA,LINE=0xffDFE5D9;
    public static final int SAGE=0xffE4EDDE,LAVENDER=0xffEDE7F4,PEACH=0xffF8E7DA,BUTTER=0xffF5EDCE,RED=0xffA13E36;
    public static final int[] NOTES={SAGE,BUTTER,LAVENDER,PEACH};
    private PocketTheme() {}
    public static int dp(Context c,int value) {return Math.round(c.getResources().getDisplayMetrics().density*value);}
    public static GradientDrawable shape(Context c,int color,int radius,boolean stroke) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(stroke) d.setStroke(dp(c,1),LINE);return d;}
    public static StateListDrawable field(Context c) {StateListDrawable out=new StateListDrawable();GradientDrawable active=shape(c,SURFACE,16,true);active.setStroke(dp(c,2),FOREST);out.addState(new int[]{android.R.attr.state_focused},active);out.addState(new int[]{},shape(c,SURFACE,16,true));return out;}
    public static RippleDrawable ripple(Context c,int color,int radius) {return new RippleDrawable(ColorStateList.valueOf(0x20245849),shape(c,color,radius,false),shape(c,Color.WHITE,radius,false));}
    public static String symbol(String label) {
        if(label.contains("SOAP")) return "soap";if(label.contains("ตาราง")||label.contains("แถว")||label.contains("คอลัมน์")) return "table";
        if(label.contains("โฟลเดอร์")) return "folder";if(label.contains("ตัวหนา")) return "bold";if(label.contains("ตัวเอียง")) return "italic";
        if(label.contains("เส้นใต้")) return "underline";if(label.contains("ขีดฆ่า")) return "strike";if(label.contains("ไฮไลต์")) return "highlight";
        if(label.contains("สี")) return "color";if(label.contains("ขนาด")) return "size";if(label.contains("เช็กลิสต์")) return "check";
        if(label.contains("เลขลำดับ")) return "number";if(label.contains("หัวข้อ")) return "heading";if(label.contains("ค้นหา")) return "find";
        if(label.contains("ถังขยะ")||label.contains("ลบ")) return "trash";if(label.contains("ปักหมุด")||label.contains("บุ๊กมาร์ก")) return "bookmark";
        if(label.contains("นำเข้า")||label.contains("ไฟล์")) return "attach";if(label.contains("ส่งออก")||label.contains("แชร์")) return "share";
        if(label.contains("ตั้งค่า")||label.contains("ฟอนต์")||label.contains("เครื่องมือ")) return "tools";return "read";
    }
}
