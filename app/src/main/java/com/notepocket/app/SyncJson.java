package com.notepocket.app;

import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Canonical JSON and bounded file hashing; no clock-based conflict decisions. */
final class SyncJson {
    static String canonical(Object value) throws JSONException {
        if(value instanceof JSONObject) {JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();o.keys().forEachRemaining(keys::add);Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String k:keys) {if(b.length()>1) b.append(',');b.append(JSONObject.quote(k)).append(':').append(canonical(o.get(k)));}return b.append('}').toString();}
        if(value instanceof JSONArray) {JSONArray a=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++) {if(i>0) b.append(',');b.append(canonical(a.get(i)));}return b.append(']').toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
    static String hash(String value) {return digest(value.getBytes(StandardCharsets.UTF_8));}
    static String digest(byte[] bytes) {try {return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}}
    static String hex(byte[] bytes) {StringBuilder b=new StringBuilder();for(byte v:bytes) b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    static String fileHash(File file,SyncCancel cancel) throws Exception {MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];try(InputStream in=new FileInputStream(file)) {int n;while((n=in.read(buffer))!=-1) {cancel.check();md.update(buffer,0,n);}}return hex(md.digest());}
    static JSONObject copy(JSONObject o) throws JSONException {return new JSONObject(canonical(o));}
    static String fingerprint(JSONObject o) throws JSONException {if(o==null) return "~";JSONObject c=copy(o);c.remove("updated");return hash(canonical(c));}
    static String text(JSONObject o,String key,int max) throws JSONException {Object v=o.get(key);if(!(v instanceof String)||((String)v).length()>max) throw new JSONException("Invalid "+key);return (String)v;}
    static String id(String id) throws JSONException {if(!id.matches("[A-Za-z0-9_-]{1,100}")) throw new JSONException("Invalid identifier");return id;}
    static String fileId(String id) throws JSONException {if(!id.matches("[A-Za-z0-9-]{1,80}")) throw new JSONException("Invalid file identifier");return id;}
}
