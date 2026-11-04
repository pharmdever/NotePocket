package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiFlowTest {
    private Context context;
    @Test public void createAutosaveAttachReadAndWritePageNote() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase("notepocket.db");
        try(Store store=new Store(context)) {
            store.save(-1,"แผนอ่านหนังสือสัปดาห์นี้","ทบทวนเซลล์และสารชีวโมเลกุล\n☐ ทำโจทย์วันละ 10 ข้อ","","การเรียน");
            long pinned=store.save(-1,"ไอเดียที่อยากเก็บไว้","พื้นที่เล็ก ๆ สำหรับความคิดใหม่และสิ่งที่อยากทำ","","ทั่วไป");store.flag(pinned,"pinned",1);
            String id=UUID.randomUUID().toString();File file=new File(context.getFilesDir(),id);
            try(PdfDocument pdf=new PdfDocument()) {
                for(int i=1;i<=2;i++) {PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,i).create());Canvas c=page.getCanvas();c.drawColor(0xffFAF8F1);Paint p=new Paint();p.setColor(0xff216B58);c.drawRect(40,60,555,240,p);p.setColor(Color.WHITE);p.setTextSize(32);c.drawText("BIOLOGY / "+i,70,150,p);p.setColor(0xff203B34);p.setTextSize(21);c.drawText("Cells, molecules and new ideas.",50,310,p);pdf.finishPage(page);}
                try(OutputStream out=new FileOutputStream(file)) {pdf.writeTo(out);}
            }
            ContentValues doc=new ContentValues();doc.put("id",id);doc.put("name","Biology study guide.pdf");doc.put("mime","application/pdf");doc.put("size",file.length());store.getWritableDatabase().insertOrThrow("docs",null,doc);
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            screenshot("01-notes");onView(withText("＋ เขียนโน้ต")).perform(click());
            onView(withHint("ชื่อโน้ต")).perform(replaceText("ทบทวนบทเรียน"));
            onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(replaceText("สรุปจากเอกสารและกลับมาทบทวน"),closeSoftKeyboard());Thread.sleep(1100);
            try(Store store=new Store(context)) {assertEquals(1,store.query("SELECT id FROM notes WHERE title=?","ทบทวนบทเรียน").size());}
            onView(withText("แนบไฟล์")).perform(click());onView(withText("เลือกจากเอกสารที่เก็บไว้")).perform(click());onView(withText("Biology study guide.pdf")).perform(click());
            onView(withText("ไฟล์แนบ")).perform(scrollTo());screenshot("02-editor");
            onView(withText("เปิด")).perform(scrollTo(),click());screenshot("03-pdf");
            onView(withText("＋")).perform(click());onView(withText("จดหน้านี้")).perform(click());
            onView(withHint("สรุปสิ่งที่อ่าน คำถาม หรือสิ่งที่ต้องกลับมาทบทวน…")).perform(replaceText("จดจำโครงสร้างเซลล์"),closeSoftKeyboard());onView(withText("เสร็จ")).perform(click());
            onView(withText("☆ บันทึกหน้า")).perform(click());onView(withText("ถัดไป ›")).perform(click());
            onView(withText("‹ ก่อนหน้า")).perform(click());onView(withText("★ บันทึกแล้ว")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
            onView(withText("‹")).perform(click());onView(withHint("ชื่อโน้ต")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("ทบทวนบทเรียน")));
        }
        try(Store store=new Store(context)) {assertEquals("จดจำโครงสร้างเซลล์",store.query("SELECT body FROM page_notes WHERE bookmarked=1").get(0).getAsString("body"));}
    }
    private void screenshot(String name) throws Exception {
        Thread.sleep(400);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))) {image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
