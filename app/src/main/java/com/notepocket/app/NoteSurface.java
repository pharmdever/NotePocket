package com.notepocket.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One note page: native prose, native inline grids, then a transparent vector ink layer. */
final class NoteSurface extends ViewGroup {
    final InlineNoteEditText body;final NoteInkLayer ink;
    private int sizes;
    private final Map<String,View> panels=new LinkedHashMap<>();
    NoteSurface(Context c,InlineNoteEditText body,NoteInk model,Runnable before,Runnable changed){super(c);this.body=body;addView(body);ink=new NoteInkLayer(c,body,model,before,changed);addView(ink);setClipChildren(true);}
    void tables(Map<String,View> next){sizes=0;for(View panel:panels.values())removeView(panel);panels.clear();panels.putAll(next);for(View v:panels.values()){if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);addView(v,getChildCount()-1);}refreshTables();requestLayout();}
    void refreshTables(){Editable text=body.getText();for(InlineTableSpan s:text.getSpans(0,text.length(),InlineTableSpan.class))text.removeSpan(s);Set<String> placed=new HashSet<>();for(InlineTables.Block b:InlineTables.find(text.toString())){View panel=panels.get(b.id);if(panel!=null&&placed.add(b.id))text.setSpan(new InlineTableSpan(body,b.id,panel),b.start,b.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}body.requestLayout();invalidate();}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),inside=Math.max(1,w-body.getCompoundPaddingLeft()-body.getCompoundPaddingRight());for(View p:panels.values())p.measure(MeasureSpec.makeMeasureSpec(inside,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int hash=1;for(View p:panels.values())hash=31*hash+p.getMeasuredHeight();if(hash!=sizes){sizes=hash;boolean padding=body.getIncludeFontPadding();body.setIncludeFontPadding(!padding);body.setIncludeFontPadding(padding);body.forceLayout();}int minimum=body.dp(280);if(body.getMinHeight()!=minimum)body.setMinHeight(minimum);body.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int h=body.getMeasuredHeight()+body.dp(ink.model.space);if(ink.model.space>0)body.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));setMeasuredDimension(w,h);ink.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){body.layout(0,0,r-l,b-t);Set<View> assigned=new HashSet<>();Layout layout=body.getLayout();if(layout!=null)for(InlineTableSpan s:body.getText().getSpans(0,body.length(),InlineTableSpan.class)){int at=body.getText().getSpanStart(s);if(at<0)continue;int top=body.getTotalPaddingTop()+layout.getLineTop(layout.getLineForOffset(at))+body.dp(6),left=body.getCompoundPaddingLeft();s.panel.layout(left,top,left+s.panel.getMeasuredWidth(),top+s.panel.getMeasuredHeight());assigned.add(s.panel);}for(View p:panels.values())if(!assigned.contains(p))p.layout(0,0,0,0);ink.layout(0,0,r-l,b-t);}
}
