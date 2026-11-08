package com.notepocket.app;

import android.graphics.*;
import android.text.*;
import android.text.style.*;

/** A whole Mermaid source block behaves as one visual object without changing the stored text. */
final class InlineDiagramSpan extends ReplacementSpan implements LineHeightSpan {
    final InlineNoteEditText owner;final String key;boolean failed;
    int imageWidth=800,imageHeight=360;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    InlineDiagramSpan(InlineNoteEditText owner,String key){this.owner=owner;this.key=key;if(android.os.Build.VERSION.SDK_INT>=30)setContentDescription("แผนภาพ Mermaid แตะเพื่อแก้ไข");}
    int width(){return Math.max(80,owner.getWidth()-owner.getCompoundPaddingLeft()-owner.getCompoundPaddingRight());}
    private float scale(){return Math.min((width()-owner.dp(16))/(float)imageWidth,owner.dp(300)/(float)imageHeight);}
    int height(){return Math.max(owner.dp(68),Math.round(imageHeight*scale()))+owner.dp(40);}
    private int start(){return owner.getText().getSpanStart(this);}
    private void metrics(Paint.FontMetricsInt fm,int h){if(fm!=null){fm.top=fm.ascent=-h;fm.descent=fm.bottom=0;fm.leading=0;}}
    @Override public int getSize(Paint p,CharSequence text,int start,int end,Paint.FontMetricsInt fm){boolean first=start==start();metrics(fm,first?height():0);return first?width():0;}
    @Override public void chooseHeight(CharSequence text,int start,int end,int spanstartv,int v,Paint.FontMetricsInt fm){int origin=start();metrics(fm,start<=origin&&origin<end?height():0);}
    @Override public void draw(Canvas c,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint p){if(start!=start())return;int w=width(),h=height();paint.setStyle(Paint.Style.FILL);paint.setColor(0xffF2F6EF);c.drawRoundRect(x+2,top+owner.dp(4),x+w-2,top+h-owner.dp(4),owner.dp(12),owner.dp(12),paint);Bitmap bitmap=owner.bitmap(key);float pad=owner.dp(8),footer=owner.dp(30);if(bitmap!=null){float scale=Math.min((w-pad*2)/bitmap.getWidth(),(h-footer-pad*2)/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale,left=x+(w-bw)/2,upper=top+pad+(h-footer-pad*2-bh)/2;c.drawBitmap(bitmap,null,new RectF(left,upper,left+bw,upper+bh),paint);}else {paint.setColor(PocketTheme.MUTED);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(owner.dp(13));paint.setTextAlign(Paint.Align.CENTER);c.drawText(failed?"สร้างภาพไม่ได้ · แตะเพื่อตรวจโค้ด":"กำลังเตรียมแผนภาพ…",x+w/2f,top+(h-footer)/2f,paint);}
        paint.setColor(PocketTheme.FOREST);paint.setTextSize(owner.dp(12));paint.setTextAlign(Paint.Align.CENTER);c.drawText("แผนภาพ Mermaid · แตะเพื่อแก้ไข",x+w/2f,top+h-owner.dp(14),paint);paint.setTextAlign(Paint.Align.LEFT);
    }
}
