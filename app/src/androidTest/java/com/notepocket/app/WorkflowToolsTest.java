package com.notepocket.app;

import android.content.*;
import android.text.*;
import android.widget.*;
import android.view.*;
import android.graphics.Bitmap;
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
public class WorkflowToolsTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Test public void listContinuationStopsOnBlankAndKeepsOrdinaryText() {
        for(String[] test:new String[][]{{"1. First\n","1. First\n2. "},{"• หนึ่ง\n","• หนึ่ง\n• "},{"☑ done\n","☑ done\n☐ "},{"- item\n","- item\n- "},{"1. One\n2. \n","1. One\n"},{"☐ \n",""}}) {SpannableStringBuilder text=new SpannableStringBuilder(test[0]);assertTrue(NoteLists.afterEnter(text,text.length()-1,0,1));assertEquals(test[1],text.toString());}
        SpannableStringBuilder ordinary=new SpannableStringBuilder("text\n");assertFalse(NoteLists.afterEnter(ordinary,4,0,1));assertEquals("text\n",ordinary.toString());SpannableStringBuilder paste=new SpannableStringBuilder("1. One\n2. Two\n");assertFalse(NoteLists.afterEnter(paste,0,0,paste.length()));
        SpannableStringBuilder lines=new SpannableStringBuilder("One\nTwo");NoteLists.toggle(lines,0,lines.length(),"number");assertEquals("1. One\n2. Two",lines.toString());NoteLists.toggle(lines,0,lines.length(),"number");assertEquals("One\nTwo",lines.toString());
        assertEquals("table",NoteTools.order("[\"table\",\"unknown\",\"table\"]").get(0));assertEquals(NoteTools.IDS.length,NoteTools.order("[\"table\",\"table\"]").size());
    }
    @Test public void tableTransferWidthsAndInsertionPreserveEveryCell() {
        NoteTable table=new NoteTable(2,2);table.widths.set(0,260);table.cells.get(0).set(0,"หัว,ข้อ\tquoted \"x\"");table.cells.get(1).set(1,"หลาย\nบรรทัด");assertEquals(table.cells,TableTransfer.parse(TableTransfer.write(table,true)));assertEquals(table.cells,TableTransfer.parseClipboard(TableTransfer.write(table,false)));assertEquals("comma,inside",TableTransfer.parseClipboard("comma,inside").get(0).get(0));
        table.insertRow(1);table.insertColumn(1);assertEquals(260,table.width(0));assertEquals("หลาย\nบรรทัด",table.cells.get(2).get(2));table.removeColumn(1);assertEquals("หลาย\nบรรทัด",table.cells.get(2).get(1));table.paste(TableTransfer.parseClipboard("a\tb\r\nc\td\r\n"),1,0);assertEquals("d",table.cells.get(2).get(1));assertEquals(260,NoteTable.decode(NoteTable.encode(Arrays.asList(table))).get(0).width(0));
        String before=NoteTable.encode(Arrays.asList(table));try {table.paste(TableTransfer.parseClipboard("x\ty"),0,7);fail();}catch(IllegalArgumentException expected) {}assertEquals(before,NoteTable.encode(Arrays.asList(table)));
        try {TableTransfer.parseClipboard("\"unfinished");fail();}catch(IllegalArgumentException expected) {}
    }
    @Test public void foldersAndSoapDraftRoundTripWithoutInventedContent() throws Exception {
        context.deleteDatabase("workflow-test.db");try(Store store=new Store(context,"workflow-test.db")) {store.createFolder("empty-folder");store.createFolder("all");SoapDraft draft=new SoapDraft();draft.put("problem","ติดตามการใช้ยา");draft.put("s","ผู้ป่วยแจ้งข้อมูลตัวอย่าง");assertTrue(draft.render().contains("[ยังไม่ได้ระบุ]"));assertFalse(draft.render().contains("ปกติ"));long id=store.save(-1,"SOAP sample","body","","all","[]",draft.json());store.renameFolder("all","งานติดตาม");assertEquals("งานติดตาม",store.note(id).getAsString("folder"));ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(context,store,bytes);Backup.restore(context,store,new ByteArrayInputStream(bytes.toByteArray()));ContentValues copy=store.query("SELECT * FROM notes WHERE id<>?",""+id).get(0);assertEquals(draft.json(),copy.getAsString("soap"));assertFalse(store.query("SELECT * FROM folders WHERE name='empty-folder'").isEmpty());store.deleteFolder("งานติดตาม");assertEquals("ทั่วไป",store.note(id).getAsString("folder"));assertEquals(2,store.query("SELECT id FROM notes WHERE folder='ทั่วไป'").size());}finally {context.deleteDatabase("workflow-test.db");}
    }
    @Test public void createsFolderSoapAndCustomToolsPastesSheetAndExportsCsv() throws Exception {
        androidx.test.espresso.Espresso.setFailureHandler((error,matcher)->{try {screenshot("workflow-failure");}catch(Exception ignored) {}new androidx.test.espresso.base.DefaultFailureHandler(context).handle(error,matcher);});context.getSharedPreferences("reader",0).edit().remove("noteToolOrder").remove("recentNoteTool").commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withText("โฟลเดอร์")).perform(click());onView(withText("สร้างโฟลเดอร์")).perform(click());onView(withHint("ชื่อโฟลเดอร์")).perform(replaceText("งาน SOAP ทดสอบ"),closeSoftKeyboard());onView(withText("บันทึก")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withText("โฟลเดอร์")).perform(click());onView(withText("งาน SOAP ทดสอบ (0)")).perform(click());onView(withText("เปิดโฟลเดอร์")).perform(click());onView(withText("＋ SOAP note")).perform(click());
            field("PROBLEM","ติดตามผลตัวอย่าง");field("S","ข้อมูลอาการที่ผู้ใช้กรอก");field("O","ผลตรวจตัวอย่างที่ผู้ใช้กรอก");field("A","การประเมินตัวอย่างที่ผู้ใช้กรอก");field("P","แผนติดตามตัวอย่างที่ผู้ใช้กรอก");
            onView(withText("ปิด เก็บร่าง")).perform(click());onView(withHint("เขียนสิ่งที่อยากจดจำ…")).perform(replaceText("1. First"),new androidx.test.espresso.ViewAction() {public org.hamcrest.Matcher<View> getConstraints() {return isAssignableFrom(EditText.class);}public String getDescription() {return "Enter continues a numbered list in the actual editor";}public void perform(androidx.test.espresso.UiController controller,View view) {EditText e=(EditText)view;e.getText().insert(e.length(),"\n");assertEquals("1. First\n2. ",e.getText().toString());}},replaceText(""));tool("ตัวช่วย SOAP note");onView(withContentDescription("SOAP S")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText("ข้อมูลอาการที่ผู้ใช้กรอก")));scenario.recreate();onView(withContentDescription("SOAP P")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo()).check(matches(withText("แผนติดตามตัวอย่างที่ผู้ใช้กรอก")));screenshot("10-soap-draft");
            onView(withText("ดูตัวอย่าง")).perform(click());onView(withText("กลับไปแก้")).perform(click());onView(withText("แทรก / อัปเดต")).perform(click());onView(withHint("เขียนสิ่งที่อยากจดจำ…")).check(matches(withText(org.hamcrest.Matchers.containsString("S • Subjective"))));
            tool("จัดลำดับเครื่องมือ");onView(withText("4. แทรกตาราง")).perform(click());onView(withText("ย้ายไปหน้าแรก")).perform(click());onView(withText("เสร็จ")).perform(click());assertEquals("table",NoteTools.order(context.getSharedPreferences("reader",0).getString("noteToolOrder","[]")).get(0));
            scenario.recreate();onView(withContentDescription("ใช้เครื่องมือล่าสุด: ตัวช่วย SOAP note")).check(matches(isDisplayed()));tool("แทรกตาราง");onView(withText("แทรก")).perform(click());onView(withContentDescription("ตาราง 1 แถว 1 คอลัมน์ 1")).perform(closeSoftKeyboard());
            scenario.onActivity(a->((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Sheet","วันที่\tรายการ\r\nวันนี้\tตัวอย่างข้อมูล\r\n")));
            menu("วางจาก Excel / Sheets");onView(withText("วาง")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());menu("แทรกแถว…");onView(withText("ก่อนแถว 2")).perform(click());menu("แทรกคอลัมน์…");onView(withText("ก่อนคอลัมน์ 2")).perform(click());
            menu("ความกว้างคอลัมน์…");onView(withText("คอลัมน์ 1 · 150")).perform(click());onView(isAssignableFrom(SeekBar.class)).perform(new androidx.test.espresso.ViewAction() {public org.hamcrest.Matcher<View> getConstraints() {return isAssignableFrom(SeekBar.class);}public String getDescription() {return "Set column width";}public void perform(androidx.test.espresso.UiController c,View v) {((SeekBar)v).setProgress(160);}});onView(withText("บันทึก")).perform(click());screenshot("11-sheet-custom-tools");
            IntentFilter csvFilter=new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);csvFilter.addCategory(Intent.CATEGORY_OPENABLE);csvFilter.addDataType("text/csv");android.app.Instrumentation.ActivityMonitor monitor=InstrumentationRegistry.getInstrumentation().addMonitor(csvFilter,new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null),true);
            try {menu("ส่งออก CSV");assertTrue(monitor.getHits()>0);}finally {InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
            File output=new File(context.getCacheDir(),"workflow-table.csv");output.delete();scenario.onActivity(a->a.onActivityResult(16,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(output))));Thread.sleep(1000);assertTrue(output.isFile());assertTrue(new String(java.nio.file.Files.readAllBytes(output.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains("ตัวอย่างข้อมูล"));output.delete();
            onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        try(Store store=new Store(context)) {ContentValues note=store.query("SELECT * FROM notes WHERE title=?","SOAP • ติดตามผลตัวอย่าง").get(0);assertEquals("งาน SOAP ทดสอบ",note.getAsString("folder"));assertEquals("ข้อมูลอาการที่ผู้ใช้กรอก",new SoapDraft(note.getAsString("soap")).get("s"));NoteTable table=NoteTable.decode(note.getAsString("tables")).get(0);assertEquals(240,table.width(0));assertEquals("ตัวอย่างข้อมูล",table.cells.get(2).get(2));assertEquals(4,table.cells.size());assertEquals(4,table.columns());}
    }
    private void field(String key,String text) {onView(withContentDescription("SOAP "+key)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(),replaceText(text),closeSoftKeyboard());}
    private void tool(String name) {onView(withContentDescription(name)).perform(scrollTo(),click());}
    private void menu(String item) {onView(withContentDescription("ตัวเลือกตาราง 1")).perform(scrollTo(),click());androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.hasToString(item)).perform(click());}
    private void screenshot(String name) throws Exception {Thread.sleep(350);Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=new File(context.getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))) {bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
