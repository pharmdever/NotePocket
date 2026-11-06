package com.notepocket.app;

import java.util.*;

/** Bounded editor history. Consecutive typing is grouped; explicit tools are separate steps. */
public final class NoteHistory {
    public static final class State {
        public final String title,rich,tables,soap;public final int start,end;
        public State(String title,String rich,String tables,String soap,int start,int end) {this.title=title;this.rich=rich;this.tables=tables;this.soap=soap;this.start=start;this.end=end;}
        boolean same(State other) {return title.equals(other.title)&&rich.equals(other.rich)&&tables.equals(other.tables)&&soap.equals(other.soap);}
    }
    private final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();private long lastTyping;
    public void remember(State current,boolean typing) {long now=android.os.SystemClock.uptimeMillis();if(!typing||now-lastTyping>750||undo.isEmpty()) {if(undo.isEmpty()||!undo.peekLast().same(current)) {undo.addLast(current);if(undo.size()>50) undo.removeFirst();}}lastTyping=typing?now:0;redo.clear();}
    public boolean canUndo() {return !undo.isEmpty();}public boolean canRedo() {return !redo.isEmpty();}
    public State undo(State current) {if(undo.isEmpty()) return null;redo.addLast(current);lastTyping=0;return undo.removeLast();}
    public State redo(State current) {if(redo.isEmpty()) return null;undo.addLast(current);lastTyping=0;return redo.removeLast();}
}
