package com.notepocket.app;

import android.graphics.*;
import android.view.View;

/** Reserves a real line box. Native, accessible cell views are laid over this exact box. */
final class InlineTableSpan extends InlineDiagramSpan {
    final View panel;final String id;
    InlineTableSpan(InlineNoteEditText owner,String id,View panel){super(owner,"table:"+id);this.id=id;this.panel=panel;if(android.os.Build.VERSION.SDK_INT>=30)setContentDescription("ตารางในเนื้อหาโน้ต");}
    @Override int height(){return Math.max(owner.dp(60),panel.getMeasuredHeight())+owner.dp(12);}
    @Override public void draw(Canvas c,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint p){}
}
