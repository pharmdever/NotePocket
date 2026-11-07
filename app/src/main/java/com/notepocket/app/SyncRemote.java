package com.notepocket.app;
import org.json.*;
import java.io.*;
import java.util.*;
/** Injectable transport. Immutable journal files make concurrent device uploads independent. */
interface SyncRemote {
    final class Item {final String id,name,type,sha;Item(String id,String name,String type,String sha) {this.id=id;this.name=name;this.type=type;this.sha=sha;}}
    JSONObject account() throws Exception;
    List<Item> list() throws Exception;
    JSONObject journal(String fileId) throws Exception;
    String publish(JSONObject commit) throws Exception;
    String uploadBlob(String sha,File file) throws Exception;
    void downloadBlob(String id,File destination,long size) throws Exception;
}
