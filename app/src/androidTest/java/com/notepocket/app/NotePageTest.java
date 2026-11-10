package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

@RunWith(AndroidJUnit4.class)
public class NotePageTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();TestIdle idle;
    @Before public void before(){idle=TestIdle.register();}
    @After public void after(){if(idle!=null)idle.close();}
    @Test public void nativeGridLivesBetweenParagraphsEditsReopensAndMovesWithText() throws Exception {
        long id;try(Store store=new Store(context)){id=store.save(-1,"Inline native table","ก่อน\nหลัง","","ทั่วไป");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Inline native table")).perform(click());scenario.onActivity(a->{EditText b=body(a);b.requestFocus();b.setSelection("ก่อน\n".length());});tool("แทรกตาราง");onView(withText("แทรก")).inRoot(isDialog()).perform(click());onView(withContentDescription("ตาราง 1 แถว 1 คอลัมน์ 1")).perform(replaceText("รายการ"),closeSoftKeyboard());onView(withContentDescription("ตาราง 1 แถว 2 คอลัมน์ 2")).perform(scrollTo(),replaceText("ค่าที่แก้ในหน้า"),closeSoftKeyboard());
            scenario.onActivity(a->{InlineNoteEditText b=body(a);NoteSurface surface=(NoteSurface)b.getParent();InlineTableSpan[] spans=b.getText().getSpans(0,b.length(),InlineTableSpan.class);assertEquals(1,spans.length);assertSame(surface,spans[0].panel.getParent());InlineTables.Block block=InlineTables.find(b.getText().toString()).get(0);assertTrue(b.getText().toString().startsWith("ก่อน\n"));assertTrue(b.getText().toString().endsWith("\nหลัง"));int after=b.getTotalPaddingTop()+b.getLayout().getLineTop(b.getLayout().getLineForOffset(block.end+1));assertTrue("Following prose must be below the editable table",after>=spans[0].panel.getBottom());assertTrue(spans[0].panel.getHeight()>b.dp(150));b.getText().insert(0,"เพิ่มบรรทัดก่อน\n");});
            onView(withContentDescription("ตาราง 1 แถว 1 คอลัมน์ 1")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("รายการ")));screenshot("27-native-inline-table");onView(withContentDescription("กลับจากโน้ต")).perform(click());onView(withText("Inline native table")).perform(click());scenario.recreate();onView(withContentDescription("ตาราง 1 แถว 2 คอลัมน์ 2")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("ค่าที่แก้ในหน้า")));
        }
        try(Store store=new Store(context)){ContentValues n=store.note(id);List<NoteTable> tables=NoteTable.decode(n.getAsString("tables"));assertTrue(tables.get(0).inline);assertEquals("ค่าที่แก้ในหน้า",tables.get(0).cells.get(1).get(1));assertTrue(NoteTable.prose(n.getAsString("body"),tables).contains("รายการ"));assertFalse(NoteTable.prose(n.getAsString("body"),tables).contains("notepocket-table:"));}
    }
    @Test public void physicalStylusAndFingerInkUndoEraseAnchorsRecreationAndBackup() throws Exception {
        long id;try(Store store=new Store(context)){id=store.save(-1,"Layered handwriting","เขียนทับข้อความนี้\nบรรทัดที่สอง\n","","ทั่วไป");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("Layered handwriting")).perform(click());onView(withContentDescription("สลับโหมดเขียนบนโน้ต")).perform(click());onView(withText("ปากกาเขียนทับข้อความ")).inRoot(isDialog()).perform(click());stroke(scenario,MotionEvent.TOOL_TYPE_STYLUS,0);scenario.onActivity(a->{assertEquals(1,surface(a).ink.model.strokes.size());assertEquals("เขียนทับข้อความนี้\nบรรทัดที่สอง\n",body(a).getText().toString());});tool("ย้อนกลับข้อความ");scenario.onActivity(a->assertEquals(0,surface(a).ink.model.strokes.size()));tool("ทำซ้ำข้อความ");stroke(scenario,MotionEvent.TOOL_TYPE_FINGER,35);scenario.onActivity(a->assertEquals(2,surface(a).ink.model.strokes.size()));screenshot("28-ink-over-note-text");
            onView(withContentDescription("สลับโหมดเขียนบนโน้ต")).perform(click());onView(withText("ยางลบเส้นลายมือ")).inRoot(isDialog()).perform(click());stroke(scenario,MotionEvent.TOOL_TYPE_FINGER,0);scenario.onActivity(a->assertEquals(1,surface(a).ink.model.strokes.size()));tool("ย้อนกลับข้อความ");scenario.onActivity(a->assertEquals(2,surface(a).ink.model.strokes.size()));
            onView(withContentDescription("สลับโหมดเขียนบนโน้ต")).perform(click());onView(withText("พิมพ์ / เลื่อนหน้า")).inRoot(isDialog()).perform(click());float[] position={0};scenario.onActivity(a->{NoteSurface s=surface(a);NoteInk.Stroke ink=s.ink.model.strokes.get(0);position[0]=s.ink.origin(ink.anchor);body(a).getText().insert(0,"บรรทัดใหม่\n");});onView(withContentDescription("สลับโหมดเขียนบนโน้ต")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));scenario.onActivity(a->{NoteSurface s=surface(a);assertEquals("บรรทัดใหม่\n".length(),s.ink.model.strokes.get(0).anchor);assertTrue(s.ink.origin(s.ink.model.strokes.get(0).anchor)>position[0]);float before=s.ink.origin(s.ink.model.strokes.get(0).anchor);body(a).append("ท้ายโน้ต\n".repeat(10));assertEquals(before,s.ink.origin(s.ink.model.strokes.get(0).anchor),1);});scenario.recreate();scenario.onActivity(a->assertEquals(2,surface(a).ink.model.strokes.size()));onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        try(Store store=new Store(context)){assertEquals(2,NoteInk.fromRich(store.note(id).getAsString("rich")).strokes.size());ByteArrayOutputStream out=new ByteArrayOutputStream();Backup.write(context,store,out);int count=Backup.restore(context,store,new ByteArrayInputStream(out.toByteArray()));assertTrue(count>0);ContentValues restored=store.query("SELECT * FROM notes WHERE title=? AND id<>?","Layered handwriting",""+id).get(0);assertEquals(2,NoteInk.fromRich(restored.getAsString("rich")).strokes.size());}
    }
    @Test public void extraWritingSpaceExtendsLongNotesWithoutBitmapOrCumulativeGrowth(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{InlineNoteEditText b=new InlineNoteEditText(context);b.setPadding(12,12,12,12);b.setText("long paragraph\n".repeat(80));NoteInk ink=new NoteInk();NoteSurface surface=new NoteSurface(context,b,ink,()->{},()->{});int width=View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),height=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);surface.measure(width,height);int initial=surface.getMeasuredHeight();ink.space=320;surface.requestLayout();surface.measure(width,height);assertEquals(initial+b.dp(320),surface.getMeasuredHeight());surface.requestLayout();surface.measure(width,height);assertEquals(initial+b.dp(320),surface.getMeasuredHeight());b.close();});}
    @Test public void inkAndTableMetadataFreezeValidateAndSurvivePhotoReferenceRemapping() throws Exception {
        NoteInk ink=new NoteInk();ink.space=320;String image=InlineImages.marker("old-photo"),plain="前\n"+image+"\nหลัง";int after=plain.indexOf("หลัง");ink.strokes.add(new NoteInk.Stroke(after,Color.RED,.004f,false,new float[]{.1f,.1f,.5f,.2f}));String rich=NoteInk.rich(NoteFormat.encode(new SpannableString(plain)),ink);InlineImages.Mapped mapped=InlineImages.remap(plain,rich,Collections.singletonMap("old-photo","a-longer-photo-reference"));NoteInk restored=NoteInk.fromRich(mapped.rich);assertEquals(mapped.body.indexOf("หลัง"),restored.strokes.get(0).anchor);assertEquals(320,restored.space);NoteHistory.State state=new NoteHistory.State("title",new SpannableString(plain),Collections.emptyList(),"{}",0,0,ink.json());ink.shift(0,0,100);assertEquals(after,NoteInk.parse(state.ink).strokes.get(0).anchor);NoteInk.validateRich(mapped.rich);
        try{NoteInk.parse("{\"v\":1,\"space\":999999,\"strokes\":[]}");fail("space bound");}catch(IllegalArgumentException expected){}try{new NoteInk.Stroke(0,Color.BLACK,.01f,false,new float[]{2,0});fail("point bound");}catch(IllegalArgumentException expected){}
        NoteTable table=new NoteTable(2,2);table.inline=true;table.cells.get(0).set(0,"hello");assertTrue(NoteTable.decode(NoteTable.encode(Arrays.asList(table))).get(0).inline);assertEquals(0,InlineTables.find("```\n"+InlineTables.marker(table.id)+"\n```").size());assertEquals(1,InlineTables.find("before\n"+InlineTables.marker(table.id)+"\nafter").size());assertEquals("before\nตาราง\nafter",LibraryPage.preview("before\n"+InlineTables.marker(table.id)+"\nafter"));
    }
    private void stroke(ActivityScenario<MainActivity> scenario,int tool,int delta)throws Exception{float[] xy=new float[4];scenario.onActivity(a->{NoteSurface s=surface(a);int[] pos=new int[2];s.ink.getLocationOnScreen(pos);xy[0]=pos[0]+body(a).getCompoundPaddingLeft()+body(a).dp(20);xy[1]=pos[1]+body(a).getTotalPaddingTop()+body(a).dp(14+delta);xy[2]=xy[0]+body(a).dp(120);xy[3]=xy[1]+body(a).dp(3);});long now=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=tool;MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.x=xy[0]+(xy[2]-xy[0])*i/12;c.y=xy[1]+(xy[3]-xy[1])*i/12;c.pressure=1;c.size=.1f;MotionEvent event=MotionEvent.obtain(now,now+i*16,i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,tool==MotionEvent.TOOL_TYPE_STYLUS?android.view.InputDevice.SOURCE_STYLUS:android.view.InputDevice.SOURCE_TOUCHSCREEN,0);InstrumentationRegistry.getInstrumentation().sendPointerSync(event);event.recycle();}InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    static InlineNoteEditText body(MainActivity a){return (InlineNoteEditText)TypingStyleTest.body(a);}static NoteSurface surface(MainActivity a){return (NoteSurface)body(a).getParent();}
    static void tool(String text){onView(withContentDescription(text)).perform(scrollTo(),click());}
    void screenshot(String name)throws Exception{Thread.sleep(300);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
