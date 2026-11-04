package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.util.Matrix;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode;
import java.io.*;
import java.util.*;

/** Appends vector paths to a copy; preserves original text, images and page structure. */
public final class AnnotatedPdf {
    private AnnotatedPdf() {}
    public static void write(Context context,Store store,String docId,OutputStream output) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        MemoryUsageSetting memory=MemoryUsageSetting.setupTempFileOnly();memory.setTempDir(context.getCacheDir());
        File temp=File.createTempFile("annotated-",".pdf",context.getCacheDir());
        try(PDDocument document=PDDocument.load(new File(context.getFilesDir(),docId),memory)) {
            if(!document.getCurrentAccessPermission().canModify()) throw new IOException("Document does not allow modification");
            Set<Integer> pages=new TreeSet<>();for(ContentValues row:store.query("SELECT page FROM page_ink WHERE doc_id=? UNION SELECT page FROM page_text WHERE doc_id=?",docId,docId)) pages.add(row.getAsInteger("page"));
            for(int index:pages) {if(index<0||index>=document.getNumberOfPages()) throw new IOException("Invalid annotation page");
                PDPage page=document.getPage(index);List<Ink> strokes=Ink.decode(store.drawing(docId,index));
                List<TextBox> boxes=TextBox.decode(store.textBoxes(docId,index));
                if(strokes.isEmpty()&&boxes.isEmpty()) continue;
                try(PDPageContentStream stream=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true,true)) {
                    stream.setLineCapStyle(1);stream.setLineJoinStyle(1);
                    for(Ink stroke:strokes) {
                        stream.saveGraphicsState();
                        PDExtendedGraphicsState state=new PDExtendedGraphicsState();state.setStrokingAlphaConstant(stroke.highlight?context.getSharedPreferences("reader",0).getInt("opacity",24)/100f:1f);
                        state.setBlendMode(stroke.highlight?BlendMode.MULTIPLY:BlendMode.NORMAL);stream.setGraphicsStateParameters(state);
                        int color=stroke.color;stream.setStrokingColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);
                        PDRectangle crop=page.getCropBox();stream.setLineWidth(stroke.width*Math.min(crop.getWidth(),crop.getHeight()));
                        float[] xy=point(page,stroke.points[0],stroke.points[1]);stream.moveTo(xy[0],xy[1]);
                        if(stroke.points.length==2) stream.lineTo(xy[0]+0.001f,xy[1]);
                        else for(int i=2;i<stroke.points.length;i+=2) {xy=point(page,stroke.points[i],stroke.points[i+1]);stream.lineTo(xy[0],xy[1]);}
                        stream.stroke();stream.restoreGraphicsState();
                    }
                    if(!boxes.isEmpty()) {
                        PDRectangle crop=page.getCropBox();boolean rotated=page.getRotation()%180!=0;float w=rotated?crop.getHeight():crop.getWidth(),h=rotated?crop.getWidth():crop.getHeight();
                        float scale=Math.min(3,Math.min(2200/w,3000/h));Bitmap layer=Bitmap.createBitmap(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),Bitmap.Config.ARGB_8888);
                        try {Canvas canvas=new Canvas(layer);RectF bounds=new RectF(0,0,layer.getWidth(),layer.getHeight());for(TextBox box:boxes) box.draw(context,canvas,bounds);
                            float[] base=point(page,0,1),right=point(page,1,1),top=point(page,0,0);
                            stream.drawImage(LosslessFactory.createFromImage(document,layer),new Matrix(right[0]-base[0],right[1]-base[1],top[0]-base[0],top[1]-base[1],base[0],base[1]));
                        } finally {layer.recycle();}
                    }
                }
            }
            document.save(temp);
            try(InputStream input=new FileInputStream(temp)) {Backup.copy(input,output,1024L*1024*1024);}
        } finally {temp.delete();}
    }
    static float[] point(PDPage page,float u,float v) {
        PDRectangle crop=page.getCropBox();float x,y;int rotation=((page.getRotation()%360)+360)%360;
        if(rotation==90) {x=v;y=u;}
        else if(rotation==180) {x=1-u;y=v;}
        else if(rotation==270) {x=1-v;y=1-u;}
        else {x=u;y=1-v;}
        return new float[]{crop.getLowerLeftX()+x*crop.getWidth(),crop.getLowerLeftY()+y*crop.getHeight()};
    }
}
