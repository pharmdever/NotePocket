package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Read/pan or draw in page coordinates. Two fingers always zoom; multi-touch cancels the active stroke. */
public final class PdfPageView extends View {
    public interface Listener { boolean save(String drawing); }
    public static final int READ=0,PEN=1,HIGHLIGHT=2;
    private Bitmap bitmap;
    private float zoom=1,offsetX,offsetY,lastX,lastY;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint inkPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private List<Ink> strokes=new ArrayList<>();
    private final List<Ink> redo=new ArrayList<>();
    private final List<Float> active=new ArrayList<>();
    private boolean drawing=false,multiTouch=false;
    private int mode=READ,penColor=0xff245BCC,highlightColor=0xffFFD54F;
    private float widthFactor=1;
    private Listener listener;
    public PdfPageView(Context context) {
        super(context);setContentDescription("หน้า PDF");setBackgroundColor(0xffE7EBE8);
        inkPaint.setStyle(Paint.Style.STROKE);inkPaint.setStrokeCap(Paint.Cap.ROUND);inkPaint.setStrokeJoin(Paint.Join.ROUND);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {changeZoom(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {return true;}
            @Override public boolean onDoubleTap(MotionEvent e) {if(mode!=READ) return false;if(zoom>1.1f) reset();else changeZoom(2,e.getX(),e.getY());return true;}
        });
    }
    public void setListener(Listener value) {listener=value;}
    public void setMode(int value) {commitGesture();mode=value;}
    public int getMode() {return mode;}
    public void setInkColor(int color) {if(mode==HIGHLIGHT) highlightColor=color;else penColor=color;}
    public void setWidthFactor(float value) {widthFactor=value;}
    public void setDrawing(String json) {strokes=Ink.decode(json);active.clear();drawing=false;redo.clear();invalidate();}
    public String getDrawing() {return Ink.encode(strokes);}
    public void setPage(Bitmap b) {bitmap=b;active.clear();drawing=false;strokes.clear();redo.clear();reset();}
    public void reset() {zoom=1;offsetX=offsetY=0;invalidate();}
    public void zoomBy(float factor) {commitGesture();changeZoom(zoom*factor,getWidth()/2f,getHeight()/2f);}
    public void undo() {
        commitGesture();if(strokes.isEmpty()) return;Ink last=strokes.remove(strokes.size()-1);
        if(persist()) redo.add(last);else strokes.add(last);invalidate();
    }
    public void redo() {
        commitGesture();if(redo.isEmpty()) return;Ink stroke=redo.remove(redo.size()-1);strokes.add(stroke);
        if(!persist()) {strokes.remove(strokes.size()-1);redo.add(stroke);}invalidate();
    }
    public void clearDrawing() {
        commitGesture();List<Ink> old=strokes;strokes=new ArrayList<>();if(persist()) redo.clear();else strokes=old;invalidate();
    }
    private boolean persist() {
        try {return listener==null||listener.save(Ink.encode(strokes));}
        catch(IllegalArgumentException e) {android.widget.Toast.makeText(getContext(),"หน้านี้มีเส้นจำนวนมากแล้ว กรุณาใช้หน้าอื่น",android.widget.Toast.LENGTH_LONG).show();return false;}
    }
    public void commitGesture() {
        if(!drawing) return;drawing=false;
        if(!active.isEmpty()) {
            float[] points=new float[active.size()];for(int i=0;i<points.length;i++) points[i]=active.get(i);
            Ink stroke=new Ink(mode==HIGHLIGHT,mode==HIGHLIGHT?highlightColor:penColor,(mode==HIGHLIGHT?0.028f:0.004f)*widthFactor,points);
            strokes.add(stroke);if(persist()) redo.clear();else strokes.remove(strokes.size()-1);
        }
        active.clear();invalidate();
    }
    private float baseScale() {return bitmap==null?1:Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());}
    private RectF pageRect() {
        if(bitmap==null) return new RectF();float s=baseScale()*zoom,w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;
        return new RectF((getWidth()-w)/2+offsetX,(getHeight()-h)/2+offsetY,(getWidth()+w)/2+offsetX,(getHeight()+h)/2+offsetY);
    }
    private void changeZoom(float next,float focusX,float focusY) {
        next=Math.max(1,Math.min(5,next));float ratio=next/zoom;
        offsetX=(offsetX+getWidth()/2f-focusX)*ratio-(getWidth()/2f-focusX);offsetY=(offsetY+getHeight()/2f-focusY)*ratio-(getHeight()/2f-focusY);
        zoom=next;clamp();invalidate();
    }
    private void clamp() {
        if(bitmap==null) return;float sx=Math.max(0,(bitmap.getWidth()*baseScale()*zoom-getWidth())/2f),sy=Math.max(0,(bitmap.getHeight()*baseScale()*zoom-getHeight())/2f);
        offsetX=Math.max(-sx,Math.min(sx,offsetX));offsetY=Math.max(-sy,Math.min(sy,offsetY));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {clamp();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(bitmap==null||bitmap.isRecycled()) return;RectF rect=pageRect();canvas.drawBitmap(bitmap,null,rect,paint);
        canvas.save();canvas.clipRect(rect);
        for(Ink stroke:strokes) drawStroke(canvas,rect,stroke);
        if(drawing&&!active.isEmpty()) {float[] points=new float[active.size()];for(int i=0;i<points.length;i++) points[i]=active.get(i);drawStroke(canvas,rect,new Ink(mode==HIGHLIGHT,mode==HIGHLIGHT?highlightColor:penColor,(mode==HIGHLIGHT?0.028f:0.004f)*widthFactor,points));}
        canvas.restore();
    }
    private void drawStroke(Canvas canvas,RectF rect,Ink stroke) {
        inkPaint.setColor(stroke.color);inkPaint.setAlpha(stroke.highlight?85:255);inkPaint.setStrokeWidth(stroke.width*Math.min(rect.width(),rect.height()));
        inkPaint.setXfermode(null);
        float[] p=stroke.points;Path path=new Path();path.moveTo(rect.left+p[0]*rect.width(),rect.top+p[1]*rect.height());
        if(p.length==2) canvas.drawPoint(rect.left+p[0]*rect.width(),rect.top+p[1]*rect.height(),inkPaint);
        else {for(int i=2;i<p.length;i+=2) path.lineTo(rect.left+p[i]*rect.width(),rect.top+p[i+1]*rect.height());canvas.drawPath(path,inkPaint);}
    }
    private void point(float x,float y) {
        if(active.size()>=4000) return;RectF rect=pageRect();if(rect.width()<=0||rect.height()<=0) return;
        float u=Math.max(0,Math.min(1,(x-rect.left)/rect.width())),v=Math.max(0,Math.min(1,(y-rect.top)/rect.height()));
        if(!active.isEmpty()) {float dx=(u-active.get(active.size()-2))*rect.width(),dy=(v-active.get(active.size()-1))*rect.height();if(dx*dx+dy*dy<4) return;}
        active.add(u);active.add(v);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(mode==READ) gestures.onTouchEvent(e);
        scale.onTouchEvent(e);
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                multiTouch=false;lastX=e.getX();lastY=e.getY();active.clear();drawing=mode!=READ&&bitmap!=null&&pageRect().contains(lastX,lastY);if(drawing) point(lastX,lastY);break;
            case MotionEvent.ACTION_POINTER_DOWN:
                drawing=false;active.clear();multiTouch=true;invalidate();break;
            case MotionEvent.ACTION_MOVE:
                if(drawing&&!multiTouch) {point(e.getX(),e.getY());invalidate();}
                else if(!scale.isInProgress()&&e.getPointerCount()==1&&(mode==READ||multiTouch)) {offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;clamp();invalidate();}
                lastX=e.getX();lastY=e.getY();break;
            case MotionEvent.ACTION_POINTER_UP:
                int i=e.getActionIndex()==0?1:0;lastX=e.getX(i);lastY=e.getY(i);break;
            case MotionEvent.ACTION_UP:
                if(drawing) point(e.getX(),e.getY());commitGesture();performClick();break;
            case MotionEvent.ACTION_CANCEL:
                drawing=false;active.clear();invalidate();break;
        }
        return true;
    }
    @Override public boolean performClick() {super.performClick();return true;}
}
