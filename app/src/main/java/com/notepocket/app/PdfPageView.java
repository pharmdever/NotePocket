package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Read/pan or draw in page coordinates. Two fingers always zoom; multi-touch cancels the active stroke. */
public final class PdfPageView extends View {
    public interface Listener { boolean save(String drawing); }
    public static final int READ=0,PEN=1,HIGHLIGHT=2,TEXT=3,SELECT=4;
    public interface TextListener {void edit(TextBox box,float x,float y);boolean save(String boxes);}
    public interface SelectionListener {void selected();void request(float u,float v);}
    private TextListener textListener;
    private SelectionListener selectionListener;
    private List<TextBox> boxes=new ArrayList<>();
    private List<PdfWords.Glyph> glyphs=new ArrayList<>();
    private int selectionStart=-1,selectionEnd=-1,dragIndex=-1;
    private float touchU,touchV;private TextBox dragOriginal;private boolean textMoved;
    private boolean fitWidth=true,needsPosition=true;
    private int highlightAlpha=61;
    public void setTextListener(TextListener value) {textListener=value;}
    public void setSelectionListener(SelectionListener value) {selectionListener=value;}
    public void setTextBoxes(String value) {boxes=TextBox.decode(value);invalidate();}
    public String getTextBoxes() {return TextBox.encode(boxes);}
    public void setWords(List<PdfWords.Glyph> words) {glyphs=words;selectionStart=selectionEnd=-1;invalidate();}
    public void selectAt(float u,float v) {selectionStart=selectionEnd=hitGlyph(u,v);invalidate();if(selectionStart>=0&&selectionListener!=null) selectionListener.selected();}
    public void clearSelection() {selectionStart=selectionEnd=-1;invalidate();}
    public void setFitWidth(boolean value) {fitWidth=value;reset();}
    public void setHighlightOpacity(int percent) {highlightAlpha=Math.round(Math.max(10,Math.min(50,percent))*2.55f);invalidate();}
    public String selectedText() {StringBuilder text=new StringBuilder();if(selectionStart>=0) for(int i=Math.min(selectionStart,selectionEnd);i<=Math.max(selectionStart,selectionEnd);i++) text.append(glyphs.get(i).text);return text.toString();}
    public void highlightSelection() {
        if(selectionStart<0||bitmap==null) return;List<RectF> groups=new ArrayList<>();
        for(int i=Math.min(selectionStart,selectionEnd);i<=Math.max(selectionStart,selectionEnd);i++) {
            RectF box=glyphs.get(i).box;if(groups.isEmpty()) {groups.add(new RectF(box));continue;}RectF last=groups.get(groups.size()-1);
            if((Math.abs(last.centerY()-box.centerY())<Math.max(last.height(),box.height())*.6f&&box.left-last.right<.04f)||(last.width()*bitmap.getWidth()<last.height()*bitmap.getHeight()&&box.width()*bitmap.getWidth()<box.height()*bitmap.getHeight()&&Math.abs(last.centerX()-box.centerX())<Math.max(last.width(),box.width())*.6f&&box.top-last.bottom<.02f)) last.union(box);else groups.add(new RectF(box));
        }
        int old=strokes.size();float minimum=Math.min(bitmap.getWidth(),bitmap.getHeight());
        for(RectF box:groups) {
            boolean horizontal=box.width()*bitmap.getWidth()>=box.height()*bitmap.getHeight();
            float width=horizontal?box.height()*bitmap.getHeight()/minimum:box.width()*bitmap.getWidth()/minimum;
            float[] xy=horizontal?new float[]{box.left,box.centerY(),box.right,box.centerY()}:new float[]{box.centerX(),box.top,box.centerX(),box.bottom};
            strokes.add(new Ink(true,highlightColor,Math.max(.002f,Math.min(.08f,width)),xy));
        }
        if(!persist()) while(strokes.size()>old) strokes.remove(strokes.size()-1);else redo.clear();clearSelection();
    }
    private int hitGlyph(float u,float v) {int nearest=-1;float distance=.0025f;for(int i=0;i<glyphs.size();i++) {RectF b=glyphs.get(i).box;if(b.contains(u,v)) return i;float d=(b.centerX()-u)*(b.centerX()-u)+(b.centerY()-v)*(b.centerY()-v);if(d<distance) {distance=d;nearest=i;}}return nearest;}
    private float[] normalized(float x,float y) {RectF r=pageRect();return new float[]{Math.max(0,Math.min(.999f,(x-r.left)/r.width())),Math.max(0,Math.min(.999f,(y-r.top)/r.height()))};}
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
        super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("หน้า PDF");setBackgroundColor(0xffE7EBE8);
        inkPaint.setStyle(Paint.Style.STROKE);inkPaint.setStrokeCap(Paint.Cap.ROUND);inkPaint.setStrokeJoin(Paint.Join.ROUND);
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {changeZoom(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {return true;}
            @Override public void onLongPress(MotionEvent e) {if(mode==READ&&selectionListener!=null&&pageRect().contains(e.getX(),e.getY())) {float[] p=normalized(e.getX(),e.getY());selectionListener.request(p[0],p[1]);}}
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
    public void setPage(Bitmap b) {bitmap=b;active.clear();drawing=false;strokes.clear();boxes.clear();glyphs.clear();clearSelection();redo.clear();needsPosition=true;reset();}
    public void reset() {zoom=1;offsetX=0;offsetY=bitmap==null?0:Math.max(0,(bitmap.getHeight()*baseScale()-getHeight())/2f);if(getWidth()>0&&getHeight()>0) needsPosition=false;invalidate();}
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
    private float baseScale() {return bitmap==null?1:(fitWidth?(float)getWidth()/bitmap.getWidth():Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight()));}
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
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {if(needsPosition) reset();else clamp();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(bitmap==null||bitmap.isRecycled()) return;if(needsPosition) reset();RectF rect=pageRect();
        Ink pending=null;if(drawing&&!active.isEmpty()) {float[] points=new float[active.size()];for(int i=0;i<points.length;i++) points[i]=active.get(i);pending=new Ink(mode==HIGHLIGHT,mode==HIGHLIGHT?highlightColor:penColor,(mode==HIGHLIGHT?0.028f:0.004f)*widthFactor,points);}
        canvas.save();canvas.clipRect(rect);boolean highlights=pending!=null&&pending.highlight;for(Ink stroke:strokes) highlights|=stroke.highlight;
        if(highlights) {
            int layer=canvas.saveLayer(rect,null);canvas.drawColor(Color.WHITE);
            for(Ink stroke:strokes) if(stroke.highlight) drawStroke(canvas,rect,stroke);if(pending!=null&&pending.highlight) drawStroke(canvas,rect,pending);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));canvas.drawBitmap(bitmap,null,rect,paint);paint.setXfermode(null);canvas.restoreToCount(layer);
        } else canvas.drawBitmap(bitmap,null,rect,paint);
        for(Ink stroke:strokes) if(!stroke.highlight) drawStroke(canvas,rect,stroke);if(pending!=null&&!pending.highlight) drawStroke(canvas,rect,pending);
        for(TextBox box:boxes) box.draw(getContext(),canvas,rect);
        if(selectionStart>=0) {Paint selection=new Paint();selection.setColor(0x553A86FF);for(int i=Math.min(selectionStart,selectionEnd);i<=Math.max(selectionStart,selectionEnd);i++) {RectF b=glyphs.get(i).box;canvas.drawRect(rect.left+b.left*rect.width(),rect.top+b.top*rect.height(),rect.left+b.right*rect.width(),rect.top+b.bottom*rect.height(),selection);}}
        canvas.restore();
    }
    private void drawStroke(Canvas canvas,RectF rect,Ink stroke) {
        inkPaint.setColor(stroke.color);inkPaint.setAlpha(stroke.highlight?highlightAlpha:255);inkPaint.setStrokeWidth(stroke.width*Math.min(rect.width(),rect.height()));
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
                multiTouch=false;lastX=e.getX();lastY=e.getY();active.clear();drawing=(mode==PEN||mode==HIGHLIGHT)&&bitmap!=null&&pageRect().contains(lastX,lastY);if(drawing) point(lastX,lastY);
                if(bitmap!=null&&pageRect().contains(lastX,lastY)) {
                    float[] p=normalized(lastX,lastY);touchU=p[0];touchV=p[1];dragIndex=-1;textMoved=false;
                    if(mode==TEXT) {for(int j=boxes.size()-1;j>=0;j--) if(boxes.get(j).bounds(getContext(),pageRect()).contains(lastX,lastY)) {dragIndex=j;dragOriginal=boxes.get(j);break;}}
                    if(mode==SELECT) {selectionStart=selectionEnd=hitGlyph(touchU,touchV);invalidate();}
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                drawing=false;active.clear();if(dragIndex>=0) boxes.set(dragIndex,dragOriginal);dragIndex=-1;multiTouch=true;clearSelection();invalidate();break;
            case MotionEvent.ACTION_MOVE:
                if(mode==TEXT&&dragIndex>=0&&!multiTouch) {float[] p=normalized(e.getX(),e.getY());float du=p[0]-touchU,dv=p[1]-touchV;if(Math.abs(du)+Math.abs(dv)>.008f) textMoved=true;if(textMoved) boxes.set(dragIndex,dragOriginal.move(dragOriginal.x+du,dragOriginal.y+dv));invalidate();}
                else if(mode==SELECT&&!multiTouch&&selectionStart>=0) {float[] p=normalized(e.getX(),e.getY());int end=hitGlyph(p[0],p[1]);if(end>=0) selectionEnd=end;invalidate();}
                else if(drawing&&!multiTouch) {point(e.getX(),e.getY());invalidate();}
                else if(!scale.isInProgress()&&e.getPointerCount()==1&&(mode==READ||multiTouch)) {offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;clamp();invalidate();}
                lastX=e.getX();lastY=e.getY();break;
            case MotionEvent.ACTION_POINTER_UP:
                int i=e.getActionIndex()==0?1:0;lastX=e.getX(i);lastY=e.getY(i);break;
            case MotionEvent.ACTION_UP:
                if(drawing) point(e.getX(),e.getY());commitGesture();
                if(!multiTouch&&mode==TEXT&&bitmap!=null&&textListener!=null) {
                    if(dragIndex>=0&&textMoved) {if(!textListener.save(TextBox.encode(boxes))) boxes.set(dragIndex,dragOriginal);}
                    else if(pageRect().contains(e.getX(),e.getY())) {float[] p=normalized(e.getX(),e.getY());textListener.edit(dragIndex>=0?boxes.get(dragIndex):null,p[0],p[1]);}
                }
                if(mode==SELECT&&selectionStart>=0&&selectionListener!=null) selectionListener.selected();dragIndex=-1;performClick();break;
            case MotionEvent.ACTION_CANCEL:
                drawing=false;active.clear();if(dragIndex>=0) boxes.set(dragIndex,dragOriginal);dragIndex=-1;invalidate();break;
        }
        return true;
    }
    @Override public boolean performClick() {super.performClick();return true;}
}
