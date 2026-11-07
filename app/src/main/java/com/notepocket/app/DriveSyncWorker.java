package com.notepocket.app;
import android.accounts.Account;
import android.content.*;
import androidx.work.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public final class DriveSyncWorker extends Worker {
    private final SyncCancel cancel=new SyncCancel();
    public DriveSyncWorker(Context context,WorkerParameters parameters) {super(context,parameters);}
    @Override public Result doWork() {
        String usedToken=null;Context c=getApplicationContext();if(!SyncScheduler.enabled(c)||!SyncRuntime.RUNNING.compareAndSet(false,true)) return Result.success();
        try(Store store=new Store(c)) {SyncEngine engine=new SyncEngine(c,store,cancel,m->{});if(!engine.config("connected").equals("1")||engine.config("email").isEmpty()) return Result.success();synchronized(SyncRuntime.EDIT_LOCK) {SyncRuntime.checkEditor();}
            AuthorizationRequest request=AuthorizationRequest.builder().setAccount(new Account(engine.config("email"),"com.google")).setRequestedScopes(Collections.singletonList(new Scope(DriveRemote.SCOPE))).build();AuthorizationResult access=Tasks.await(Identity.getAuthorizationClient(c).authorize(request),30,TimeUnit.SECONDS);
            if(access.hasResolution()||access.getAccessToken()==null||!access.getGrantedScopes().contains(DriveRemote.SCOPE)) {engine.config("background_status","ต้องกดเชื่อมบัญชีอีกครั้ง");return Result.success();}
            usedToken=access.getAccessToken();engine.config("background_status",engine.sync(new DriveRemote(usedToken,cancel)));c.sendBroadcast(new Intent(SyncScheduler.DONE).setPackage(c.getPackageName()));return Result.success();
        }catch(SyncRuntime.Deferred e) {return Result.success();}catch(DriveRemote.Failure e) {if(e.code==401&&usedToken!=null) try {Tasks.await(Identity.getAuthorizationClient(c).clearToken(ClearTokenRequest.builder().setToken(usedToken).build()),10,TimeUnit.SECONDS);}catch(Exception ignored) {}return e.code==401||e.code==403?Result.success():Result.retry();}catch(Exception|OutOfMemoryError e) {return isStopped()?Result.success():Result.retry();}finally {SyncRuntime.RUNNING.set(false);}
    }
    @Override public void onStopped() {cancel.cancel();}
}
