package com.notepocket.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class DiagramTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private TestIdle idle;
    @Before public void before(){idle=TestIdle.register();}
    @After public void after(){idle.close();}
    @Test public void extractsFencedAndRawSourceWithoutRunningOtherLanguages() {
        String note="คำอธิบายก่อน\n```mermaid\nflowchart TD\n A[หนึ่ง] --> B[สอง]\n```\n```python\nprint(123)\n```\n~~~mermaid\nsequenceDiagram\n Alice->>Bob: สวัสดี\n~~~\nท้ายโน้ต";
        List<DiagramSource.Block> blocks=DiagramSource.find(note);assertEquals(2,blocks.size());for(DiagramSource.Block b:blocks)assertEquals(b.original,note.substring(b.start,b.end));assertTrue(blocks.get(1).code.startsWith("sequenceDiagram"));assertEquals(DiagramSource.SAMPLE,DiagramSource.clean(DiagramSource.fenced(DiagramSource.SAMPLE)));assertEquals(1,DiagramSource.find(DiagramSource.SAMPLE).size());assertTrue(DiagramSource.find("```python\nprint(123)\n```").isEmpty());
        for(String bad:new String[]{"print(123)","flowchart TD\n%%{init: {'securityLevel':'loose'}}%%\nA-->B","flowchart TD\n"+new String(new char[50001]).replace('\0','a')})try{DiagramSource.clean(bad);fail();}catch(IllegalArgumentException expected){}
        String quoted="flowchart TD\nA[\"literal ``` and </script>\"] --> B[done]";assertEquals(quoted,DiagramSource.clean(DiagramSource.fenced(quoted)));
    }
    @Test public void realOfflineMermaidRendersPlantDiagramAndExportsActualPng() throws Exception {
        try(ActivityScenario<DiagramActivity> scenario=ActivityScenario.launch(intent(DiagramSource.SAMPLE))) {
            awaitRender(scenario,"ready");assertEquals("10",js(scenario,"document.querySelectorAll('svg .node').length.toString()"));assertTrue(js(scenario,"document.querySelector('svg').textContent").contains("Vascular cambium"));
            scenario.onActivity(a->{WebView w=findWeb(a.getWindow().getDecorView());assertTrue(w.getSettings().getBlockNetworkLoads());assertFalse(w.getSettings().getAllowFileAccess());assertFalse(w.getSettings().getAllowContentAccess());});
            js(scenario,"fetch('https://example.com/private').then(()=>window.probe='unexpected').catch(()=>window.probe='blocked');'started'");long until=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<until&&!js(scenario,"window.probe||''").equals("blocked"))Thread.sleep(50);assertEquals("blocked",js(scenario,"window.probe"));screenshot("19-mermaid-plant");
            IntentFilter filter=new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("image/png");Instrumentation.ActivityMonitor monitor=InstrumentationRegistry.getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
            try {onView(withContentDescription("บันทึกแผนภาพเป็น PNG")).perform(click());long end=System.currentTimeMillis()+15000;while(monitor.getHits()==0&&System.currentTimeMillis()<end)Thread.sleep(100);assertTrue("PNG save picker opens: "+js(scenario,"JSON.stringify({state:window.diagramState,error:window.diagramExportError||'',stage:document.getElementById('stage').innerHTML.slice(0,250),svg:document.querySelector('#stage svg')?.getBoundingClientRect().toJSON()})"),monitor.getHits()>0);}finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
            File output=new File(context.getCacheDir(),"diagram-test-export.png");output.delete();scenario.onActivity(a->a.onActivityResult(31,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(output))));long end=System.currentTimeMillis()+10000;Bitmap image=null;while(System.currentTimeMillis()<end){if(output.isFile())image=BitmapFactory.decodeFile(output.getPath());if(image!=null)break;Thread.sleep(100);}assertNotNull(image);assertTrue(output.length()>4000);assertTrue(image.getWidth()>300);assertTrue(image.getWidth()<=3072);assertTrue(image.getHeight()<=3072);int colored=0;for(int y=0;y<image.getHeight();y+=4)for(int x=0;x<image.getWidth();x+=4)if(image.getPixel(x,y)!=Color.WHITE)colored++;assertTrue("PNG contains colored diagram pixels",colored>200);image.recycle();output.delete();
        }
    }
    @Test public void invalidSyntaxShowsErrorAndDraftSurvivesRecreation() throws Exception {
        try(ActivityScenario<DiagramActivity> scenario=ActivityScenario.launch(intent("flowchart TD\nA[broken"))) {
            awaitRender(scenario,"error");onView(withText("โค้ด")).perform(click());onView(withContentDescription("โค้ด Mermaid")).perform(replaceText("sequenceDiagram\n Alice->>Bob: สวัสดีจากโน้ต"),closeSoftKeyboard());scenario.recreate();onView(withContentDescription("โค้ด Mermaid")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("sequenceDiagram\n Alice->>Bob: สวัสดีจากโน้ต")));onView(withText("แผนภาพ")).perform(click());awaitRender(scenario,"ready");assertTrue(js(scenario,"document.querySelector('svg').textContent").contains("สวัสดีจากโน้ต"));screenshot("20-mermaid-sequence");
        }
    }
    @Test public void pastedChatGptCodeUpdatesOnlyItsBlockAndUndoKeepsOriginalNote() throws Exception {
        String original="หัวข้อก่อน\n"+DiagramSource.fenced("flowchart TD\n A[เดิม] --> B[จบ]")+"\nข้อความท้ายยังอยู่";long note;
        try(Store store=new Store(context)){note=store.save(-1,"Mermaid pasted test",original,"","ทั่วไป");}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Mermaid pasted test")).perform(click());onView(withContentDescription("ดูแผนภาพ Mermaid ในโน้ต")).perform(scrollTo(),click());DiagramActivity child=awaitDiagram();awaitRender(child,"ready");onView(withText("โค้ด")).perform(click());onView(withContentDescription("โค้ด Mermaid")).perform(replaceText("flowchart LR\n A[ใหม่] --> B[ปลายทาง]"),closeSoftKeyboard());onView(withContentDescription("บันทึกแผนภาพในโน้ต")).perform(click());awaitMain();
            onView(withHint("เขียนสิ่งที่อยากจดจำ…")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("หัวข้อก่อน\n"+DiagramSource.fenced("flowchart LR\n A[ใหม่] --> B[ปลายทาง]")+"\nข้อความท้ายยังอยู่")));onView(withContentDescription("ย้อนกลับข้อความ")).perform(click());onView(withHint("เขียนสิ่งที่อยากจดจำ…")).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText(original)));onView(withContentDescription("กลับจากโน้ต")).perform(click());
        }
        try(Store store=new Store(context)){assertEquals(original,store.note(note).getAsString("body"));ByteArrayOutputStream out=new ByteArrayOutputStream();Backup.write(context,store,out);assertTrue(out.size()>0);}
    }
    private Intent intent(String source) throws Exception {String token=UUID.randomUUID().toString();JSONObject draft=new JSONObject();draft.put("code",source);draft.put("original","");draft.put("start",0);draft.put("end",0);DiagramDraft.write(context,token,draft);return new Intent(context,DiagramActivity.class).putExtra("draft",token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
    private String js(ActivityScenario<DiagramActivity> scenario,String script) throws Exception {AtomicReference<DiagramActivity> a=new AtomicReference<>();scenario.onActivity(a::set);return js(a.get(),script);}
    private String js(DiagramActivity activity,String script) throws Exception {CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{WebView w=findWeb(activity.getWindow().getDecorView());if(w==null){result.set("null");done.countDown();return;}w.evaluateJavascript(script,s->{result.set(s);done.countDown();});});assertTrue(done.await(5,TimeUnit.SECONDS));Object value=new JSONTokener(result.get()).nextValue();return String.valueOf(value);}
    private void awaitRender(ActivityScenario<DiagramActivity> scenario,String state) throws Exception {AtomicReference<DiagramActivity> a=new AtomicReference<>();scenario.onActivity(a::set);awaitRender(a.get(),state);}
    private void awaitRender(DiagramActivity activity,String state) throws Exception {long until=System.currentTimeMillis()+25000;String actual="";while(System.currentTimeMillis()<until){actual=js(activity,"window.diagramState||''");if(actual.equals(state)){if(!state.equals("ready"))return;AtomicBoolean painted=new AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(TextView t:allText(activity.getWindow().getDecorView()))if(t.getText().toString().startsWith("แผนภาพพร้อมแล้ว"))painted.set(true);});if(painted.get())return;}Thread.sleep(100);}assertEquals("Mermaid engine render status",state,actual);}
    private DiagramActivity awaitDiagram() throws Exception {long end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){AtomicReference<DiagramActivity> found=new AtomicReference<>();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof DiagramActivity)found.set((DiagramActivity)a);});if(found.get()!=null)return found.get();Thread.sleep(100);}throw new AssertionError("Diagram activity not resumed");}
    private void awaitMain() throws Exception {long end=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<end){AtomicBoolean yes=new AtomicBoolean();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof MainActivity&&a.hasWindowFocus())yes.set(true);});if(yes.get())return;Thread.sleep(100);}fail("Main activity not resumed");}
    private static List<TextView> allText(View v){List<TextView> result=new ArrayList<>();if(v instanceof TextView)result.add((TextView)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(allText(((ViewGroup)v).getChildAt(i)));return result;}
    private static WebView findWeb(View view) {if(view instanceof WebView)return (WebView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView w=findWeb(((ViewGroup)view).getChildAt(i));if(w!=null)return w;}return null;}
    private void screenshot(String name) throws Exception {Thread.sleep(200);Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File dir=new File(context.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}
