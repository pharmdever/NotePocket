package com.notepocket.app;
import org.json.*;
import java.util.*;

/** A revision names all previous heads it replaces. Versions never depend on device clocks. */
final class SyncOp {
    static final int MAX_RECORD=1536*1024;
    final String rev,entity,kind;final List<String> parents;final JSONObject value;final long time;
    SyncOp(String rev,String entity,String kind,List<String> parents,JSONObject value,long time) throws JSONException {
        this.rev=SyncJson.id(rev);this.entity=SyncJson.id(entity);if(!Arrays.asList("note","folder","doc","font","page","reading").contains(kind)) throw new JSONException("Unknown record kind");this.kind=kind;
        this.parents=new ArrayList<>(parents);Collections.sort(this.parents);if(parents.size()>100||new HashSet<>(parents).size()!=parents.size()||parents.contains(rev)) throw new JSONException("Invalid parents");for(String p:parents) SyncJson.id(p);
        this.value=value==null?null:SyncJson.copy(value);this.time=time;validate();
        if(SyncJson.canonical(json()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_RECORD) throw new JSONException("Record too large to sync");
    }
    JSONObject json() throws JSONException {return new JSONObject().put("rev",rev).put("entity",entity).put("kind",kind).put("parents",new JSONArray(parents)).put("value",value==null?JSONObject.NULL:value).put("time",time);}
    static SyncOp parse(JSONObject o) throws JSONException {JSONArray a=o.getJSONArray("parents");List<String> p=new ArrayList<>();for(int i=0;i<a.length();i++) p.add(a.getString(i));return new SyncOp(o.getString("rev"),o.getString("entity"),o.getString("kind"),p,o.isNull("value")?null:o.getJSONObject("value"),o.getLong("time"));}
    String fingerprint() throws JSONException {return SyncJson.fingerprint(value);}
    String label() {if(value==null) return kind+" · deleted";String s=value.optString(kind.equals("note")?"title":kind.equals("folder")?"name":kind.equals("page")?"doc":"name",kind);return s.substring(0,Math.min(160,s.length()));}
    private void validate() throws JSONException {
        if(value==null) return;
        switch(kind) {
            case "note":SyncJson.text(value,"title",20000);SyncJson.text(value,"body",1000000);NoteInk.validateRich(SyncJson.text(value,"rich",2000000));folder(SyncJson.text(value,"folder",80));bit("pinned");bit("deleted");NoteTable.decode(SyncJson.text(value,"tables",2000000));new SoapDraft(SyncJson.text(value,"soap",500000));JSONArray docs=value.getJSONArray("docs");if(docs.length()>1000) throw new JSONException("Too many attachments");for(int i=0;i<docs.length();i++) SyncJson.fileId(docs.getString(i));break;
            case "folder":folder(SyncJson.text(value,"name",80));break;
            case "doc":case "font":SyncJson.fileId(value.getString("id"));SyncJson.text(value,"name",20000);long size=value.getLong("size");if(size<0||size>(kind.equals("font")?20L:512L)*1024*1024) throw new JSONException("Invalid file size");if(!value.getString("sha").matches("[a-f0-9]{64}")) throw new JSONException("Invalid checksum");if(kind.equals("doc")) SyncJson.text(value,"mime",200);break;
            case "page":SyncJson.fileId(value.getString("doc"));page();Ink.decode(SyncJson.text(value,"ink",2000000));TextBox.decode(SyncJson.text(value,"boxes",1000000));SyncJson.text(value,"body",500000);bit("bookmarked");break;
            case "reading":SyncJson.fileId(value.getString("doc"));page();break;
        }
        String expected=kind.equals("folder")?"l-"+SyncJson.hash(value.getString("name")):kind.equals("doc")?"d-"+value.getString("id"):kind.equals("font")?"f-"+value.getString("id"):kind.equals("page")?pageEntity(value.getString("doc"),value.getInt("page")):kind.equals("reading")?"r-"+value.getString("doc"):entity;
        if(!expected.equals(entity)||kind.equals("note")&&!entity.startsWith("n-")) throw new JSONException("Record identity mismatch");
    }
    static String pageEntity(String doc,int page) {return "p-"+SyncJson.hash(doc+":"+page);}
    private void page() throws JSONException {if(value.getInt("page")<0||value.getInt("page")>1000000) throw new JSONException("Invalid page");}
    private void bit(String key) throws JSONException {int v=value.getInt(key);if(v!=0&&v!=1) throw new JSONException("Invalid flag");}
    private static void folder(String name) throws JSONException {if(name.trim().isEmpty()) throw new JSONException("Invalid folder");}
}
