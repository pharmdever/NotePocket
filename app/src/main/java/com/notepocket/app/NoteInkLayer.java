package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.view.*;
import java.util.*;

/** Transparent, bitmap-free ink. Text mode passes every touch through to prose and grids. */
final class NoteInkLayer extends View {
    static final int TEXT=0,PEN=1,HIGHLIGHT=2,ERASE=3;
    NoteInk model;int mode=TEXT,color=0xff245BCC;float penDp=2.5f;
    final InlineNoteEditText body;final Runnable before,changed;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
    private final List<Float> pending=new ArrayList<>();private int anchor;private boolean gesture,erasing;private float lastX,lastY;
    NoteInkLayer(Context c,InlineNoteEditText body,NoteInk model,Runnable before,Runnable changed){super(c);this.body=body;this.model=model;this.before=before;this.changed=changed;setContentDescription("ชั้นลายมือบนโน้ต");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setWillNotDraw(false);}
    void mode(int value){mode=value;gesture=false;pending.clear();invalidate();}
    float width(){return Math.max(1,body.getWidth()-body.getCompoundPaddingLeft()-body.getCompoundPaddingRight());}
    float origin(int at){Layout l=body.getLayout();return body.getTotalPaddingTop()+(l==null?0:l.getLineTop(l.getLineForOffset(Math.max(0,Math.min(at,body.length())))));}
    void edit(int at,int before,int count){model.shift(at,before,count);invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);for(NoteInk.Stroke s:model.strokes)draw(c,s.anchor,s.color,s.width,s.highlight,s.xy);if(gesture&&!erasing&&pending.size()>=2){float[] xy=new float[pending.size()];for(int i=0;i<xy.length;i++)xy[i]=pending.get(i);draw(c,anchor,color,(mode==HIGHLIGHT?14:penDp)*getResources().getDisplayMetrics().density/width(),mode==HIGHLIGHT,xy);}}
    private void draw(Canvas c,int at,int color,float size,boolean highlight,float[] xy){float w=width(),left=body.getCompoundPaddingLeft(),top=origin(at);paint.setColor(color);paint.setAlpha(highlight?75:255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(Math.max(1,size*w));path.reset();for(int i=0;i<xy.length;i+=2){float x=left+xy[i]*w,y=top+xy[i+1]*w;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}if(xy.length==2){paint.setStyle(Paint.Style.FILL);c.drawCircle(left+xy[0]*w,top+xy[1]*w,paint.getStrokeWidth()/2,paint);}else c.drawPath(path,paint);}
    private void point(float x,float y){float w=width(),nx=Math.max(0,Math.min(1,(x-body.getCompoundPaddingLeft())/w)),ny=Math.max(-100,Math.min(100,(y-origin(anchor))/w));if(pending.size()<(erasing?512:4000)&&(pending.isEmpty()||Math.hypot(x-lastX,y-lastY)>=body.dp(erasing?6:1))){pending.add(nx);pending.add(ny);lastX=x;lastY=y;}}
    @Override public boolean onTouchEvent(MotionEvent e){if(mode==TEXT)return false;if(e.getPointerCount()>1){gesture=false;pending.clear();getParent().requestDisallowInterceptTouchEvent(false);invalidate();return false;}int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){if(body.getLayout()==null)return false;gesture=true;erasing=mode==ERASE||e.getToolType(0)==MotionEvent.TOOL_TYPE_ERASER;anchor=body.getLayout().getLineStart(body.getLayout().getLineForVertical(Math.max(0,(int)e.getY()-body.getTotalPaddingTop())));pending.clear();point(e.getX(),e.getY());getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}if(!gesture)return false;if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<e.getHistorySize();i++)point(e.getHistoricalX(i),e.getHistoricalY(i));point(e.getX(),e.getY());invalidate();return true;}if(action==MotionEvent.ACTION_UP){point(e.getX(),e.getY());gesture=false;getParent().requestDisallowInterceptTouchEvent(false);if(erasing)erase();else if(model.strokes.size()<NoteInk.MAX_STROKES&&model.points()+pending.size()/2<=NoteInk.MAX_POINTS){float[] xy=new float[pending.size()];for(int i=0;i<xy.length;i++)xy[i]=pending.get(i);before.run();model.strokes.add(new NoteInk.Stroke(anchor,color,(mode==HIGHLIGHT?14:penDp)*getResources().getDisplayMetrics().density/width(),mode==HIGHLIGHT,xy));changed.run();}else android.widget.Toast.makeText(getContext(),"ลายมือเต็มหน้านี้แล้ว กรุณาแยกโน้ต",android.widget.Toast.LENGTH_SHORT).show();pending.clear();invalidate();performClick();return true;}if(action==MotionEvent.ACTION_CANCEL){gesture=false;pending.clear();getParent().requestDisallowInterceptTouchEvent(false);invalidate();}return true;}
    private void erase(){List<NoteInk.Stroke> hit=new ArrayList<>();float w=width(),left=body.getCompoundPaddingLeft();for(NoteInk.Stroke s:model.strokes){float top=origin(s.anchor),limit=body.dp(10)+s.width*w/2;boolean found=false;for(int er=0;er<pending.size()&&!found;er+=2){float x=left+pending.get(er)*w,y=origin(anchor)+pending.get(er+1)*w,ax=left+s.xy[0]*w,ay=top+s.xy[1]*w;found=Math.hypot(x-ax,y-ay)<=limit;for(int i=2;i<s.xy.length&&!found;i+=2){float bx=left+s.xy[i]*w,by=top+s.xy[i+1]*w,dx=bx-ax,dy=by-ay,t=dx*dx+dy*dy==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));found=Math.hypot(x-ax-t*dx,y-ay-t*dy)<=limit;ax=bx;ay=by;}}if(found)hit.add(s);}if(!hit.isEmpty()){before.run();model.strokes.removeAll(hit);changed.run();}}

    @Override public boolean performClick(){super.performClick();return true;}
}
