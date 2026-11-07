package com.notepocket.app;
import android.content.*;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

final class SyncScheduler {
    static final String DONE="com.notepocket.notes.SYNC_DONE",ONCE="notepocket-drive-once",PERIODIC="notepocket-drive-periodic";
    static boolean enabled(Context c) {return c.getSharedPreferences("drive",0).getBoolean("auto",false);}
    private static Constraints constraints(Context c) {return new Constraints.Builder().setRequiredNetworkType(c.getSharedPreferences("drive",0).getBoolean("wifi",true)?NetworkType.UNMETERED:NetworkType.CONNECTED).build();}
    static void configure(Context c) {WorkManager manager=WorkManager.getInstance(c);if(!enabled(c)) {manager.cancelUniqueWork(ONCE);manager.cancelUniqueWork(PERIODIC);return;}manager.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.UPDATE,new PeriodicWorkRequest.Builder(DriveSyncWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints(c)).build());enqueue(c);}
    static void enqueue(Context c) {if(enabled(c)) WorkManager.getInstance(c).enqueueUniqueWork(ONCE,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(DriveSyncWorker.class).setInitialDelay(2,TimeUnit.SECONDS).setConstraints(constraints(c)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());}
}
