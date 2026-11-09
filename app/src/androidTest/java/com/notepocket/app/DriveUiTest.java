package com.notepocket.app;
import android.content.*;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class DriveUiTest {
    private TestIdle idle;
    @Before public void before() {idle=TestIdle.register();}
    @After public void after() {idle.close();}
    private void dismissClipboardOverlay() throws Exception {
        android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();int flags=info.flags;info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        try {for(int attempt=0;attempt<20;attempt++) {for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()) {android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root==null) continue;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/dismiss_button")) if(node.isVisibleToUser()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {System.out.println("NOTEPOCKET_CLIPBOARD_OVERLAY:dismissed");InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;}}Thread.sleep(50);}System.out.println("NOTEPOCKET_CLIPBOARD_OVERLAY:not-present");}finally {info.flags=flags;automation.setServiceInfo(info);}
    }
    private void awaitMain() throws Exception {
        int stable=0;for(int i=0;i<200;i++) {java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) if(a instanceof MainActivity&&a.getWindow().getDecorView().isShown()&&a.hasWindowFocus()) ready.set(true);});if(ready.get()) {if(++stable>=3){InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;}}else stable=0;Thread.sleep(50);}
        fail("Main screen must resume after closing the Drive screen");
    }
    @Test public void offlineUserCanOpenSetupCopyPublicFingerprintAndReturnToNotes() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withText("ตั้งค่า")).perform(click());onView(withText("เชื่อมบัญชีและซิงก์ Drive")).perform(scrollTo(),click());onView(withText("ใช้งานในเครื่องได้ตามเดิม")).check(matches(isDisplayed()));onView(withText("เชื่อมบัญชี Google")).check(matches(isDisplayed()));
            Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,"18-drive-settings.png"))) {image.compress(Bitmap.CompressFormat.PNG,100,out);}finally {image.recycle();}
            onView(withText("คัดลอก Package และ SHA-1")).perform(scrollTo(),click());InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);String copied=clipboard.getPrimaryClip().getItemAt(0).getText().toString();assertTrue(copied.startsWith(context.getPackageName()+"\n"));assertTrue(copied.substring(copied.indexOf('\n')+1).matches("([0-9A-F]{2}:){19}[0-9A-F]{2}"));});
            dismissClipboardOverlay();onView(withContentDescription("กลับจาก Google Drive")).perform(click());awaitMain();onView(withText("โน้ต")).perform(click());awaitMain();onView(withText("＋ เขียนโน้ต")).check(matches(isDisplayed()));
        }
    }
}
