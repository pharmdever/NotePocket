package com.notepocket.app;

import android.os.*;
import java.util.concurrent.*;

/** A single ordered writer, with at most one in-flight and one latest pending snapshot. */
public final class EditorSaver implements AutoCloseable {
    public interface Listener {void completed(long revision,long id,Exception failure);}
    private final Store store;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"NotePocket-save"));
    private Work pending;private boolean running,closed;private long ticket,completedTicket;private Exception lastFailure;
    public volatile long noteId;
    private static final class Work {final long ticket;final NoteSnapshot snapshot;Work(long ticket,NoteSnapshot snapshot) {this.ticket=ticket;this.snapshot=snapshot;}}
    public EditorSaver(Store store,long id,Listener listener) {this.store=store;noteId=id;this.listener=listener;}
    public synchronized long offer(NoteSnapshot snapshot) {if(closed) throw new IllegalStateException("Saver closed");long next=++ticket;pending=new Work(next,snapshot);if(!running) {running=true;worker.execute(this::drain);}return next;}
    private void drain() {
        while(true) {Work work;synchronized(this) {work=pending;pending=null;if(work==null) {running=false;notifyAll();return;}}
            Exception failure=null;try {noteId=store.saveSnapshot(noteId,work.snapshot);}catch(Exception e) {failure=e;}
            Exception result=failure;long saved=noteId;synchronized(this) {lastFailure=failure;completedTicket=work.ticket;notifyAll();}
            main.post(()->{if(listener!=null) listener.completed(work.snapshot.revision,saved,result);});
        }
    }
    public boolean flush(NoteSnapshot snapshot) {long wanted=offer(snapshot);synchronized(this) {while(completedTicket<wanted) {try {wait();}catch(InterruptedException e) {Thread.currentThread().interrupt();return false;}}return lastFailure==null;}}
    public synchronized boolean isRunning() {return running;}
    @Override public void close() {synchronized(this) {closed=true;}worker.shutdown();}
}
