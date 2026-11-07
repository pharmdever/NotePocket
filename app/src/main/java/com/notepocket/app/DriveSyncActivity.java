package com.notepocket.app;

import android.accounts.Account;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Optional Drive connection. Editing remains available without an account or network. */
public final class DriveSyncActivity extends Activity {
    private static final int AUTHORIZE=70;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy,exitAfterCancel,authorizing;private SyncCancel cancel;private String restoreRevision="";private int pendingAction;
    private boolean receiverRegistered;
    private final BroadcastReceiver receiver=new BroadcastReceiver() {public void onReceive(Context c,Intent i) {if(!busy&&!authorizing&&!isDestroyed()) render();}};
    private TextView status;private LinearLayout root,controls;private AuthorizationClient auth;
    @Override public void onCreate(Bundle saved) {super.onCreate(saved);auth=Identity.getAuthorizationClient(this);if(saved!=null) {pendingAction=saved.getInt("pendingAction");restoreRevision=saved.getString("restoreRevision","");}render();}
    private int dp(int n) {return PocketTheme.dp(this,n);}
    private TextView text(String value,int size,boolean bold) {TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(PocketTheme.INK);t.setPadding(dp(3),dp(6),dp(3),dp(6));if(bold) t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String value,Runnable run) {Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(PocketTheme.FOREST);b.setBackground(PocketTheme.ripple(this,PocketTheme.SAGE,16));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setMinHeight(dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));controls.addView(b,p);b.setOnClickListener(v->{if(!busy&&!authorizing) run.run();});return b;}
    private LinearLayout card(LinearLayout page) {LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(12),dp(16),dp(12));c.setBackground(PocketTheme.shape(this,PocketTheme.SURFACE,20,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(7));page.addView(c,p);return c;}
    private void render() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(PocketTheme.CREAM);root.setPadding(dp(16),dp(8),dp(16),dp(8));setContentView(root);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(16),i.getSystemWindowInsetTop()+dp(8),dp(16),i.getSystemWindowInsetBottom()+dp(8));return i;});
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);root.addView(heading);ReaderIconButton back=new ReaderIconButton(this,"back","กลับจาก Google Drive",null,this::onBackPressed);heading.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));heading.addView(text("Google Drive",24,true));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);
        String owner,email,connected,last;try(Store store=new Store(this)) {SyncEngine engine=new SyncEngine(this,store,new SyncCancel(),m->{});owner=engine.config("owner");email=engine.config("email");connected=engine.config("connected");last=engine.config("last_success");}
        LinearLayout account=card(page);account.addView(text(connected.equals("1")?"เชื่อมต่อบัญชีแล้ว":"ใช้งานในเครื่องได้ตามเดิม",20,true));if(!email.isEmpty()) account.addView(text(email,16,false));
        status=text(last.isEmpty()?"ยังไม่เคยซิงก์สำเร็จ":"สำเร็จล่าสุด "+DateFormat.getDateTimeInstance().format(new Date(Long.parseLong(last))),14,false);status.setContentDescription("สถานะซิงก์ Drive");account.addView(status);
        account.addView(text("ซิงก์โน้ต โฟลเดอร์ ตาราง SOAP ไฟล์แนบ PDF รอยเขียน ข้อความบน PDF บุ๊กมาร์ก และฟอนต์ · ดาวน์โหลดครบแล้วจึงเปิดออฟไลน์ได้",15,false));controls=account;
        button(connected.equals("1")?"ซิงก์ตอนนี้":"เชื่อมบัญชี Google",()->authorize(0));
        if(connected.equals("1")) {button("ประวัติและกู้คืนเป็นสำเนา",this::history);button("ยกเลิกการเชื่อมบัญชี",this::disconnect);}
        if(!owner.isEmpty()) {CheckBox auto=new CheckBox(this);auto.setText("ซิงก์อัตโนมัติเมื่อมีอินเทอร์เน็ตและไม่ได้แก้ไขโน้ต/PDF");auto.setTextColor(PocketTheme.INK);auto.setChecked(getSharedPreferences("drive",0).getBoolean("auto",false));account.addView(auto);auto.setOnCheckedChangeListener((b,on)->{getSharedPreferences("drive",0).edit().putBoolean("auto",on).apply();SyncScheduler.configure(this);});CheckBox wifi=new CheckBox(this);wifi.setText("อัตโนมัติเฉพาะเครือข่ายไม่จำกัดข้อมูล เช่น Wi-Fi");wifi.setTextColor(PocketTheme.INK);wifi.setChecked(getSharedPreferences("drive",0).getBoolean("wifi",true));account.addView(wifi);wifi.setOnCheckedChangeListener((b,on)->{getSharedPreferences("drive",0).edit().putBoolean("wifi",on).apply();SyncScheduler.configure(this);});}
        LinearLayout info=card(page);info.addView(text("ข้อมูลในเครื่องและการกู้คืน",19,true));info.addView(text("ไม่เชื่อมบัญชีก็เขียนโน้ตและอ่านไฟล์ในเครื่องได้ การเชื่อมครั้งแรกจะรวมข้อมูลในเครื่องกับบัญชีที่เลือก รุ่นนี้ผูกข้อมูลหนึ่งชุดกับบัญชีเดียวเพื่อป้องกันส่งข้อมูลข้ามบัญชี",15,false));info.addView(text("เมื่อแก้ไขชนกันจะเก็บสำเนาขัดแย้งไว้ ประวัติรุ่นเก่าและไฟล์จะใช้พื้นที่ Drive ของคุณ การลบถาวรในหน้าหลักไม่ได้ลบประวัติที่ซิงก์แล้ว และยังควรเก็บไฟล์สำรอง ZIP แยกไว้",15,false));
        LinearLayout setup=card(page);setup.addView(text("ตั้งค่า Google Cloud สำหรับเจ้าของแอป",19,true));setup.addView(text("เปิด Google Drive API และสร้าง OAuth Client แบบ Android พร้อมเพิ่ม Gmail ผู้ทดลองก่อนเชื่อมครั้งแรก",15,false));setup.addView(text("Package: "+getPackageName()+"\nSHA-1: "+certificate(),13,false));controls=setup;
        button("คัดลอก Package และ SHA-1",()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Android OAuth",getPackageName()+"\n"+certificate()));Toast.makeText(this,"คัดลอกแล้ว",Toast.LENGTH_SHORT).show();});
        button("อ่านขั้นตอนตั้งค่า Drive",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/pharmdever/NotePocket/blob/main/docs/drive-setup.md"))));root.requestApplyInsets();
    }
    private void authorize(int action) {
        if(busy||authorizing) return;pendingAction=action;authorizing=true;status.setText("กำลังขอสิทธิ์ Google Drive…");AuthorizationRequest.Builder request=AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(DriveRemote.SCOPE)));
        String email;try(Store store=new Store(this)) {email=new SyncEngine(this,store,new SyncCancel(),m->{}).config("email");}if(!email.isEmpty()) request.setAccount(new Account(email,"com.google"));
        auth.authorize(request.build()).addOnSuccessListener(this,result->{authorizing=false;if(result.hasResolution()) {try {startIntentSenderForResult(result.getPendingIntent().getIntentSender(),AUTHORIZE,null,0,0,0);}catch(IntentSender.SendIntentException e) {status.setText("เปิดหน้าขอสิทธิ์ไม่ได้ กรุณาลองอีกครั้ง");}}else accept(result);}).addOnFailureListener(this,e->{authorizing=false;status.setText(authError(e));});
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {super.onActivityResult(request,result,data);if(request==AUTHORIZE) {authorizing=false;if(result!=RESULT_OK) {restoreRevision="";status.setText("ยังไม่ได้เชื่อมบัญชี · ข้อมูลในเครื่องยังใช้ได้");return;}try {accept(auth.getAuthorizationResultFromIntent(data));}catch(ApiException e) {status.setText(authError(e));}}}
    private void accept(AuthorizationResult result) {
        if(!result.getGrantedScopes().contains(DriveRemote.SCOPE)||result.getAccessToken()==null) {status.setText("ต้องอนุญาตพื้นที่ข้อมูลแอปใน Drive จึงจะซิงก์ได้");return;}String token=result.getAccessToken();
        run("ตรวจบัญชี Google…",()->new DriveRemote(token,cancel).account(),profile->{JSONObject user;String owner;try {user=profile.getJSONObject("user");try(Store store=new Store(this)) {owner=new SyncEngine(this,store,new SyncCancel(),m->{}).config("owner");}if(!owner.isEmpty()&&!owner.equals(user.getString("permissionId"))) {status.setText("บัญชีไม่ตรงกับข้อมูลในเครื่อง กรุณาเลือกบัญชีเดิม");return;}}catch(Exception e) {status.setText("ตรวจข้อมูลบัญชีไม่ได้");return;}
            if(owner.isEmpty()) new PocketDialog.Builder(this).setTitle("เชื่อมกับ "+user.optString("emailAddress","บัญชี Google นี้")+"?").setMessage("จะรวมโน้ตและเอกสารทั้งหมดในเครื่องกับข้อมูล NotePocket ของบัญชีนี้ เก็บไฟล์และประวัติในพื้นที่ Drive ของคุณ และดาวน์โหลดข้อมูลจากอุปกรณ์อื่นมาไว้ในเครื่อง").setNegativeButton("ยกเลิก",null).setPositiveButton("เชื่อมและซิงก์",(d,w)->connectAndSync(token,user)).show();else connectAndSync(token,user);
        });
    }
    private void connectAndSync(String token,JSONObject user) {run("เตรียมซิงก์…",()->{try(Store store=new Store(getApplicationContext())) {SyncEngine engine=new SyncEngine(getApplicationContext(),store,cancel,this::progress);engine.bindAccount(user);if(!restoreRevision.isEmpty()) {engine.restoreCopy(restoreRevision);restoreRevision="";}return new JSONObject().put("message",engine.sync(new DriveRemote(token,cancel)));}},answer->{render();status.setText(answer.optString("message"));SyncScheduler.configure(this);sendBroadcast(new Intent(SyncScheduler.DONE).setPackage(getPackageName()));});}
    private void progress(String value) {runOnUiThread(()->{if(!isDestroyed()) status.setText(value);});}
    private interface Operation {JSONObject run() throws Exception;}
    private interface Done {void run(JSONObject result);}
    private void run(String message,Operation op,Done done) {
        if(busy) return;if(!SyncRuntime.RUNNING.compareAndSet(false,true)) {status.setText("มีการซิงก์กำลังทำงานอยู่ กรุณารอสักครู่");return;}busy=true;cancel=new SyncCancel();status.setText(message);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        worker.execute(()->{JSONObject answer=null;Exception error=null;try {answer=op.run();}catch(Exception e) {error=e;}catch(OutOfMemoryError e) {error=new IOException("ข้อมูลใหญ่เกินหน่วยความจำ ข้อมูลในเครื่องยังอยู่");}finally {SyncRuntime.RUNNING.set(false);}JSONObject output=answer;Exception failure=error;runOnUiThread(()->{busy=false;if(isDestroyed()) return;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);if(exitAfterCancel) {finish();return;}if(failure!=null) {restoreRevision="";status.setText(failure instanceof InterruptedIOException?"หยุดซิงก์แล้ว · ข้อมูลในเครื่องยังอยู่":failure instanceof IOException||failure instanceof SyncRuntime.Deferred?failure.getMessage():"ยังซิงก์ไม่ได้ ตรวจข้อมูลและการตั้งค่า · ข้อมูลในเครื่องยังอยู่");}else done.run(output);});});
    }
    private void history() {
        List<ContentValues> rows;try(Store store=new Store(this)) {rows=store.query("SELECT rev,label,kind,time FROM sync_ops WHERE deleted=0 AND kind IN ('note','doc','page') ORDER BY rowid DESC LIMIT 100");}if(rows.isEmpty()) {status.setText("ยังไม่มีประวัติในเครื่องนี้ กดซิงก์ก่อน");return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++) labels[i]=rows.get(i).getAsString("label")+"\n"+DateFormat.getDateTimeInstance().format(new Date(rows.get(i).getAsLong("time")));
        new PocketDialog.Builder(this).setTitle("ประวัติล่าสุด · กู้คืนเป็นสำเนา").setItems(labels,(d,i)->{String selected=rows.get(i).getAsString("rev");new PocketDialog.Builder(this).setTitle("กู้คืนและซิงก์เป็นสำเนาใหม่?").setMessage("เก็บข้อมูลปัจจุบันไว้ และดาวน์โหลดไฟล์ที่จำเป็นจาก Drive").setNegativeButton("ยกเลิก",(x,w)->restoreRevision="").setPositiveButton("กู้คืน",(x,w)->{restoreRevision=selected;authorize(1);}).show();}).setNegativeButton("ปิด",null).show();
    }
    private void disconnect() {new PocketDialog.Builder(this).setTitle("ยกเลิกการเชื่อมบัญชี?").setMessage("หยุดซิงก์อัตโนมัติและเพิกถอนสิทธิ์ Drive ข้อมูลในเครื่องและประวัติบน Drive ยังอยู่ การเชื่อมใหม่ใช้บัญชีเดิมของข้อมูลชุดนี้").setNegativeButton("ยกเลิก",null).setPositiveButton("ยกเลิกการเชื่อม",(d,w)->{
        String email;try(Store store=new Store(this)) {SyncEngine engine=new SyncEngine(this,store,new SyncCancel(),m->{});email=engine.config("email");engine.config("connected","0");}getSharedPreferences("drive",0).edit().putBoolean("auto",false).apply();SyncScheduler.configure(this);
        RevokeAccessRequest.Builder r=RevokeAccessRequest.builder().setScopes(Collections.singletonList(new Scope(DriveRemote.SCOPE)));if(!email.isEmpty()) r.setAccount(new Account(email,"com.google"));auth.revokeAccess(r.build()).addOnCompleteListener(this,t->{render();if(!t.isSuccessful()) status.setText("หยุดซิงก์ในแอปแล้ว แต่เพิกถอนสิทธิ์ Google ยังไม่สำเร็จ สามารถลบสิทธิ์ในบัญชี Google ได้");});render();
    }).show();}
    private String certificate() {try {PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);byte[] bytes=Build.VERSION.SDK_INT>=28?p.signingInfo.getApkContentsSigners()[0].toByteArray():p.signatures[0].toByteArray();byte[] sha=MessageDigest.getInstance("SHA-1").digest(bytes);StringBuilder b=new StringBuilder();for(byte v:sha) {if(b.length()>0) b.append(':');b.append(String.format(Locale.ROOT,"%02X",v&255));}return b.toString();}catch(Exception e) {return "ตรวจจาก APK release ใน GitHub Actions";}}
    static String authError(Exception e) {int code=e instanceof ApiException?((ApiException)e).getStatusCode():-1;return code==10?"Google OAuth ยังไม่ตรงกับแอป ตรวจ Package, SHA-1, Drive API และ Test users ตามคู่มือ":"เชื่อม Google ไม่สำเร็จ ("+code+") · ตรวจอินเทอร์เน็ตและ Google Play services";}
    @Override protected void onResume() {super.onResume();if(!receiverRegistered) {IntentFilter f=new IntentFilter(SyncScheduler.DONE);androidx.core.content.ContextCompat.registerReceiver(this,receiver,f,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}}
    @Override protected void onPause() {if(receiverRegistered) {unregisterReceiver(receiver);receiverRegistered=false;}super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out) {out.putInt("pendingAction",pendingAction);out.putString("restoreRevision",restoreRevision);super.onSaveInstanceState(out);}
    @Override public void onBackPressed() {if(busy) {exitAfterCancel=true;cancel.cancel();status.setText("กำลังหยุดซิงก์…");}else super.onBackPressed();}
    @Override protected void onDestroy() {if(cancel!=null&&isFinishing()) cancel.cancel();worker.shutdown();super.onDestroy();}
}
