package com.notepocket.app;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class InkExportTest {
    @Test public void exportedStrokesAlignWithAllRotationsAndPreserveTextAndSource() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();PDFBoxResourceLoader.init(context);
        context.deleteDatabase("export-test.db");String id=UUID.randomUUID().toString();File source=new File(context.getFilesDir(),id);File output=new File(context.getCacheDir(),"ink-export-test.pdf");
        try(Store store=new Store(context,"export-test.db")) {
            try(PDDocument pdf=new PDDocument()) {
                for(int rotation:new int[]{0,90,180,270}) {
                    PDPage page=new PDPage();page.setCropBox(new PDRectangle(30,40,300,420));page.setRotation(rotation);pdf.addPage(page);
                    try(PDPageContentStream stream=new PDPageContentStream(pdf,page)) {stream.beginText();stream.setFont(PDType1Font.HELVETICA,14);stream.newLineAtOffset(60,200);stream.showText("Searchable original");stream.endText();}
                }
                pdf.save(source);
            }
            byte[] before=digest(source);
            for(int page=0;page<4;page++) store.saveDrawing(id,page,Ink.encode(Arrays.asList(new Ink(false,0xffE13A3A,0.012f,new float[]{0.2f,0.25f,0.8f,0.25f}),new Ink(true,0xffFFD54F,0.028f,new float[]{0.2f,0.65f,0.8f,0.65f}))));
            try(OutputStream out=new FileOutputStream(output)) {AnnotatedPdf.write(context,store,id,out);}
            assertArrayEquals(before,digest(source));
            try(PDDocument pdf=PDDocument.load(output)) {assertEquals(4,pdf.getNumberOfPages());String text=new PDFTextStripper().getText(pdf);assertEquals(4,text.split("Searchable original",-1).length-1);}
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)) {
                for(int i=0;i<4;i++) try(PdfRenderer.Page page=pdf.openPage(i)) {
                    Bitmap bitmap=Bitmap.createBitmap(page.getWidth()*2,page.getHeight()*2,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    int pixel=bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/4);assertTrue("Red ink alignment failed at rotation "+i+": "+Integer.toHexString(pixel),Color.red(pixel)>180&&Color.green(pixel)<110&&Color.blue(pixel)<110);bitmap.recycle();
                }
            }
        } finally {source.delete();output.delete();context.deleteDatabase("export-test.db");}
    }
    @Test public void invalidCoordinatesAreRejected() {
        try {new Ink(false,0xff000000,0.004f,new float[]{Float.NaN,0.5f});fail("NaN accepted");}catch(IllegalArgumentException expected) {}
        try {Ink.decode("[{\"highlight\":false,\"color\":0,\"width\":0.004,\"points\":[-1,500]}]");fail("Outside page accepted");}catch(IllegalArgumentException expected) {}
    }
    private byte[] digest(File file) throws Exception {MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)) {byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1) hash.update(b,0,n);}return hash.digest();}
}
