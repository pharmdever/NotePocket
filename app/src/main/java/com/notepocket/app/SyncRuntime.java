package com.notepocket.app;
import java.util.concurrent.atomic.AtomicBoolean;
/** A live editor must never be overwritten by a background merge. */
final class SyncRuntime {
    static final Object EDIT_LOCK=new Object();static int editors;
    static final AtomicBoolean RUNNING=new AtomicBoolean();
    static void enterEditor() {synchronized(EDIT_LOCK) {editors++;}}
    static void leaveEditor() {synchronized(EDIT_LOCK) {editors=Math.max(0,editors-1);}}
    static void checkEditor() throws Deferred {if(editors>0) throw new Deferred();}
    static final class Deferred extends Exception {Deferred() {super("มีข้อมูลรอซิงก์ · ปิดหน้าแก้ไขโน้ตก่อน");}}
}
