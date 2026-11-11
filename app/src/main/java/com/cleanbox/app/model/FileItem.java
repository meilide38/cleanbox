package com.cleanbox.app.model;

import java.io.File;

/** 单条可清理条目。 */
public class FileItem {

    public final File file;
    public final long size;
    public final boolean directory;
    public boolean selected;
    public String note;

    public FileItem(File file, long size, boolean directory) {
        this(file, size, directory, null);
    }

    public FileItem(File file, long size, boolean directory, String note) {
        this.file = file;
        this.size = size;
        this.directory = directory;
        this.note = note;
    }
}
