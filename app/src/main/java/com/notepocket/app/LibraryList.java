package com.notepocket.app;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Recycles native list rows; debounces and cancels searches; stale results cannot replace a newer query. */
public final class LibraryList extends LinearLayout implements AutoCloseable {
    public interface Binder {View bind(ContentValues row,View recycled,boolean files);}
    private final Context context;private final Binder binder;private final boolean files;private final String filter;
    private final List<ContentValues> rows=new ArrayList<>();private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>(),r->new Thread(r,"NotePocket-library"));
    private final ListView list;private final TextView status,footer;private final BaseAdapter adapter;
    private CancellationSignal cancellation;private Runnable debounce;private int generation;private boolean more,closed;private volatile boolean loading;private String query="";private long total=-1;
    public LibraryList(Context context,boolean files,String filter,String query,Binder binder) {
        super(context);this.context=context.getApplicationContext();this.files=files;this.filter=filter;this.binder=binder;setOrientation(VERTICAL);
        status=new TextView(context);status.setTextSize(13);status.setTextColor(PocketTheme.MUTED);status.setPadding(0,dp(8),0,dp(6));addView(status,new LayoutParams(-1,-2));list=new ListView(context);list.setDivider(null);list.setDividerHeight(0);list.setSelector(android.R.color.transparent);list.setCacheColorHint(android.graphics.Color.TRANSPARENT);list.setContentDescription(files?"รายการเอกสาร":"รายการโน้ต");addView(list,new LayoutParams(-1,0,1));footer=new TextView(context);footer.setTextSize(14);footer.setTextColor(PocketTheme.MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(dp(12),dp(18),dp(12),dp(18));list.addFooterView(footer,null,false);
        adapter=new BaseAdapter() {public int getCount() {return rows.size();}public ContentValues getItem(int at) {return rows.get(at);}public long getItemId(int at) {return rows.get(at).getAsLong(files?"_rowid":"id");}public boolean hasStableIds() {return true;}public View getView(int at,View recycled,ViewGroup parent) {return binder.bind(rows.get(at),recycled,files);}};list.setAdapter(adapter);list.setOnScrollListener(new AbsListView.OnScrollListener() {public void onScrollStateChanged(AbsListView view,int state) {}public void onScroll(AbsListView view,int first,int visible,int count) {if(count>1&&first+visible>=count-4) next();}});footer.setOnClickListener(v->next());this.query=query;reload();
    }
    private int dp(int value) {return PocketTheme.dp(getContext(),value);}
    public boolean isLoading() {return loading;}
    public int loadedCount() {return rows.size();}
    public int attachedRowCount() {return list.getChildCount();}
    public void search(String value) {query=value;reset();loading=true;status.setText("กำลังค้นหา…");debounce=()->{debounce=null;loading=false;next();};main.postDelayed(debounce,240);}
    private void reset() {generation++;if(cancellation!=null) cancellation.cancel();if(debounce!=null) main.removeCallbacks(debounce);debounce=null;worker.getQueue().clear();rows.clear();more=true;loading=false;total=-1;adapter.notifyDataSetChanged();}
    public void reload() {if(closed) return;reset();next();}
    private void next() {
        if(closed||loading||!more) return;loading=true;footer.setText("กำลังโหลด…");if(rows.isEmpty()) status.setText(query.isEmpty()?"กำลังเปิดรายการ…":"กำลังค้นหา…");final int request=generation;final String term=query;final ContentValues after=rows.isEmpty()?null:rows.get(rows.size()-1);CancellationSignal signal=new CancellationSignal();cancellation=signal;
        worker.execute(()->{try(Store store=new Store(context)) {LibraryPage page=LibraryPage.read(store,files,filter,term,after,signal);main.post(()->{if(closed||request!=generation) return;loading=false;if(page.total>=0) total=page.total;rows.addAll(page.rows);more=page.more;String noun=files?" เอกสาร":" โน้ต";status.setText(total>=0?total+noun:"พบแล้ว "+rows.size()+(more?"+":"")+noun);footer.setText(rows.isEmpty()?(term.isEmpty()?files?"ยังไม่มีเอกสาร · แตะนำเข้าไฟล์เพื่อเริ่ม":"ยังไม่มีโน้ต · แตะเขียนโน้ตเพื่อเริ่ม":"ไม่พบรายการ · ลองเปลี่ยนคำค้นหา"):more?"เลื่อนเพื่อดูรายการเพิ่มเติม":"");adapter.notifyDataSetChanged();});}catch(Exception error) {main.post(()->{if(closed||request!=generation) return;loading=false;status.setText("เปิดรายการไม่ได้");footer.setText("แตะเพื่อลองอีกครั้ง");});}});
    }
    public void releaseRows() {if(!closed) {reset();footer.setText("");}}
    @Override public void close() {closed=true;generation++;if(cancellation!=null) cancellation.cancel();if(debounce!=null) main.removeCallbacks(debounce);worker.getQueue().clear();worker.shutdownNow();}
}
