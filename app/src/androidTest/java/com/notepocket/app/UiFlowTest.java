package com.notepocket.app;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiFlowTest {
    private TestIdle idle;
    @Before public void registerBackgroundWork() {idle=TestIdle.register();}
    @After public void unregisterBackgroundWork() {if(idle!=null) idle.close();}
    private Context context;
    @Test public void notesInkTextFontsLandscapeFullscreenAndSettings() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();androidx.test.espresso.Espresso.setFailureHandler((error,matcher)->{try {screenshot("failure");}catch(Exception ignored) {}new androidx.test.espresso.base.DefaultFailureHandler(context).handle(error,matcher);});context.deleteDatabase("notepocket.db");context.getSharedPreferences("reader",0).edit().clear().commit();String docId;
        try(Store store=new Store(context)) {
            store.createFolder("empty-upgrade-folder");SoapDraft upgradeSoap=new SoapDraft();upgradeSoap.put("s","ข้อความ SOAP ก่อนอัปเดต");NoteTable upgradeTable=new NoteTable(2,2);upgradeTable.widths.set(0,240);upgradeTable.cells.get(0).set(0,"ตารางก่อนอัปเดต");android.text.SpannableString upgradeText=new android.text.SpannableString("สีและขนาดก่อนอัปเดต");upgradeText.setSpan(new android.text.style.AbsoluteSizeSpan(26,true),0,upgradeText.length(),33);upgradeText.setSpan(new android.text.style.ForegroundColorSpan(0xffD12D39),0,upgradeText.length(),33);store.save(-1,"Note editor upgrade fixture",upgradeText.toString(),NoteFormat.encode(upgradeText),"ทดสอบ",NoteTable.encode(java.util.Arrays.asList(upgradeTable)),upgradeSoap.json());
            store.save(-1,"แผนอ่านหนังสือสัปดาห์นี้","ทบทวนเซลล์และสารชีวโมเลกุล\n☐ ทำโจทย์วันละ 10 ข้อ","","การเรียน");
            long pinned=store.save(-1,"ไอเดียที่อยากเก็บไว้","พื้นที่เล็ก ๆ สำหรับความคิดใหม่และสิ่งที่อยากทำ","","ทั่วไป");store.flag(pinned,"pinned",1);
            docId=UUID.randomUUID().toString();File file=new File(context.getFilesDir(),docId);PdfDocument pdf=new PdfDocument();
            try {for(int i=1;i<=2;i++) {PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,i).create());Canvas c=page.getCanvas();c.drawColor(0xffFAF8F1);Paint p=new Paint();p.setColor(0xff216B58);c.drawRect(40,60,555,240,p);p.setColor(Color.WHITE);p.setTextSize(32);c.drawText("BIOLOGY / "+i,70,150,p);p.setColor(0xff203B34);p.setTextSize(21);c.drawText("Cells, molecules and new ideas.",50,310,p);pdf.finishPage(page);}try(OutputStream out=new FileOutputStream(file)) {pdf.writeTo(out);}}finally {pdf.close();}
            ContentValues doc=new ContentValues();doc.put("id",docId);doc.put("name","Biology study guide.pdf");doc.put("mime","application/pdf");doc.put("size",file.length());store.getWritableDatabase().insertOrThrow("docs",null,doc);
            File systemFont=systemFont();try(InputStream in=new FileInputStream(systemFont)) {Fonts.importFont(context,store,in,"SampleFont.ttf");}
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            screenshot("01-notes");onView(withText("ทั้งหมด ▾")).perform(click());screenshot("12-filter-dialog");androidx.test.espresso.Espresso.pressBack();onView(withText("＋ เขียนโน้ต")).perform(click());onView(withHint("ชื่อโน้ต")).perform(replaceText("ทบทวนบทเรียน"));onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(replaceText("สรุปจากเอกสารและกลับมาทบทวน"),closeSoftKeyboard());Thread.sleep(1100);
            onView(withContentDescription("แนบไฟล์")).perform(click());onView(withText("เลือกจากเอกสารที่เก็บไว้")).perform(click());onView(withText("Biology study guide.pdf")).perform(click());onView(withText("เปิด")).perform(scrollTo(),click());screenshot("02-reader");
            palette("ปากกา");onView(withContentDescription("ปากกา")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isSelected()));onView(isAssignableFrom(PdfPageView.class)).perform(swipeRight());palette("ไฮไลต์");onView(isAssignableFrom(PdfPageView.class)).perform(swipeLeft());palette("↶");palette("ทำซ้ำ");
            palette("ข้อความ");onView(isAssignableFrom(PdfPageView.class)).perform(click());onView(withHint("ข้อความบน PDF")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("ข้อความใหม่บน PDF"),closeSoftKeyboard());
            onView(withContentDescription("ฟอนต์ข้อความ")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(),click());onView(withText("SampleFont.ttf")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());onView(withContentDescription("สีข้อความ")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(),click());onView(withText("แดง")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());onView(withText("บันทึก")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());screenshot("03-text-and-highlight");
            readerMore("จดหน้านี้");onView(withHint("สรุปสิ่งที่อ่าน คำถาม หรือสิ่งที่ต้องกลับมาทบทวน…")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(replaceText("จดจำโครงสร้างเซลล์"),closeSoftKeyboard());onView(withText("เสร็จ")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());readerMore("บันทึก / เลิกบันทึกหน้านี้");
            onView(withContentDescription("หน้าถัดไป")).perform(click());onView(withContentDescription("หน้าก่อนหน้า")).perform(click());onView(withContentDescription("เลือกเลขหน้า PDF")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText(org.hamcrest.Matchers.startsWith("★"))));
            // Select a real extracted glyph, then use the contextual highlighter action.
            List<PdfWords.Glyph> words=PdfWords.read(context,docId,0);assertFalse(words.isEmpty());PdfWords.Glyph glyph=words.get(words.size()-1);
            scenario.onActivity(a->{PdfPageView view=findPdf(a.getWindow().getDecorView());view.setWords(words);view.setMode(PdfPageView.SELECT);view.selectAt(glyph.box.centerX(),glyph.box.centerY());});onView(withText("ไฮไลต์")).perform(click());
            scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1000);
            scenario.onActivity(a->{PdfPageView view=findPdf(a.getWindow().getDecorView());assertTrue("Landscape PDF must retain most of the screen height",view.getHeight()>a.getResources().getDisplayMetrics().heightPixels*.55f);});screenshot("04-landscape");
            onView(withContentDescription("เต็มจอ")).perform(click());Thread.sleep(500);dismissFullscreenHint();scenario.onActivity(a->{PdfPageView view=findPdf(a.getWindow().getDecorView());assertTrue("Fullscreen should maximize the document",view.getHeight()>a.getResources().getDisplayMetrics().heightPixels*.85f);});screenshot("05-fullscreen");readerMore("ซ่อนแถบเครื่องมือ");onView(withContentDescription("เปิดเครื่องมือลอย")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));screenshot("06-fullscreen-hidden-tools");onView(withContentDescription("เปิดเครื่องมือลอย")).perform(click());
            scenario.onActivity(MainActivity::onBackPressed);scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(800);onView(withContentDescription("กลับจาก PDF")).perform(click());onView(withHint("ชื่อโน้ต")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("ทบทวนบทเรียน")));
            onView(withContentDescription("กลับจากโน้ต")).perform(click());onView(withText("ตั้งค่า")).perform(click());onView(withText("ตั้งค่าตัวอ่าน PDF")).perform(scrollTo(),click());onView(withText("เปิดหน้าจอค้างขณะอ่าน PDF")).perform(click());onView(withText("บันทึก")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());assertTrue(context.getSharedPreferences("reader",0).getBoolean("keepAwake",false));
        }
        try(Store store=new Store(context)) {assertEquals("จดจำโครงสร้างเซลล์",store.page(docId,0).getAsString("body"));assertTrue(Ink.decode(store.drawing(docId,0)).size()>=2);TextBox box=TextBox.decode(store.textBoxes(docId,0)).get(0);assertEquals("ข้อความใหม่บน PDF",box.text);assertEquals(0xffE13A3A,box.color);assertTrue(box.font.startsWith("font-"));}
    }
    private void dismissFullscreenHint() throws Exception {
        // Android's first-use immersive hint belongs to System UI, outside Espresso's roots.
        for(int i=0;i<12;i++) {android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null) {for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Got it")) {if(node.isClickable()) {node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(300);return;}}}Thread.sleep(250);}
    }
    private void palette(String item) {String description=item.equals("↶")?"ย้อนกลับรอยเขียน":item.equals("ทำซ้ำ")?"ทำซ้ำรอยเขียน":item;onView(withContentDescription(description)).perform(click());}
    private void readerMore(String item) {onView(withContentDescription("เครื่องมือเพิ่มเติม")).perform(click());androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.instanceOf(android.view.MenuItem.class),org.hamcrest.Matchers.hasToString(item))).perform(click());}
    static File systemFont() {File[] files=new File("/system/fonts").listFiles((dir,name)->name.toLowerCase(java.util.Locale.ROOT).endsWith(".ttf"));assertNotNull(files);assertTrue(files.length>0);for(File file:files) if(file.getName().contains("Thai")) return file;return files[0];}
    static PdfPageView findPdf(android.view.View view) {if(view instanceof PdfPageView) return (PdfPageView)view;if(view instanceof android.view.ViewGroup) {android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++) {PdfPageView result=findPdf(group.getChildAt(i));if(result!=null) return result;}}return null;}
    private void screenshot(String name) throws Exception {Thread.sleep(400);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))) {image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
