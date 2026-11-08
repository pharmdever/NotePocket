package com.notepocket.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class InlineDiagramTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private TestIdle idle;
    @Before public void before(){idle=TestIdle.register();}
    @After public void after(){idle.close();}
    private String original(){return "อธิบายก่อนแผนภาพ\n"+DiagramSource.fenced("flowchart TD\n A[เริ่มต้น] --> B[จดโน้ต]\n B --> C[ทบทวน]")+"\nพิมพ์ต่อด้านล่างได้\n";}
    private long seed(String title,String body){try(Store store=new Store(context)){return store.save(-1,title,body,"","ทั่วไป");}}
    @Test public void rendersOneImageBetweenParagraphsAndRebuildsFromSourceAfterCacheClear() throws Exception {
        String source=original(),code=DiagramSource.find(source).get(0).code;DiagramCache.file(context,DiagramCache.key(code)).delete();long note=seed("Inline diagram visual test",source);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Inline diagram visual test")).perform(click());awaitImages(scenario,1);
            scenario.onActivity(a->{InlineNoteEditText body=find(a.getWindow().getDecorView());assertEquals(source,body.getText().toString());assertEquals(1,body.spans().length);int first=body.getLayout().getLineForOffset(0),after=body.getLayout().getLineForOffset(source.indexOf("พิมพ์ต่อ"));int delta=body.getLayout().getLineTop(after)-body.getLayout().getLineTop(first);int expected=body.getLayout().getLineBottom(first)-body.getLayout().getLineTop(first)+body.spans()[0].height();assertEquals("Text below must start after the complete image",expected,delta,body.dp(3));assertEquals(0,NoteFormat.snapshot(body.getText()).getSpans(0,body.length(),InlineDiagramSpan.class).length);assertFalse(NoteFormat.encode(body.getText()).contains("InlineDiagramSpan"));});
            onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(closeSoftKeyboard());screenshot("21-inline-diagram-between-paragraphs");scenario.recreate();awaitImages(scenario,1);onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        DiagramCache.file(context,DiagramCache.key(code)).delete();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Inline diagram visual test")).perform(click());awaitImages(scenario,1);scenario.onActivity(a->assertEquals(source,find(a.getWindow().getDecorView()).getText().toString()));onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        try(Store store=new Store(context)){assertEquals(source,store.note(note).getAsString("body"));ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(context,store,bytes);Backup.restore(context,store,new ByteArrayInputStream(bytes.toByteArray()));boolean restored=false;for(ContentValues n:store.query("SELECT id,body FROM notes WHERE title=?","Inline diagram visual test"))if(n.getAsLong("id")!=note&&source.equals(n.getAsString("body")))restored=true;assertTrue("Backup restores source without depending on image cache",restored);}
    }
    @Test public void insertsAtCursorEvenWithExistingDiagramThenTypingAndUndoKeepBothSides() throws Exception {
        String source=original();seed("Inline insert position test",source);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Inline insert position test")).perform(click());awaitImages(scenario,1);int at=source.indexOf("พิมพ์ต่อ");scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());b.requestFocus();b.setSelection(at);});
            onView(withContentDescription("แทรกแผนภาพตรงเคอร์เซอร์")).perform(scrollTo(),click());awaitDiagram();onView(withContentDescription("โค้ด Mermaid")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("")));String added="sequenceDiagram\n Alice->>Bob: สวัสดี";onView(withContentDescription("โค้ด Mermaid")).perform(replaceText(added),closeSoftKeyboard());onView(withContentDescription("บันทึกแผนภาพในโน้ต")).perform(click());awaitImages(scenario,2);
            String expected=source.substring(0,at)+DiagramSource.fenced(added)+"\n"+source.substring(at);
            scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());assertEquals(expected,b.getText().toString());int tail=b.length();b.setSelection(tail);b.getText().insert(tail,"ข้อความที่จดเพิ่ม");});onView(withContentDescription("ย้อนกลับข้อความ")).perform(click());scenario.onActivity(a->assertEquals(expected,find(a.getWindow().getDecorView()).getText().toString()));onView(withContentDescription("ย้อนกลับข้อความ")).perform(click());scenario.onActivity(a->assertEquals(source,find(a.getWindow().getDecorView()).getText().toString()));onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
    }
    @Test public void atomicSelectionAndDeletionProtectSourceAndImageCanBeEditedOrRemoved() throws Exception {
        String source=original();seed("Inline atomic edit test",source);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Inline atomic edit test")).perform(click());awaitImages(scenario,1);
            scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());InlineDiagramSpan s=b.spans()[0];int start=b.getText().getSpanStart(s),end=b.getText().getSpanEnd(s);b.setSelection(start+5,end-2);assertEquals(start,b.getSelectionStart());assertEquals(end,b.getSelectionEnd());b.getText().delete(end,end+1);assertEquals("Backspace cannot break a diagram fence",source,b.getText().toString());});
            // Tap the actual inline image, rather than a card elsewhere in the editor.
            onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(closeSoftKeyboard());scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());b.clearFocus();});tapDiagram(scenario);onView(withText("ลบแผนภาพ")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());onView(withText("ยืนยัน")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());assertTrue(b.getText().toString().contains("อธิบายก่อนแผนภาพ"));assertTrue(b.getText().toString().contains("พิมพ์ต่อด้านล่างได้"));assertTrue(DiagramSource.find(b.getText().toString()).isEmpty());});onView(withContentDescription("ย้อนกลับข้อความ")).perform(click());awaitImages(scenario,1);scenario.onActivity(a->assertEquals(source,find(a.getWindow().getDecorView()).getText().toString()));onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
    }
    private void awaitImages(ActivityScenario<MainActivity> scenario,int count)throws Exception{long end=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<end){AtomicBoolean ready=new AtomicBoolean();scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());if(b==null)return;InlineDiagramSpan[] spans=b.spans();boolean ok=spans.length==count&&b.isIdle();for(InlineDiagramSpan s:spans)ok&=b.bitmap(s.key)!=null;ready.set(ok);});if(ready.get())return;Thread.sleep(150);}fail("Inline images were not rendered");}
    private void tapDiagram(ActivityScenario<MainActivity> scenario)throws Exception{AtomicReference<float[]> point=new AtomicReference<>();scenario.onActivity(a->{InlineNoteEditText b=find(a.getWindow().getDecorView());InlineDiagramSpan s=b.spans()[0];int line=b.getLayout().getLineForOffset(b.getText().getSpanStart(s));int[] p=new int[2];b.getLocationOnScreen(p);point.set(new float[]{p[0]+b.getWidth()/2f,p[1]+b.getTotalPaddingTop()+(b.getLayout().getLineTop(line)+b.getLayout().getLineBottom(line))/2f});});float[] p=point.get();long t=SystemClock.uptimeMillis();android.app.UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();android.view.MotionEvent down=android.view.MotionEvent.obtain(t,t,android.view.MotionEvent.ACTION_DOWN,p[0],p[1],0),up=android.view.MotionEvent.obtain(t,t+80,android.view.MotionEvent.ACTION_UP,p[0],p[1],0);try{ui.injectInputEvent(down,true);ui.injectInputEvent(up,true);}finally{down.recycle();up.recycle();}Thread.sleep(250);}
    private DiagramActivity awaitDiagram()throws Exception{long end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){AtomicReference<DiagramActivity> found=new AtomicReference<>();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof DiagramActivity)found.set((DiagramActivity)a);});if(found.get()!=null)return found.get();Thread.sleep(100);}throw new AssertionError("Diagram editor not resumed");}
    private static InlineNoteEditText find(View v){if(v instanceof InlineNoteEditText)return(InlineNoteEditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){InlineNoteEditText found=find(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private void screenshot(String name)throws Exception{Thread.sleep(250);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
