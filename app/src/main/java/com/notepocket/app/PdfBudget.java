package com.notepocket.app;

import android.graphics.*;
import android.graphics.pdf.PdfRenderer;

/** One page at a time; bounds allocations by the process heap, not just by PDF page dimensions. */
public final class PdfBudget {
    private PdfBudget() {}
    public static long pixels(long heap) {return Math.min(16L*1024*1024,Math.max(4L*1024*1024,heap/12))/4;}
    public static int[] dimensions(int width,int height,long pixels) {double scale=Math.min(3,Math.min(2200d/width,Math.min(3000d/height,Math.sqrt(pixels/((double)width*height)))));return new int[]{Math.max(1,(int)Math.floor(width*scale)),Math.max(1,(int)Math.floor(height*scale))};}
    public static Bitmap render(PdfRenderer.Page page) {long budget=pixels(Runtime.getRuntime().maxMemory());OutOfMemoryError last=null;for(int attempt=0;attempt<3;attempt++) {Bitmap bitmap=null;try {int[] size=dimensions(page.getWidth(),page.getHeight(),budget);bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return bitmap;}catch(OutOfMemoryError error) {if(bitmap!=null) bitmap.recycle();last=error;budget=Math.max(250000,budget/2);}catch(RuntimeException error) {if(bitmap!=null) bitmap.recycle();throw error;}}throw last;}
}
