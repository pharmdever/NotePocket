package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Vector icons with Android touch/focus behavior, spoken labels and long-press tooltips. */
public final class ReaderIconButton extends View {
    private final String icon,caption;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ReaderIconButton(Context context,String icon,String label,String caption,Runnable action) {
        super(context);this.icon=icon;this.caption=caption;setContentDescription(label);setTooltipText(label);setFocusable(true);setClickable(true);setOnClickListener(v->action.run());
        int size=Math.round(48*getResources().getDisplayMetrics().density);setMinimumWidth(size);setMinimumHeight(size);
    }
    @Override protected void drawableStateChanged() {super.drawableStateChanged();invalidate();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");info.setSelected(isSelected());}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        int color=!isEnabled()?0xffA2AAA4:isSelected()?Color.WHITE:0xff216B58;
        paint.setStyle(Paint.Style.FILL);paint.setColor(isSelected()?0xff216B58:isPressed()||isFocused()?0xffD9E7D7:0xffEFF4EC);
        canvas.drawRoundRect(new RectF(3*density,3*density,getWidth()-3*density,getHeight()-3*density),12*density,12*density,paint);
        canvas.save();float side=24*density,cy=caption==null?getHeight()/2f:22*density;canvas.translate((getWidth()-side)/2,cy-side/2);canvas.scale(side/24,side/24);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.9f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setPathEffect(null);
        switch(icon) {
            case "read": path(canvas,3,5,3,19,11,21,12,21,21,19,21,5,13,7,12,7,3,5);line(canvas,12,7,12,21);line(canvas,6,10,9,11);line(canvas,15,11,18,10);break;
            case "pen": path(canvas,4,20,5,14,16,3,21,8,10,19,4,20);line(canvas,13,6,18,11);break;
            case "highlight": path(canvas,5,14,14,5,20,11,11,20,5,14);line(canvas,4,21,15,21);line(canvas,7,16,9,18);break;
            case "select": paint.setPathEffect(new DashPathEffect(new float[]{2,2},0));canvas.drawRoundRect(new RectF(3,4,21,20),1,1,paint);paint.setPathEffect(null);line(canvas,12,7,12,17);line(canvas,9,7,15,7);line(canvas,9,17,15,17);break;
            case "text": line(canvas,5,5,19,5);line(canvas,5,5,5,8);line(canvas,19,5,19,8);line(canvas,12,5,12,20);line(canvas,9,20,15,20);break;
            case "redo": canvas.scale(-1,1,12,12);undo(canvas);break;
            case "undo": undo(canvas);break;
            case "next": path(canvas,9,5,16,12,9,19);break;
            case "previous": case "back": path(canvas,15,5,8,12,15,19);break;
            case "full": path(canvas,9,3,3,3,3,9);path(canvas,15,3,21,3,21,9);path(canvas,3,15,3,21,9,21);path(canvas,15,21,21,21,21,15);break;
            case "tools": for(int y:new int[]{6,12,18}) line(canvas,3,y,21,y);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(8,6,2.8f,paint);canvas.drawCircle(16,12,2.8f,paint);canvas.drawCircle(10,18,2.8f,paint);break;
            default: paint.setStyle(Paint.Style.FILL);for(int y:new int[]{5,12,19}) canvas.drawCircle(12,y,1.8f,paint);
        }
        canvas.restore();if(caption!=null) {paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);canvas.drawText(caption,getWidth()/2f,getHeight()-9*density,paint);paint.setTextAlign(Paint.Align.LEFT);}
    }
    private void undo(Canvas canvas) {path(canvas,8,4,3,9,8,14);Path p=new Path();p.moveTo(3,9);p.lineTo(13,9);p.cubicTo(22,9,22,19,15,21);canvas.drawPath(p,paint);}
    private void line(Canvas canvas,float x,float y,float endX,float endY) {canvas.drawLine(x,y,endX,endY,paint);}
    private void path(Canvas canvas,float...xy) {Path p=new Path();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2) p.lineTo(xy[i],xy[i+1]);canvas.drawPath(p,paint);}
}
