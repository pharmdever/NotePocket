package com.notepocket.app;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Canned HTTPS connections test protocol handling without granting access to a real account. */
@RunWith(AndroidJUnit4.class)
public class DriveHttpTest {
    private static final class Reply extends HttpURLConnection {
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();final Map<String,String> headers=new HashMap<>();byte[] body;int status;long length=-1;
        Reply(int status,String body) throws MalformedURLException {super(new URL("https://www.googleapis.com/drive/v3/files"));this.status=status;this.body=body.getBytes(StandardCharsets.UTF_8);}
        public void connect() {}public void disconnect() {}public boolean usingProxy() {return false;}
        @Override public OutputStream getOutputStream() {return sent;}@Override public InputStream getInputStream() {return new ByteArrayInputStream(body);}
        @Override public int getResponseCode() {return status;}@Override public String getHeaderField(String key) {return headers.get(key);}@Override public long getContentLengthLong() {return length;}
    }
    @Test public void metadataPaginationStaysInAppDataAndDoesNotPutTokenInUrl() throws Exception {
        List<URL> urls=new ArrayList<>();Reply first=new Reply(200,"{\"nextPageToken\":\"next-token\",\"files\":[{\"id\":\"j_one\",\"appProperties\":{\"npType\":\"journal-v1\"}}]}");Reply second=new Reply(200,"{\"files\":[{\"id\":\"b_one\",\"appProperties\":{\"npType\":\"blob-v1\",\"sha\":\"abc\"}}]}");ArrayDeque<Reply> replies=new ArrayDeque<>(Arrays.asList(first,second));DriveRemote remote=new DriveRemote("qa-token",new SyncCancel(),url->{urls.add(url);return replies.remove();});List<SyncRemote.Item> files=remote.list();assertEquals(2,files.size());assertEquals(2,urls.size());assertTrue(urls.get(0).getQuery().contains("spaces=appDataFolder"));assertTrue(urls.get(1).getQuery().contains("pageToken=next-token"));for(URL url:urls) assertFalse(url.toString().contains("qa-token"));assertEquals("Bearer qa-token",first.getRequestProperty("Authorization"));assertFalse(first.getInstanceFollowRedirects());
    }
    @Test public void resumableUploadHonorsAcknowledgedEightMegabyteChunks() throws Exception {
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"drive-http-"+UUID.randomUUID());try {
            try(RandomAccessFile out=new RandomAccessFile(file,"rw")) {out.setLength(9L*1024*1024);}Reply start=new Reply(200,"{}");start.headers.put("Location","https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=session");Reply part=new Reply(308,"");part.headers.put("Range","bytes=0-8388607");Reply end=new Reply(200,"{\"id\":\"blob-final\"}");ArrayDeque<Reply> replies=new ArrayDeque<>(Arrays.asList(start,part,end));DriveRemote remote=new DriveRemote("qa-token",new SyncCancel(),url->replies.remove());assertEquals("blob-final",remote.uploadBlob(SyncJson.fileHash(file,new SyncCancel()),file));assertEquals("bytes 0-8388607/9437184",part.getRequestProperty("Content-Range"));assertEquals("bytes 8388608-9437183/9437184",end.getRequestProperty("Content-Range"));assertEquals(8*1024*1024,part.sent.size());assertEquals(1024*1024,end.sent.size());JSONObject metadata=new JSONObject(start.sent.toString("UTF-8"));assertEquals("appDataFolder",metadata.getJSONArray("parents").getString(0));assertEquals("blob-v1",metadata.getJSONObject("appProperties").getString("npType"));
        }finally {file.delete();}
    }
    @Test public void redirectsAndUnsafeLocationsNeverReceiveAuthorization() throws Exception {
        for(String url:new String[]{"http://www.googleapis.com/drive/v3/files","https://evil.example/drive/v3/files","https://www.googleapis.com@evil.example/drive/v3/files","https://www.googleapis.com:444/drive/v3/files","https://www.googleapis.com/other/path"}) {try {DriveRemote.trusted(url);fail(url);}catch(IOException expected) {}}
        Reply moved=new Reply(302,"{}");moved.headers.put("Location","https://evil.example/steal");int[] requests={0};DriveRemote remote=new DriveRemote("qa-token",new SyncCancel(),url->{requests[0]++;return moved;});try {remote.account();fail();}catch(DriveRemote.Failure expected) {assertEquals(302,expected.code);}assertEquals(1,requests[0]);assertFalse(moved.getInstanceFollowRedirects());
    }
    @Test public void downloadBoundsAndAuthenticationErrorsAreExplicit() throws Exception {
        Reply tooBig=new Reply(200,"12345");File target=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"drive-http-overflow");try {DriveRemote remote=new DriveRemote("qa-token",new SyncCancel(),url->tooBig);try {remote.downloadBlob("blob",target,4);fail();}catch(IOException expected) {}assertTrue(target.length()<=4);}finally {target.delete();}
        DriveRemote unauthorized=new DriveRemote("qa-token",new SyncCancel(),url->new Reply(401,"{}"));try {unauthorized.account();fail();}catch(DriveRemote.Failure expected) {assertEquals(401,expected.code);}
    }
}
