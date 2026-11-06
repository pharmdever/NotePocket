package com.notepocket.app;

import android.text.*;
import java.util.*;

/** Frozen UI data: workers never read a live Editable or mutable table. */
public final class NoteSnapshot {
    public final String title,folder,soap;
    public final Spanned body;
    public final List<NoteTable> tables;
    public final long revision;
    public NoteSnapshot(String title,Spanned body,String folder,List<NoteTable> tables,String soap,long revision) {this.title=title;this.body=NoteFormat.snapshot(body);this.folder=folder;this.tables=NoteTable.copy(tables);this.soap=soap;this.revision=revision;}
}
