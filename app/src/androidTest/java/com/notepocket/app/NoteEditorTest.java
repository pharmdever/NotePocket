package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NoteEditorTest {
    private TestIdle idle;
    @Before public void registerBackgroundWork() {idle=TestIdle.register();}
    @After public void unregisterBackgroundWork() {if(idle!=null) idle.close();}
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void richFormattingAndTablesSurviveBackupAndOldNotesStillOpen() throws Exception {
        context.deleteDatabase("editor-test.db");
        try(Store store=new Store(context,"editor-test.db")) {
            SpannableStringBuilder text=new SpannableStringBuilder("ข้อความไทย\nStudy plan");text.setSpan(new StyleSpan(Typeface.BOLD),0,10,33);text.setSpan(new UnderlineSpan(),0,4,33);text.setSpan(new StrikethroughSpan(),12,16,33);text.setSpan(new ForegroundColorSpan(0xffD12D39),0,4,33);text.setSpan(new BackgroundColorSpan(0xffFFF19A),0,4,33);text.setSpan(new AbsoluteSizeSpan(26,true),0,10,33);
            NoteTable table=new NoteTable(3,3);table.cells.get(0).set(0,"วันที่");table.cells.get(1).set(1,"ทบทวนบทเรียน\nทำโจทย์");List<NoteTable> tables=Arrays.asList(table);
            String rich=NoteFormat.encode(text);long note=store.save(-1,"editor-test",text.toString(),rich,"เรียน",NoteTable.encode(tables));ByteArrayOutputStream zip=new ByteArrayOutputStream();Backup.write(context,store,zip);Backup.restore(context,store,new ByteArrayInputStream(zip.toByteArray()));
            ContentValues copy=store.query("SELECT * FROM notes WHERE id<>?",""+note).get(0);assertEquals(NoteTable.encode(tables),copy.getAsString("tables"));SpannableStringBuilder loaded=NoteFormat.decode(copy.getAsString("rich"),copy.getAsString("body"));assertEquals(text.toString(),loaded.toString());assertEquals(26,loaded.getSpans(0,10,AbsoluteSizeSpan.class)[0].getSize());assertEquals(0xffD12D39,loaded.getSpans(0,4,ForegroundColorSpan.class)[0].getForegroundColor());assertEquals(1,loaded.getSpans(0,4,UnderlineSpan.class).length);assertEquals(1,loaded.getSpans(12,16,StrikethroughSpan.class).length);assertEquals(1,loaded.getSpans(0,4,BackgroundColorSpan.class).length);
            assertEquals("เก่า",NoteFormat.decode("<b>เก่า</b>","เก่า").toString());assertEquals(1,NoteFormat.decode("<b>เก่า</b>","เก่า").getSpans(0,3,StyleSpan.class).length);
            assertEquals("ทบทวนบทเรียน\nทำโจทย์",NoteTable.decode(copy.getAsString("tables")).get(0).cells.get(1).get(1));
            store.save(note,"edited","body","","เรียน");assertEquals(NoteTable.encode(tables),store.note(note).getAsString("tables"));
            try {NoteTable.decode("[{\"id\":\"bad\",\"cells\":[[\"a\"],[\"b\",\"c\"]]}]");fail("Ragged tables must fail");}catch(IllegalArgumentException expected) {}
        }finally {context.deleteDatabase("editor-test.db");}
    }
    @Test public void stickyIconToolbarGridUndoReopenAndKeyboardScrolling() throws Exception {
        androidx.test.espresso.Espresso.setFailureHandler((error,matcher)->{try {screenshot("editor-failure");}catch(Exception ignored) {}new androidx.test.espresso.base.DefaultFailureHandler(context).handle(error,matcher);});
        long id;try(Store store=new Store(context)) {id=store.save(-1,"เครื่องมือโน้ตรุ่นใหม่","Study notes\nแผนทบทวนบทเรียน","","เรียน");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withText("เครื่องมือโน้ตรุ่นใหม่")).perform(click());onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(click());awaitKeyboard(scenario);
            select(scenario,0,5);tool("ตัวหนา");tool("ขีดเส้นใต้");tool("สีข้อความ");onView(withText("แดง")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());tool("หัวข้อ");
            tool("แทรกตาราง");onView(withText("แทรก")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());onView(withContentDescription("ตาราง 1 แถว 1 คอลัมน์ 1")).perform(replaceText("รายการ"),closeSoftKeyboard());
            onView(withContentDescription("ตาราง 1 แถว 2 คอลัมน์ 1")).perform(scrollTo(),replaceText("ทบทวนบทเรียน"),closeSoftKeyboard());screenshot("07-note-table-icons");
            onView(withContentDescription("ตัวเลือกตาราง 1")).perform(scrollTo(),click());onView(withText("เพิ่มแถวด้านล่าง")).perform(click());
            tool("ย้อนกลับข้อความ");tool("ทำซ้ำข้อความ");onView(withContentDescription("กลับจากโน้ต")).perform(click());onView(withText("เครื่องมือโน้ตรุ่นใหม่")).perform(click());
            scenario.onActivity(a->{EditText body=(EditText)find(a.getWindow().getDecorView(),null,"เขียนสิ่งที่อยากจดจำ…");assertEquals(1,body.getText().getSpans(0,5,UnderlineSpan.class).length);assertEquals(26,body.getText().getSpans(0,5,AbsoluteSizeSpan.class)[0].getSize());});
            StringBuilder longText=new StringBuilder();for(int i=1;i<=65;i++) longText.append("บันทึกบรรทัดที่ ").append(i).append("\n");
            scenario.onActivity(a->{EditText b=(EditText)find(a.getWindow().getDecorView(),null,"เขียนสิ่งที่อยากจดจำ…");b.requestFocus();b.setText(longText.toString());b.setSelection(b.length());});
            scenario.onActivity(a->{EditText body=(EditText)find(a.getWindow().getDecorView(),null,"เขียนสิ่งที่อยากจดจำ…");body.requestFocus();body.setSelection(body.length());((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(body,0);});awaitKeyboard(scenario);
            assertKeyboardAndCursor(scenario);screenshot("08-note-keyboard-sticky");onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(closeSoftKeyboard());
            scenario.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(800);scenario.onActivity(a->{EditText b=(EditText)find(a.getWindow().getDecorView(),null,"เขียนสิ่งที่อยากจดจำ…");b.requestFocus();b.setSelection(b.length());b.append("บรรทัดสุดท้าย");((android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(b,0);});awaitKeyboard(scenario);assertKeyboardAndCursor(scenario);screenshot("09-note-landscape-keyboard");onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(closeSoftKeyboard());
        }
        try(Store store=new Store(context)) {List<NoteTable> tables=NoteTable.decode(store.note(id).getAsString("tables"));assertEquals(4,tables.get(0).cells.size());assertEquals("รายการ",tables.get(0).cells.get(0).get(0));assertEquals("ทบทวนบทเรียน",tables.get(0).cells.get(1).get(0));}
    }
    private void awaitKeyboard(ActivityScenario<MainActivity> scenario) throws Exception {
        // Orientation recreation may resume before the new window receives focus; IME requests then get ignored.
        for(int attempt=0;attempt<20;attempt++) {boolean[] visible={false};scenario.onActivity(a->{View decor=a.getWindow().getDecorView();EditText body=(EditText)find(decor,null,"เขียนสิ่งที่อยากจดจำ…");if(!decor.hasWindowFocus()) return;body.requestFocus();android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);ime.showSoftInput(body,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);android.view.WindowInsetsController controller=body.getWindowInsetsController();if(controller!=null) controller.show(WindowInsets.Type.ime());WindowInsets insets=decor.getRootWindowInsets();visible[0]=insets!=null&&insets.isVisible(WindowInsets.Type.ime());});if(visible[0]) {Thread.sleep(350);return;}Thread.sleep(250);}
        screenshot("editor-keyboard-failure");fail("Keyboard did not become visible after the recreated window received focus");
    }
    private void assertKeyboardAndCursor(ActivityScenario<MainActivity> scenario) {scenario.onActivity(a->{View decor=a.getWindow().getDecorView();WindowInsets insets=decor.getRootWindowInsets();assertNotNull(insets);assertTrue("Soft keyboard should be visible",insets.isVisible(WindowInsets.Type.ime()));View icon=find(decor,"ตัวหนา",null);View toolbar=(View)icon.getParent().getParent().getParent();int[] pos=new int[2];toolbar.getLocationOnScreen(pos);assertTrue("Toolbar must fit above keyboard",pos[1]+toolbar.getHeight()<=decor.getHeight()-insets.getInsets(WindowInsets.Type.ime()).bottom+4);EditText body=(EditText)find(decor,null,"เขียนสิ่งที่อยากจดจำ…");int[] b=new int[2];body.getLocationOnScreen(b);int y=b[1]+body.getTotalPaddingTop()+body.getLayout().getLineBottom(body.getLayout().getLineForOffset(body.getSelectionEnd()));assertTrue("Typed line must remain above tools",y<=pos[1]+4);});}
    private void select(ActivityScenario<MainActivity> scenario,int start,int end) {scenario.onActivity(a->{EditText body=(EditText)find(a.getWindow().getDecorView(),null,"เขียนสิ่งที่อยากจดจำ…");body.requestFocus();body.setSelection(start,end);});}
    private void tool(String description) {onView(withContentDescription(description)).perform(scrollTo(),click());}
    private static View find(View view,String description,String hint) {if(description!=null&&description.equals(view.getContentDescription())) return view;if(hint!=null&&view instanceof EditText&&hint.contentEquals(((EditText)view).getHint())) return view;if(view instanceof android.view.ViewGroup) {android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++) {View found=find(group.getChildAt(i),description,hint);if(found!=null) return found;}}return null;}
    private void screenshot(String name) throws Exception {Thread.sleep(400);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))) {image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
