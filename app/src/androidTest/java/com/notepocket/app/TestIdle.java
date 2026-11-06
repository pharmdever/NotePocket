package com.notepocket.app;

import android.app.Activity;
import android.os.*;
import androidx.test.espresso.*;
import androidx.test.runner.lifecycle.*;

/** Waits for real asynchronous library/render/save work without fixed sleeps. */
public final class TestIdle implements IdlingResource {
    private ResourceCallback callback;private final Handler main=new Handler(Looper.getMainLooper());private boolean polling;
    private boolean idle() {for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) if(activity instanceof MainActivity&&!((MainActivity)activity).isWorkIdle()) return false;return true;}
    private final Runnable poll=new Runnable() {public void run() {if(idle()) {polling=false;if(callback!=null) callback.onTransitionToIdle();}else main.postDelayed(this,50);}};
    public String getName() {return "NotePocket background work";}
    public boolean isIdleNow() {boolean ready=idle();if(!ready&&!polling) {polling=true;main.postDelayed(poll,50);}return ready;}
    public void registerIdleTransitionCallback(ResourceCallback callback) {this.callback=callback;}
    public void close() {main.removeCallbacks(poll);IdlingRegistry.getInstance().unregister(this);}
    public static TestIdle register() {TestIdle resource=new TestIdle();IdlingRegistry.getInstance().register(resource);return resource;}
}
