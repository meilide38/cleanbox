package com.cleanbox.app.scan;

import com.cleanbox.app.model.FileItem;
import com.cleanbox.app.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地文件扫描器。全部逻辑基于 {@link java.io.File}，不依赖任何数据库或云端规则。
 */
public final class Scanner {

    public interface Progress {
        void onStatus(String text);
    }

    private interface Visitor {
        void onFile(File file);
    }

    private static final int MAX_DEPTH = 24;
    private static final long MAX_ENTRIES = 400000L;
    private static final int MAX_RESULT = 500;

    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"};
    private static final String[] VIDEO_EXT = {"mp4", "mkv", "avi", "mov", "3gp", "wmv", "flv", "ts", "m4v"};
    private static final String[] AUDIO_EXT = {"mp3", "wav", "flac", "aac", "ogg", "m4a", "amr", "wma"};

    private Scanner() {
    }

    private static void walk(File dir, Visitor visitor, List<File> emptyDirs,
                             Progress progress, int depth, long[] counter) {
        if (dir == null || depth > MAX_DEPTH || counter[0] > MAX_ENTRIES) {
            return;
        }
        String path = dir.getAbsolutePath();
        // Android 11 起 /Android/data 与 /Android/obb 即使有全盘权限也不可读，直接跳过
        if (path.endsWith("/Android/data") || path.endsWith("/Android/obb")) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        if (children.length == 0) {
            if (depth > 0 && emptyDirs != null) {
                emptyDirs.add(dir);
            }
            return;
        }
        for (File child : children) {
            if (counter[0] > MAX_ENTRIES) {
                return;
            }
            String name = child.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (child.isDirectory()) {
                walk(child, visitor, emptyDirs, progress, depth + 1, counter);
            } else if (child.isFile()) {
                counter[0]++;
                if (counter[0] % 400 == 0 && progress != null) {
                    progress.onStatus("已扫描 " + counter[0] + " 个文件…");
                }
                visitor.onFile(child);
            }
        }
    }

    private static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static boolean matches(String ext, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<FileItem> sortAndTrim(List<FileItem> items) {
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                return Long.compare(b.size, a.size);
            }
        });
        ArrayList<FileItem> result = new ArrayList<>();
        for (int i = 0; i < items.size() && i < MAX_RESULT; i++) {
            result.add(items.get(i));
        }
        return result;
    }

    public static ArrayList<FileItem> scanLarge(long minBytes, Progress progress) {
        final List<FileItem> items = new ArrayList<>();
        walk(FileUtils.externalRoot(), new Visitor() {
            @Override
            public void onFile(File file) {
                if (file.length() >= minBytes) {
                    items.add(new FileItem(file, file.length(), false));
                }
            }
        }, null, progress, 0, new long[]{0});
        return sortAndTrim(items);
    }

    public static ArrayList<FileItem> scanPackages(Progress progress) {
        final List<FileItem> items = new ArrayList<>();
        walk(FileUtils.externalRoot(), new Visitor() {
            @Override
            public void onFile(File file) {
                if ("apk".equals(extension(file))) {
                    items.add(new FileItem(file, file.length(), false));
                }
            }
        }, null, progress, 0, new long[]{0});
        return sortAndTrim(items);
    }

    public static ArrayList<FileItem> scanMedia(Progress progress) {
        final List<FileItem> items = new ArrayList<>();
        walk(FileUtils.externalRoot(), new Visitor() {
            @Override
            public void onFile(File file) {
                String ext = extension(file);
                if (matches(ext, IMAGE_EXT) || matches(ext, VIDEO_EXT) || matches(ext, AUDIO_EXT)) {
                    items.add(new FileItem(file, file.length(), false));
                }
            }
        }, null, progress, 0, new long[]{0});
        return sortAndTrim(items);
    }

    public static ArrayList<FileItem> scanEmptyDirs(Progress progress) {
        final List<File> empty = new ArrayList<>();
        walk(FileUtils.externalRoot(), new Visitor() {
            @Override
            public void onFile(File file) {
            }
        }, empty, progress, 0, new long[]{0});
        ArrayList<FileItem> items = new ArrayList<>();
        for (int i = 0; i < empty.size() && i < MAX_RESULT; i++) {
            File dir = empty.get(i);
            items.add(new FileItem(dir, 0L, true));
        }
        return items;
    }

    public static ArrayList<FileItem> scanDuplicates(Progress progress) {
        final long minSize = 512L * 1024L;
        final Map<Long, List<File>> bySize = new HashMap<>();
        walk(FileUtils.externalRoot(), new Visitor() {
            @Override
            public void onFile(File file) {
                long size = file.length();
                if (size >= minSize) {
                    List<File> group = bySize.get(size);
                    if (group == null) {
                        group = new ArrayList<>();
                        bySize.put(size, group);
                    }
                    group.add(file);
                }
            }
        }, null, progress, 0, new long[]{0});

        if (progress != null) {
            progress.onStatus("正在比对内容…");
        }

        final Map<String, List<File>> byHash = new HashMap<>();
        for (Map.Entry<Long, List<File>> entry : bySize.entrySet()) {
            List<File> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            for (File file : group) {
                String hash = FileUtils.hashHead(file, 256 * 1024);
                if (hash.isEmpty()) {
                    continue;
                }
                String key = entry.getKey() + ":" + hash;
                List<File> same = byHash.get(key);
                if (same == null) {
                    same = new ArrayList<>();
                    byHash.put(key, same);
                }
                same.add(file);
            }
        }

        List<FileItem> items = new ArrayList<>();
        for (List<File> group : byHash.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                File file = group.get(i);
                FileItem item = new FileItem(file, file.length(), false,
                        i == 0 ? "保留原件" : "重复副本");
                item.selected = i > 0;
                items.add(item);
            }
        }
        return sortAndTrim(items);
    }
}
