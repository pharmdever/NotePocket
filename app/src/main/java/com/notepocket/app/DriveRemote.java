package com.notepocket.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Drive v3 appDataFolder only. Tokens are never persisted or sent to redirects. */
final class DriveRemote implements SyncRemote {
    static final String SCOPE="https://www.googleapis.com/auth/drive.appdata";
    private static final String API="https://www.googleapis.com/drive/v3/",UPLOAD="https://www.googleapis.com/upload/drive/v3/";
    private final String token;private final SyncCancel cancel;
    DriveRemote(String token,SyncCancel cancel) {if(token==null||token.isEmpty()) throw new IllegalArgumentException("Missing token");this.token=token;this.cancel=cancel;}
    static final class Failure extends IOException {final int code;Failure(int code) {super(code==401?"สิทธิ์ Google หมดอายุ กรุณาเชื่อมบัญชีอีกครั้ง":code==403?"Drive ยังไม่อนุญาต ตรวจ OAuth, พื้นที่ และโควตาบัญชี":code==429?"Drive จำกัดการใช้งานชั่วคราว กรุณาลองใหม่":code==404?"Drive ยังแสดงข้อมูลไม่ครบ กรุณาลองใหม่":"เชื่อม Drive ไม่สำเร็จ ("+code+")");this.code=code;}}
    @Override public JSONObject account() throws Exception {return json("GET",API+"about?fields="+enc("user(permissionId,emailAddress,displayName)"),null);}
    @Override public List<Item> list() throws Exception {
        List<Item> result=new ArrayList<>();String page="";Set<String> seen=new HashSet<>();do {cancel.check();String url=API+"files?spaces=appDataFolder&pageSize=1000&q="+enc("trashed = false and (appProperties has { key='npType' and value='journal-v1' } or appProperties has { key='npType' and value='blob-v1' })")+"&fields="+enc("nextPageToken,files(id,name,appProperties)")+(page.isEmpty()?"":"&pageToken="+enc(page));JSONObject answer=json("GET",url,null);JSONArray files=answer.getJSONArray("files");for(int i=0;i<files.length();i++) {JSONObject f=files.getJSONObject(i),p=f.optJSONObject("appProperties");if(p!=null) result.add(new Item(f.getString("id"),f.optString("name"),p.optString("npType"),p.optString("sha")));}if(result.size()>100000) throw new IOException("ประวัติบน Drive ใหญ่เกินขอบเขตรุ่นนี้");page=answer.optString("nextPageToken");if(!page.isEmpty()&&!seen.add(page)) throw new IOException("Drive pagination repeated");}while(!page.isEmpty());return result;
    }
    @Override public JSONObject journal(String fileId) throws Exception {return json("GET",API+"files/"+safeId(fileId)+"?alt=media",null);}
    @Override public String publish(JSONObject commit) throws Exception {
        byte[] data=commit.toString().getBytes(StandardCharsets.UTF_8);if(data.length>8*1024*1024) throw new IOException("Journal too large");String boundary="np_"+UUID.randomUUID();JSONObject meta=metadata("journal-"+UUID.randomUUID()+".json","journal-v1","");ByteArrayOutputStream body=new ByteArrayOutputStream();body.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+meta+"\r\n--"+boundary+"\r\nContent-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(data);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpURLConnection c=open(UPLOAD+"files?uploadType=multipart&fields=id","POST");try {c.setRequestProperty("Content-Type","multipart/related; boundary="+boundary);send(c,body.toByteArray());require(c);return readJson(c).getString("id");}finally {c.disconnect();}
    }
    @Override public String uploadBlob(String sha,File file) throws Exception {
        if(!sha.matches("[a-f0-9]{64}")||file.length()>512L*1024*1024) throw new IOException("Invalid blob");HttpURLConnection start=open(UPLOAD+"files?uploadType=resumable&fields=id","POST");String location;
        try {start.setRequestProperty("Content-Type","application/json; charset=UTF-8");start.setRequestProperty("X-Upload-Content-Type","application/octet-stream");start.setRequestProperty("X-Upload-Content-Length",Long.toString(file.length()));send(start,metadata("blob-"+sha,"blob-v1",sha).toString().getBytes(StandardCharsets.UTF_8));require(start);location=start.getHeaderField("Location");if(location==null) throw new IOException("Missing upload session");trusted(location);}finally {start.disconnect();}
        try(RandomAccessFile in=new RandomAccessFile(file,"r")) {long at=0,total=file.length();do {cancel.check();int length=(int)Math.min(8L*1024*1024,total-at);HttpURLConnection c=open(location,"PUT");try {c.setRequestProperty("Content-Type","application/octet-stream");c.setRequestProperty("Content-Range",total==0?"bytes */0":"bytes "+at+"-"+(at+length-1)+"/"+total);c.setDoOutput(true);c.setFixedLengthStreamingMode(length);try(OutputStream out=c.getOutputStream()) {byte[] buffer=new byte[65536];int left=length;in.seek(at);while(left>0) {cancel.check();int n=in.read(buffer,0,Math.min(buffer.length,left));if(n<0) throw new EOFException();out.write(buffer,0,n);left-=n;}}
                    int status=c.getResponseCode();if(status==200||status==201) return readJson(c).getString("id");if(status!=308) throw new Failure(status);String range=c.getHeaderField("Range");if(range==null||!range.matches("bytes=0-[0-9]+")) throw new IOException("Invalid upload acknowledgement");long next=Long.parseLong(range.substring(8))+1;if(next<=at||next>at+length) throw new IOException("Invalid upload offset");at=next;
                } finally {c.disconnect();}}while(at<total);throw new IOException("Incomplete upload response");}
    }
    @Override public void downloadBlob(String id,File target,long size) throws Exception {
        if(size<0||size>512L*1024*1024) throw new IOException("Invalid download size");HttpURLConnection c=open(API+"files/"+safeId(id)+"?alt=media","GET");try {require(c);long advertised=c.getContentLengthLong();if(advertised>=0&&advertised!=size) throw new IOException("File size mismatch");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(target)) {byte[] buffer=new byte[65536];long count=0;int n;while((n=in.read(buffer))!=-1) {cancel.check();count+=n;if(count>size) throw new IOException("File too large");out.write(buffer,0,n);}if(count!=size) throw new IOException("File incomplete");}}finally {c.disconnect();}
    }
    private JSONObject metadata(String name,String type,String sha) throws JSONException {return new JSONObject().put("name",name).put("parents",new JSONArray().put("appDataFolder")).put("appProperties",new JSONObject().put("npType",type).put("sha",sha));}
    private JSONObject json(String method,String url,byte[] body) throws Exception {HttpURLConnection c=open(url,method);try {if(body!=null) {c.setRequestProperty("Content-Type","application/json");send(c,body);}require(c);return readJson(c);}finally {c.disconnect();}}
    private JSONObject readJson(HttpURLConnection c) throws Exception {try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1) {cancel.check();if(out.size()+n>8*1024*1024) throw new IOException("Drive response too large");out.write(buffer,0,n);}return new JSONObject(out.toString("UTF-8"));}}
    private HttpURLConnection open(String url,String method) throws Exception {cancel.check();trusted(url);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");cancel.connection(c);return c;}
    static void trusted(String value) throws IOException {try {URL url=new URL(value);if(!url.getProtocol().equals("https")||!url.getHost().equals("www.googleapis.com")||url.getPort()!=-1&&url.getPort()!=443||url.getUserInfo()!=null||!(url.getPath().startsWith("/drive/v3/")||url.getPath().startsWith("/upload/drive/v3/"))) throw new IOException("Untrusted Drive URL");}catch(MalformedURLException e) {throw new IOException("Invalid Drive URL");}}
    private void send(HttpURLConnection c,byte[] bytes) throws Exception {c.setDoOutput(true);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()) {cancel.check();out.write(bytes);}}
    private void require(HttpURLConnection c) throws Exception {cancel.check();int code=c.getResponseCode();if(code<200||code>=300) throw new Failure(code);}
    private static String enc(String value) throws UnsupportedEncodingException {return URLEncoder.encode(value,"UTF-8");}
    private static String safeId(String id) throws IOException {if(!id.matches("[A-Za-z0-9_-]{1,200}")) throw new IOException("Invalid Drive file ID");return id;}
}
