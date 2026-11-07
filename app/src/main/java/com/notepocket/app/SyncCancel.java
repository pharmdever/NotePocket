package com.notepocket.app;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
final class SyncCancel {
    private volatile boolean cancelled;private volatile HttpURLConnection connection;
    void check() throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Sync cancelled");}
    void connection(HttpURLConnection value) throws InterruptedIOException {connection=value;check();}
    void cancel() {cancelled=true;HttpURLConnection c=connection;if(c!=null) c.disconnect();}
}
