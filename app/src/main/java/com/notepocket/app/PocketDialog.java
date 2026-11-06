package com.notepocket.app;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

/** Uses native dialog behavior, scrolling and adapters with the app's shared visual language. */
public final class PocketDialog {
    private PocketDialog() {}
    public static class Builder extends AlertDialog.Builder {
        public Builder(Context context) {super(context,R.style.PocketDialogTheme);}
        @Override public Builder setTitle(CharSequence title) {
            super.setTitle(title);Context c=getContext();LinearLayout header=new LinearLayout(c);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(22),dp(18),dp(22),dp(10));
            TextView brand=new TextView(c);brand.setText("NOTEPOCKET");brand.setTextSize(10);brand.setLetterSpacing(.16f);brand.setTextColor(PocketTheme.FOREST);header.addView(brand);
            TextView heading=new TextView(c);heading.setText(title);heading.setTextColor(PocketTheme.INK);heading.setTextSize(21);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(0,dp(6),0,dp(4));header.addView(heading);super.setCustomTitle(header);return this;
        }
        @Override public Builder setItems(CharSequence[] items,DialogInterface.OnClickListener listener) {super.setAdapter(new MenuAdapter(getContext(),items),listener);return this;}
        @Override public Builder setMessage(CharSequence message) {
            Context c=getContext();ScrollView sc=new ScrollView(c);LinearLayout wrap=new LinearLayout(c);wrap.setPadding(dp(18),dp(4),dp(18),dp(12));sc.addView(wrap);TextView text=new TextView(c);text.setText(message);text.setTextColor(PocketTheme.INK);text.setTextSize(16);text.setLineSpacing(dp(3),1);text.setPadding(dp(16),dp(14),dp(16),dp(14));text.setBackground(PocketTheme.shape(c,PocketTheme.SAGE,18,false));wrap.addView(text,new LinearLayout.LayoutParams(-1,-2));super.setView(sc);return this;
        }
        @Override public AlertDialog create() {
            AlertDialog dialog=super.create();View decor=dialog.getWindow().getDecorView();decor.setClipToOutline(true);decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View view) {view.post(()->decorate(dialog));}
                public void onViewDetachedFromWindow(View view) {}
            });return dialog;
        }
        private void decorate(AlertDialog dialog) {
            Context c=getContext();ListView list=dialog.getListView();if(list!=null) {list.setDivider(null);list.setDividerHeight(0);list.setPadding(dp(14),dp(2),dp(14),dp(8));list.setClipToPadding(false);}
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {Button b=dialog.getButton(which);if(b==null||b.getVisibility()!=View.VISIBLE) continue;boolean primary=which==AlertDialog.BUTTON_POSITIVE;b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?PocketTheme.SURFACE:PocketTheme.FOREST);b.setBackground(PocketTheme.ripple(c,primary?PocketTheme.FOREST:which==AlertDialog.BUTTON_NEGATIVE?PocketTheme.SAGE:PocketTheme.CREAM,14));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));if(b.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)b.getLayoutParams();p.setMargins(dp(3),dp(4),dp(3),dp(8));b.setLayoutParams(p);}}
        }
        private int dp(int n) {return PocketTheme.dp(getContext(),n);}
    }
    private static final class MenuAdapter extends BaseAdapter {
        private final Context context;private final CharSequence[] items;
        MenuAdapter(Context context,CharSequence[] items) {this.context=context;this.items=items.clone();}
        public int getCount() {return items.length;}public CharSequence getItem(int at) {return items[at];}public long getItemId(int at) {return at;}
        private int dp(int value) {return PocketTheme.dp(context,value);}
        public View getView(int at,View recycled,ViewGroup parent) {
            String label=items[at].toString();boolean destructive=label.contains("ลบ")||label.equals("ถังขยะ")||label.contains("ย้ายเข้าถังขยะ");
            LinearLayout outer=new LinearLayout(context);outer.setPadding(0,dp(3),0,dp(3));LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(6),dp(12),dp(6));row.setMinimumHeight(dp(60));row.setBackground(PocketTheme.shape(context,destructive?0xffF8E8E2:at%3==1?PocketTheme.CREAM:PocketTheme.SAGE,16,false));outer.addView(row,new LinearLayout.LayoutParams(-1,-2));
            ReaderIconButton glyph=new ReaderIconButton(context,PocketTheme.symbol(label),label,null,()->{});glyph.setClickable(false);glyph.setFocusable(false);glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(glyph,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView text=new TextView(context);text.setId(android.R.id.text1);text.setText(items[at]);text.setTextSize(16);text.setTextColor(destructive?PocketTheme.RED:PocketTheme.INK);text.setPadding(dp(10),dp(4),dp(8),dp(4));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=new TextView(context);arrow.setText("›");arrow.setTextSize(22);arrow.setTextColor(PocketTheme.MUTED);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow);return outer;
        }
    }
}
