package com.notepocket.app;

import android.text.Editable;
import java.util.regex.*;

public final class NoteLists {
    private static final Pattern PREFIX=Pattern.compile("^(\\s*)(☐ |☑ |• |[-*] |(\\d{1,6})\\. )");
    private NoteLists() {}
    public static boolean afterEnter(Editable text,int at,int before,int count) {
        if(before!=0||count!=1||at<0||at>=text.length()||text.charAt(at)!='\n') return false;
        int start=text.toString().lastIndexOf('\n',at-1)+1;String line=text.subSequence(start,at).toString();Matcher m=PREFIX.matcher(line);if(!m.find()) return false;
        if(line.substring(m.end()).trim().isEmpty()) {text.delete(start,at+1);return true;}
        String prefix=m.group(2);if(m.group(3)!=null) {long next=Long.parseLong(m.group(3))+1;if(next>999999) return false;prefix=next+". ";}else if(prefix.equals("☑ ")) prefix="☐ ";
        text.insert(at+1,m.group(1)+prefix);return true;
    }
    public static void toggle(Editable text,int lo,int hi,String kind) {
        int start=text.toString().lastIndexOf('\n',Math.max(-1,lo-1))+1,end=text.toString().indexOf('\n',Math.max(start,hi>lo?hi-1:hi));if(end<0) end=text.length();String[] lines=text.subSequence(start,end).toString().split("\n",-1);
        int[] remove=new int[lines.length];boolean all=true;
        for(int i=0;i<lines.length;i++) {Matcher m=PREFIX.matcher(lines[i]);if(m.find()) {remove[i]=m.end();boolean same=kind.equals("number")?m.group(3)!=null:kind.equals("check")?(m.group(2).equals("☐ ")||m.group(2).equals("☑ ")):m.group(2).equals("• ");if(!same) all=false;}else all=false;}
        int offset=start;for(int i=0;i<lines.length;i++) {String add=all?"":kind.equals("number")?(i+1)+". ":kind.equals("check")?"☐ ":"• ";text.replace(offset,offset+remove[i],add);offset+=lines[i].length()-remove[i]+add.length()+1;}
    }
}
