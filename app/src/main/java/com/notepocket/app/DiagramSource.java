package com.notepocket.app;

import java.util.*;
import java.util.regex.*;

/** Source stays ordinary note text, preserving undo, backups and Drive compatibility. */
public final class DiagramSource {
    public static final int MAX_CODE=50000,MAX_BLOCKS=20;
    private static final Pattern FENCE=Pattern.compile("(?m)^ {0,3}(`{3,}|~{3,})[ \\t]*mermaid[ \\t]*\\r?$");
    private static final Pattern TYPE=Pattern.compile("^(flowchart|graph|sequenceDiagram|classDiagram|stateDiagram(?:-v2)?|erDiagram|gantt|pie|mindmap|timeline|gitGraph|journey|quadrantChart|requirementDiagram)(?:[ \\t\\r\\n;]|$)");
    public static final String SAMPLE="flowchart TD\n    A[\"Plant Growth\"] --> B[\"Primary growth\"]\n    A --> C[\"Secondary growth\"]\n    B --> D[\"Apical meristem\"]\n    B --> E[\"Intercalary meristem\"]\n    D --> F[\"Increase length\"]\n    E --> F\n    C --> G[\"Lateral meristem\"]\n    G --> H[\"Increase girth\"]\n    G --> I[\"Vascular cambium\"]\n    G --> J[\"Cork cambium\"]";
    public static final class Block {
        public final int start,end;public final String original,code;
        Block(String note,int start,int end,String code) {this.start=start;this.end=end;this.original=note.substring(start,end);this.code=code;}
    }
    public static List<Block> find(String note) {
        List<Block> blocks=new ArrayList<>();Matcher open=FENCE.matcher(note);int from=0;
        while(blocks.size()<MAX_BLOCKS&&open.find(from)) {
            String delimiter=open.group(1);Pattern close=Pattern.compile("(?m)^ {0,3}"+Pattern.quote(delimiter.substring(0,1))+"{"+delimiter.length()+",}[ \\t]*\\r?$");
            Matcher end=close.matcher(note);int content=open.end();if(content<note.length()&&note.charAt(content)=='\n')content++;
            if(!end.find(content))break;
            blocks.add(new Block(note,open.start(),end.end(),note.substring(content,end.start()).trim()));from=end.end();
        }
        if(blocks.isEmpty()&&TYPE.matcher(note.trim()).find())blocks.add(new Block(note,0,note.length(),note.trim()));
        return blocks;
    }
    public static String clean(String pasted) {
        String code=pasted.trim();List<Block> blocks=find(code);
        if(blocks.size()==1&&blocks.get(0).start==0&&blocks.get(0).end==code.length())code=blocks.get(0).code;
        if(code.isEmpty())throw new IllegalArgumentException("วางโค้ด Mermaid ก่อน หรือกดใช้ตัวอย่าง");
        if(code.length()>MAX_CODE)throw new IllegalArgumentException("โค้ดแผนภาพยาวเกิน 50,000 ตัวอักษร กรุณาแบ่งแผนภาพ");
        if(code.contains("%%{")||code.startsWith("---"))throw new IllegalArgumentException("รุ่นนี้ยังไม่รองรับคำสั่งตั้งค่า init หรือ frontmatter กรุณาใช้เฉพาะโค้ดแผนภาพ");
        if(!TYPE.matcher(code).find())throw new IllegalArgumentException("ต้องเป็นโค้ด Mermaid เช่น flowchart TD หรือ sequenceDiagram");
        return code;
    }
    public static String fenced(String code) {
        String fence="```";while(code.contains(fence))fence+="`";
        return fence+"mermaid\n"+code+"\n"+fence;
    }
    private DiagramSource() {}
}
