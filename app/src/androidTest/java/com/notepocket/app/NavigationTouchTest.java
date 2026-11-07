package com.notepocket.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Exercises real touch dispatch; performClick alone would miss child tooltip interception. */
@RunWith(AndroidJUnit4.class)
public class NavigationTouchTest {
    private TestIdle idle;
    @Before public void before() {idle=TestIdle.register();}
    @After public void after() {idle.close();}

    @Test public void iconCaptionAndPaddingAllSwitchTabs() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            for(int round=0;round<2;round++) for(int zone=0;zone<3;zone++) for(String label:new String[]{"เอกสาร","ตั้งค่า","โน้ต"}) {
                final int part=zone;
                onView(allOf(withContentDescription(label),isAssignableFrom(LinearLayout.class)))
                    .perform(new androidx.test.espresso.action.GeneralClickAction(
                        androidx.test.espresso.action.Tap.SINGLE,
                        view->point(view,part),androidx.test.espresso.action.Press.FINGER,
                        InputDevice.SOURCE_TOUCHSCREEN,MotionEvent.BUTTON_PRIMARY));
                destination(label);
            }
            onView(withText("ทุกความคิด อยู่ใกล้มือ")).check(doesNotExist());
            onView(withText("● ออฟไลน์")).check(doesNotExist());
            onView(withText("ออฟไลน์")).check(matches(isDisplayed()));
            screenshot("16-navigation-notes");
            // Re-selecting the active tab should not discard a search or rebuild the page.
            onView(withHint("ค้นหาในชื่อและเนื้อหา…")).perform(replaceText("keep active search"),closeSoftKeyboard());
            onView(allOf(withContentDescription("โน้ต"),isAssignableFrom(LinearLayout.class))).perform(click());
            onView(withHint("ค้นหาในชื่อและเนื้อหา…")).check(matches(withText("keep active search")));
        }
    }

    @Test public void tabsSwitchWhilePreviousSearchIsStillPending() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            destination("โน้ต");
            for(int round=0;round<4;round++) for(String label:new String[]{"เอกสาร","ตั้งค่า","โน้ต"}) {
                scenario.onActivity(activity->{
                    View decor=activity.getWindow().getDecorView();
                    EditText search=findSearch(decor);
                    if(search!=null) search.setText("pending search before tab switch");
                    View target=findTab(decor,label);assertNotNull(target);
                    float[] screen=point(target,0);int[] origin=new int[2];decor.getLocationOnScreen(origin);
                    long now=SystemClock.uptimeMillis();
                    MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,screen[0]-origin[0],screen[1]-origin[1],0);
                    MotionEvent up=MotionEvent.obtain(now,now+16,MotionEvent.ACTION_UP,screen[0]-origin[0],screen[1]-origin[1],0);
                    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    try {assertTrue(decor.dispatchTouchEvent(down));assertTrue(decor.dispatchTouchEvent(up));}finally {down.recycle();up.recycle();}
                });
                destination(label);
            }
            onView(allOf(withContentDescription("ตั้งค่า"),isAssignableFrom(LinearLayout.class))).perform(click());
            destination("ตั้งค่า");screenshot("17-navigation-settings");
            scenario.recreate();destination("ตั้งค่า");
            onView(allOf(withContentDescription("เอกสาร"),isAssignableFrom(LinearLayout.class))).perform(click());destination("เอกสาร");
            onView(allOf(withContentDescription("โน้ต"),isAssignableFrom(LinearLayout.class))).perform(click());destination("โน้ต");
        }
    }
    private static void destination(String label) {
        onView(allOf(withContentDescription(label),isAssignableFrom(LinearLayout.class))).check(matches(isSelected()));
        onView(withText(label.equals("โน้ต")?"＋ เขียนโน้ต":label.equals("เอกสาร")?"＋ นำเข้าไฟล์":"ข้อมูลของคุณอยู่กับคุณ")).check(matches(isDisplayed()));
    }
    private static float[] point(View slot,int zone) {
        View target=zone<2?((ViewGroup)slot).getChildAt(zone):slot;
        int[] at=new int[2];target.getLocationOnScreen(at);
        assertTrue(target.getWidth()>0&&target.getHeight()>0);
        return new float[]{at[0]+target.getWidth()*(zone==2?.12f:.5f),at[1]+target.getHeight()*(zone==2?.25f:.5f)};
    }
    private static View findTab(View view,String label) {
        if(view instanceof LinearLayout&&label.equals(view.getContentDescription())) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {View found=findTab(((ViewGroup)view).getChildAt(i),label);if(found!=null) return found;}return null;
    }
    private static EditText findSearch(View view) {
        if(view instanceof EditText&&((EditText)view).getHint()!=null&&((EditText)view).getHint().toString().startsWith("ค้นหา")) return (EditText)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {EditText found=findSearch(((ViewGroup)view).getChildAt(i));if(found!=null) return found;}return null;
    }
    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))) {image.compress(Bitmap.CompressFormat.PNG,100,out);}finally {image.recycle();}
    }
}
