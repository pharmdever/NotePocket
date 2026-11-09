package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.*;
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
public class TypingStyleTest {
    final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    TestIdle idle;SharedPreferences prefs;String oldStyle,oldPresets;
    @Before public void before(){idle=TestIdle.register();prefs=context.getSharedPreferences("reader",0);oldStyle=prefs.getString("noteTypingStyle",null);oldPresets=prefs.getString(StylePresets.KEY,null);prefs.edit().remove("noteTypingStyle").remove(StylePresets.KEY).commit();}
    @After public void after(){if(prefs!=null)prefs.edit().putString("noteTypingStyle",oldStyle).putString(StylePresets.KEY,oldPresets).commit();if(idle!=null)idle.close();}
    @Test public void chooseBeforeTypingHandlesThaiCompositionSizeToggleClearAndUndo() throws Exception {
        long id;try(Store store=new Store(context)){id=store.save(-1,"typing-style-ime","เดิม\n","","ทั่วไป");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("typing-style-ime")).perform(click());scenario.onActivity(a->{EditText b=body(a);b.requestFocus();b.setSelection(b.length());});
            tool("สีข้อความ");onView(withText("แดง")).inRoot(isDialog()).perform(click());tool("ขนาดข้อความ");onView(withText("ใหญ่ 32")).inRoot(isDialog()).perform(click());tool("ตัวหนา");
            scenario.onActivity(a->{EditText b=body(a);InputConnection ic=b.onCreateInputConnection(new EditorInfo());ic.setComposingText("ก",1);ic.setComposingText("กำ",1);ic.setComposingText("กำลัง",1);ic.finishComposingText();ic.commitText("พิมพ์\nต่อ",1);Spanned s=b.getText();assertEquals("เดิม\nกำลังพิมพ์\nต่อ",s.toString());assertEquals(0,s.getSpans(0,3,ForegroundColorSpan.class).length);assertStyle(s,4,s.length(),0xffD12D39,32,1);});
            tool("ตัวหนา");scenario.onActivity(a->{EditText b=body(a);int at=b.length();b.onCreateInputConnection(new EditorInfo()).commitText("ธรรมดา",1);assertStyle(b.getText(),at,b.length(),0xffD12D39,32,0);});
            tool("ล้างรูปแบบ");scenario.onActivity(a->{EditText b=body(a);int at=b.length();b.onCreateInputConnection(new EditorInfo()).commitText("ปกติ",1);assertStyle(b.getText(),at,b.length(),PocketTheme.INK,18,0);});
            screenshot("25-typing-style-before-selection");tool("ย้อนกลับข้อความ");tool("ทำซ้ำข้อความ");onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        try(Store store=new Store(context)){ContentValues n=store.note(id);Spanned s=NoteFormat.decode(n.getAsString("rich"),n.getAsString("body"));assertTrue(s.toString().contains("กำลังพิมพ์"));assertStyle(s,4,6,0xffD12D39,32,1);}
    }
    @Test public void namedPresetAppliesToNewTextAndSelectionSurvivesRecreationAndCanRenameDelete() throws Exception {
        try(Store store=new Store(context)){store.save(-1,"typing-style-presets","ก่อน\n","","ทั่วไป");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            onView(withText("typing-style-presets")).perform(click());scenario.onActivity(a->{EditText b=body(a);b.requestFocus();b.setSelection(b.length());});tool("สีข้อความ");onView(withText("แดง")).inRoot(isDialog()).perform(click());tool("ขนาดข้อความ");onView(withText("หัวข้อ 26")).inRoot(isDialog()).perform(click());tool("ตัวเอียง");
            onView(withContentDescription("เปิดพรีเซ็ตรูปแบบ")).perform(click());onView(withText("บันทึกรูปแบบปัจจุบันเป็นพรีเซ็ต")).inRoot(isDialog()).perform(click());onView(withContentDescription("ชื่อพรีเซ็ต")).perform(replaceText("หัวข้อส่วนตัว"),closeSoftKeyboard());onView(withText("บันทึก")).inRoot(isDialog()).perform(click());assertEquals(1,StylePresets.read(prefs).size());
            tool("ล้างรูปแบบ");scenario.recreate();onView(withContentDescription("เปิดพรีเซ็ตรูปแบบ")).perform(click());onView(withText(org.hamcrest.Matchers.startsWith("หัวข้อส่วนตัว ·"))).inRoot(isDialog()).perform(click());scenario.onActivity(a->{EditText b=body(a);b.requestFocus();b.setSelection(b.length());int at=b.length();b.onCreateInputConnection(new EditorInfo()).commitText("ข้อความใหม่",1);assertStyle(b.getText(),at,b.length(),0xffD12D39,26,2);b.setSelection(0,3);});
            onView(withContentDescription("เปิดพรีเซ็ตรูปแบบ")).perform(click());onView(withText(org.hamcrest.Matchers.startsWith("หัวข้อส่วนตัว ·"))).inRoot(isDialog()).perform(click());scenario.onActivity(a->assertStyle(body(a).getText(),0,3,0xffD12D39,26,2));
            onView(withContentDescription("เปิดพรีเซ็ตรูปแบบ")).perform(click());screenshot("26-saved-style-presets");onView(withText("จัดการพรีเซ็ต")).inRoot(isDialog()).perform(click());onView(withText("หัวข้อส่วนตัว")).inRoot(isDialog()).perform(click());onView(withText("เปลี่ยนชื่อ")).inRoot(isDialog()).perform(click());onView(withHint("ชื่อพรีเซ็ต")).perform(replaceText("หัวข้อแดง"),closeSoftKeyboard());onView(withText("บันทึก")).inRoot(isDialog()).perform(click());assertEquals("หัวข้อแดง",StylePresets.read(prefs).get(0).name);
            onView(withContentDescription("เปิดพรีเซ็ตรูปแบบ")).perform(click());onView(withText("จัดการพรีเซ็ต")).inRoot(isDialog()).perform(click());onView(withText("หัวข้อแดง")).inRoot(isDialog()).perform(click());onView(withText("ลบพรีเซ็ต")).inRoot(isDialog()).perform(click());onView(withText("ยืนยัน")).inRoot(isDialog()).perform(click());assertTrue(StylePresets.read(prefs).isEmpty());
        }
    }
    @Test public void repeatedTypingMergesSpansPreservesRichNeighborsAndInlineMedia() {
        SpannableStringBuilder text=new SpannableStringBuilder("ก่อนหลัง");text.setSpan(new ForegroundColorSpan(0xff245BCC),0,text.length(),33);TypingStyle s=new TypingStyle();s.color=0xffD12D39;s.size=26;s.bits=3;s.underline=true;s.highlight=0xffFFF19A;for(int i=0;i<600;i++){text.insert(4+i,"ก");s.apply(text,4+i,5+i);}assertEquals("span count must remain bounded",7,text.getSpans(0,text.length(),CharacterStyle.class).length);assertStyle(text,4,604,0xffD12D39,26,3);assertEquals(0xff245BCC,text.getSpans(0,1,ForegroundColorSpan.class)[0].getForegroundColor());assertEquals(0xff245BCC,text.getSpans(604,605,ForegroundColorSpan.class)[0].getForegroundColor());
        new TypingStyle().apply(text,100,200);assertStyle(text,100,200,PocketTheme.INK,18,0);assertStyle(text,4,100,0xffD12D39,26,3);assertStyle(text,200,604,0xffD12D39,26,3);
        ReplacementSpan media=new ReplacementSpan(){public int getSize(Paint p,CharSequence t,int st,int en,Paint.FontMetricsInt f){return 10;}public void draw(Canvas c,CharSequence t,int st,int en,float x,int top,int y,int bottom,Paint p){}};text.setSpan(media,0,3,33);TypingStyle.clear(text,0,text.length());assertEquals(0,text.getSpanStart(media));assertEquals(3,text.getSpanEnd(media));assertEquals(1,text.getSpans(0,text.length(),CharacterStyle.class).length);
    }
    @Test public void presetStorageValidatesClampsAndRetainsAllSettings() {
        SharedPreferences isolated=context.getSharedPreferences("typing-test",0);isolated.edit().clear().commit();TypingStyle s=new TypingStyle();s.bits=3;s.underline=true;s.strike=true;s.color=0xffD12D39;s.highlight=0xffFFF19A;s.size=32;StylePresets.write(isolated,Arrays.asList(new StylePresets.Entry("ทดสอบ",s)));TypingStyle read=StylePresets.read(isolated).get(0).style;assertEquals(s.json(),read.json());assertNotSame(s,read);assertEquals(48,TypingStyle.parse("{\"size\":9999}").size);assertNull(TypingStyle.parse("broken"));List<StylePresets.Entry> many=new ArrayList<>();for(int i=0;i<25;i++)many.add(new StylePresets.Entry(""+i,s));try{StylePresets.write(isolated,many);fail("limit");}catch(IllegalArgumentException expected){}assertEquals(1,StylePresets.read(isolated).size());isolated.edit().clear().commit();
    }
    static void assertStyle(Spanned text,int a,int b,int color,int size,int bits){for(int at=a;at<b;at++){int actualColor=PocketTheme.INK,actualSize=18,actualBits=0;for(CharacterStyle span:text.getSpans(at,at+1,CharacterStyle.class)){if(text.getSpanStart(span)>at||text.getSpanEnd(span)<=at)continue;if(span instanceof ForegroundColorSpan)actualColor=((ForegroundColorSpan)span).getForegroundColor();if(span instanceof AbsoluteSizeSpan)actualSize=((AbsoluteSizeSpan)span).getSize();if(span instanceof StyleSpan)actualBits|=((StyleSpan)span).getStyle();}assertEquals("color at "+at,color,actualColor);assertEquals("size at "+at,size,actualSize);assertEquals("style at "+at,bits,actualBits);}}
    static EditText body(MainActivity a){return (EditText)find(a.getWindow().getDecorView());}
    static View find(View v){if(v instanceof EditText&&"เขียนสิ่งที่อยากจดจำ…".contentEquals(((EditText)v).getHint()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=find(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}return null;}
    static void tool(String s){onView(withContentDescription(s)).perform(scrollTo(),click());}
    void screenshot(String name)throws Exception{Thread.sleep(300);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
