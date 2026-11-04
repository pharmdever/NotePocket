package com.notepocket.app;
import android.content.*;
import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ReaderRegressionTest {
    @Test public void highlightPreservesBlackInPreviewAndSelectionCreatesInk() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Bitmap page=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);page.eraseColor(Color.WHITE);Canvas original=new Canvas(page);Paint black=new Paint();black.setColor(Color.BLACK);original.drawRect(100,280,300,320,black);
            PdfPageView view=new PdfPageView(c);view.measure(android.view.View.MeasureSpec.makeMeasureSpec(400,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(600,1073741824));view.layout(0,0,400,600);view.setPage(page);view.setDrawing(Ink.encode(Arrays.asList(new Ink(true,0xffFFD54F,.08f,new float[]{.1f,.5f,.9f,.5f}))));
            Bitmap output=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);view.draw(new Canvas(output));assertEquals(Color.BLACK,output.getPixel(200,300));int yellow=output.getPixel(50,300);assertTrue(Color.red(yellow)>240&&Color.green(yellow)>210&&Color.blue(yellow)<240);
            view.setWords(Arrays.asList(new PdfWords.Glyph("Hello",new RectF(.1f,.1f,.4f,.15f))));view.selectAt(.2f,.12f);assertEquals("Hello",view.selectedText());view.highlightSelection();assertEquals(2,Ink.decode(view.getDrawing()).size());page.recycle();output.recycle();
        });
    }
    @Test public void glyphPositionsUseCropAndAllPageRotations() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();PDFBoxResourceLoader.init(c);String id=UUID.randomUUID().toString();File file=new File(c.getFilesDir(),id);
        try {try(PDDocument pdf=new PDDocument()) {for(int r:new int[]{0,90,180,270}) {PDPage p=new PDPage();p.setCropBox(new PDRectangle(30,40,300,420));p.setRotation(r);pdf.addPage(p);try(PDPageContentStream out=new PDPageContentStream(pdf,p)) {out.beginText();out.setFont(PDType1Font.HELVETICA,20);out.newLineAtOffset(80,250);out.showText("Hello PDF");out.endText();}}pdf.save(file);}
            for(int page=0;page<4;page++) {List<PdfWords.Glyph> glyphs=PdfWords.read(c,id,page);assertFalse(glyphs.isEmpty());StringBuilder text=new StringBuilder();for(PdfWords.Glyph glyph:glyphs) {text.append(glyph.text);assertTrue(glyph.box.width()>0&&glyph.box.height()>0);assertTrue(glyph.box.left>=0&&glyph.box.bottom<=1);}assertTrue(text.toString().contains("Hello"));}
        } finally {file.delete();}
    }
    @Test public void importedFontAndColoredThaiTextSurviveBackup() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.deleteDatabase("font-test.db");List<File> created=new ArrayList<>();
        try(Store store=new Store(c,"font-test.db")) {String font;try(InputStream in=new FileInputStream(UiFlowTest.systemFont())) {font=Fonts.importFont(c,store,in,"ThaiTest.ttf");}created.add(new File(c.getFilesDir(),font));String doc=UUID.randomUUID().toString();File file=new File(c.getFilesDir(),doc);created.add(file);try(OutputStream out=new FileOutputStream(file)) {out.write(7);}ContentValues d=new ContentValues();d.put("id",doc);d.put("name","test.pdf");d.put("mime","application/pdf");d.put("size",1);store.getWritableDatabase().insertOrThrow("docs",null,d);
            store.saveText(doc,0,TextBox.encode(Arrays.asList(new TextBox("font-box","ข้อความไทย",font,.2f,.3f,.6f,.04f,0xff245BCC))));ByteArrayOutputStream bytes=new ByteArrayOutputStream();Backup.write(c,store,bytes);Backup.restore(c,store,new ByteArrayInputStream(bytes.toByteArray()));assertEquals(2,store.query("SELECT id FROM fonts").size());assertEquals(2,store.query("SELECT id FROM docs").size());List<ContentValues> texts=store.query("SELECT boxes FROM page_text");TextBox restored=TextBox.decode(texts.get(1).getAsString("boxes")).get(0);assertEquals("ข้อความไทย",restored.text);assertNotEquals(font,restored.font);assertEquals(0xff245BCC,restored.color);
            for(ContentValues row:store.query("SELECT id FROM fonts UNION SELECT id FROM docs")) created.add(new File(c.getFilesDir(),row.getAsString("id")));
        } finally {for(File f:created) f.delete();c.deleteDatabase("font-test.db");}
    }
}
