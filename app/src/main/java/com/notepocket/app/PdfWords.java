package com.notepocket.app;

import android.content.Context;
import android.graphics.RectF;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.text.*;
import java.io.*;
import java.util.*;

public final class PdfWords {
    public static final class Glyph {
        public final String text;public final RectF box;
        public Glyph(String text,RectF box) {this.text=text;this.box=box;}
    }
    private PdfWords() {}
    public static List<Glyph> read(Context context,String docId,int pageIndex) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());List<Glyph> glyphs=new ArrayList<>();Set<String> seen=new HashSet<>();
        MemoryUsageSetting memory=MemoryUsageSetting.setupTempFileOnly();memory.setTempDir(context.getCacheDir());
        try(PDDocument pdf=PDDocument.load(new File(context.getFilesDir(),docId),memory)) {
            if(!pdf.getCurrentAccessPermission().canExtractContent()) throw new IOException("Text extraction not permitted");
            if(pageIndex<0||pageIndex>=pdf.getNumberOfPages()) throw new IOException();PDPage page=pdf.getPage(pageIndex);
            PDFTextStripper stripper=new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition position) {
                    if(glyphs.size()>=30000||position.getUnicode()==null||position.getUnicode().isEmpty()) return;
                    PDRectangle crop=page.getCropBox();float x=position.getTextMatrix().getTranslateX(),y=position.getTextMatrix().getTranslateY();
                    float w=Math.max(.5f,position.getWidthDirAdj()),h=Math.max(1,position.getHeightDir());double angle=Math.toRadians(position.getDir());float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                    RectF bounds=new RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
                    for(float along:new float[]{0,w}) for(float high:new float[]{-h*.2f,h*1.05f}) {
                        float[] p=display(page,x+along*dx-high*dy,y+along*dy+high*dx);
                        bounds.left=Math.min(bounds.left,p[0]);bounds.right=Math.max(bounds.right,p[0]);bounds.top=Math.min(bounds.top,p[1]);bounds.bottom=Math.max(bounds.bottom,p[1]);
                    }
                    if(!bounds.intersect(0,0,1,1)) return;
                    String key=position.getUnicode()+Math.round(bounds.left*10000)+":"+Math.round(bounds.top*10000);
                    if(seen.add(key)) glyphs.add(new Glyph(position.getUnicode(),bounds));
                }
            };
            stripper.setStartPage(pageIndex+1);stripper.setEndPage(pageIndex+1);stripper.getText(pdf);
        }
        return glyphs;
    }
    static float[] display(PDPage page,float x,float y) {
        PDRectangle crop=page.getCropBox();float u=x/crop.getWidth(),v=y/crop.getHeight();int r=((page.getRotation()%360)+360)%360;
        if(r==90) return new float[]{v,u};if(r==180) return new float[]{1-u,v};if(r==270) return new float[]{1-v,1-u};return new float[]{u,1-v};
    }
}
