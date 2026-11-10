package com.notepocket.app;

import android.text.Spanned;
import java.util.*;
import java.util.function.Supplier;

/** Lazy, grouped history with both entry and estimated-memory limits. */
public final class NoteHistory {
    public static final long MAX_BYTES=4L*1024*1024;
    public static final class State {
        public final String title,soap,ink;public final Spanned body;public final List<NoteTable> tables;public final int start,end;final long bytes;
        public State(String title,Spanned body,List<NoteTable> tables,String soap,int start,int end) {this(title,body,tables,soap,start,end,new NoteInk().json());}
        public State(String title,Spanned body,List<NoteTable> tables,String soap,int start,int end,String ink) {this.ink=ink;this.title=title;this.body=NoteFormat.snapshot(body);this.tables=NoteTable.copy(tables);this.soap=soap;this.start=start;this.end=end;long size=1024L+2L*(title.length()+body.length()+soap.length()+ink.length())+64L*body.getSpans(0,body.length(),Object.class).length;for(NoteTable t:tables) for(List<String> row:t.cells) for(String cell:row) size+=64L+cell.length()*2L;bytes=size;}
    }
    private final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();private long lastTyping,bytes;
    public void remember(Supplier<State> capture,boolean typing) {long now=android.os.SystemClock.uptimeMillis();boolean boundary=!typing||lastTyping==0||now-lastTyping>750;lastTyping=typing?now:0;for(State state:redo) bytes-=state.bytes;redo.clear();if(boundary) {State state=capture.get();if(state.bytes>MAX_BYTES) {undo.clear();bytes=0;return;}undo.addLast(state);bytes+=state.bytes;trim();}}
    private void trim() {while((undo.size()>50||bytes>MAX_BYTES)&&!undo.isEmpty()) bytes-=undo.removeFirst().bytes;while(bytes>MAX_BYTES&&!redo.isEmpty()) bytes-=redo.removeFirst().bytes;}
    public long retainedBytes() {return bytes;}
    public boolean canUndo() {return !undo.isEmpty();}public boolean canRedo() {return !redo.isEmpty();}
    public State undo(State current) {if(undo.isEmpty()) return null;State state=undo.removeLast();bytes-=state.bytes;redo.addLast(current);bytes+=current.bytes;lastTyping=0;trim();return state;}
    public State redo(State current) {if(redo.isEmpty()) return null;State state=redo.removeLast();bytes-=state.bytes;undo.addLast(current);bytes+=current.bytes;lastTyping=0;trim();return state;}
    public void breakTyping() {lastTyping=0;}
    public void clear() {undo.clear();redo.clear();bytes=0;lastTyping=0;}
}
