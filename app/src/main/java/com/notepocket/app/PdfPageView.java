package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Pinch to zoom, drag to pan, double tap to reset; bounded bitmap allocation. */
public final class PdfPageView extends View {
    private Bitmap bitmap;
    private float zoom=1,offsetX=0,offsetY=0,lastX,lastY;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    public PdfPageView(Context context) {
        super(context);setContentDescription("หน้า PDF ใช้สองนิ้วซูม ลากเพื่อเลื่อน และแตะสองครั้งเพื่อคืนขนาด");
        setBackgroundColor(Color.rgb(231,235,232));
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                changeZoom(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { if(zoom>1.1f) reset();else changeZoom(2,e.getX(),e.getY());return true; }
        });
    }
    public void setPage(Bitmap b) { bitmap=b; reset(); }
    public void reset() { zoom=1;offsetX=offsetY=0;invalidate(); }
    public void zoomBy(float factor) { changeZoom(zoom*factor,getWidth()/2f,getHeight()/2f); }
    private float baseScale() { return bitmap==null?1:Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight()); }
    private void changeZoom(float next,float focusX,float focusY) {
        next=Math.max(1,Math.min(5,next));float ratio=next/zoom;
        offsetX=(offsetX+getWidth()/2f-focusX)*ratio-(getWidth()/2f-focusX);
        offsetY=(offsetY+getHeight()/2f-focusY)*ratio-(getHeight()/2f-focusY);
        zoom=next;clamp();invalidate();
    }
    private void clamp() {
        if(bitmap==null) return;
        float sx=Math.max(0,(bitmap.getWidth()*baseScale()*zoom-getWidth())/2f);
        float sy=Math.max(0,(bitmap.getHeight()*baseScale()*zoom-getHeight())/2f);
        offsetX=Math.max(-sx,Math.min(sx,offsetX));offsetY=Math.max(-sy,Math.min(sy,offsetY));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { clamp(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(bitmap==null||bitmap.isRecycled()) return;
        float s=baseScale()*zoom,w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;
        canvas.drawBitmap(bitmap,null,new RectF((getWidth()-w)/2+offsetX,(getHeight()-h)/2+offsetY,(getWidth()+w)/2+offsetX,(getHeight()+h)/2+offsetY),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);scale.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) { lastX=e.getX();lastY=e.getY(); }
        else if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
            if(!scale.isInProgress()&&e.getPointerCount()==1) { offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;clamp();invalidate(); }
            lastX=e.getX();lastY=e.getY();
        } else if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP) { int i=e.getActionIndex()==0?1:0;lastX=e.getX(i);lastY=e.getY(i); }
        else if(e.getActionMasked()==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick() { super.performClick();return true; }
}
