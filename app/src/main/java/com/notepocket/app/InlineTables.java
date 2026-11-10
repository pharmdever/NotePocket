package com.notepocket.app;

import java.util.*;
import java.util.regex.*;

/** Table data keeps its stable ID; prose contains only an atomic placement reference. */
final class InlineTables {
    private static final Pattern REF=Pattern.compile("(?m)^ {0,3}\\[ตาราง\\]\\(notepocket-table:([A-Za-z0-9-]{1,80})\\)[ \\t]*\\r?$");
    private static final Pattern FENCE=Pattern.compile("(?m)^ {0,3}(`{3,}|~{3,})([^\\r\\n]*)\\r?$");
    static final class Block {final String id;final int start,end;Block(Matcher m){id=m.group(1);start=m.start();end=m.end();}}
    static String marker(String id){if(!id.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException();return "[ตาราง](notepocket-table:"+id+")";}
    static List<Block> find(String text){List<Block> out=new ArrayList<>();if(!text.contains("(notepocket-table:"))return out;List<int[]> code=new ArrayList<>();Matcher f=FENCE.matcher(text);int from=0;while(f.find(from)){String ticks=f.group(1);Matcher end=Pattern.compile("(?m)^ {0,3}"+Pattern.quote(ticks.substring(0,1))+"{"+ticks.length()+",}[ \\t]*\\r?$").matcher(text);if(end.find(f.end())){code.add(new int[]{f.start(),end.end()});from=end.end();}else{code.add(new int[]{f.start(),text.length()});break;}}Matcher m=REF.matcher(text);while(m.find()){boolean skip=false;for(int[] r:code)if(m.start()>=r[0]&&m.start()<r[1]){skip=true;break;}if(!skip)out.add(new Block(m));}return out;}
    static Set<String> ids(String text){Set<String> out=new HashSet<>();for(Block b:find(text))out.add(b.id);return out;}
    private InlineTables(){}
}
