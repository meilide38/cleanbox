package com.cleanbox.app.util;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

public final class FileUtils {

    private FileUtils() {
    }

    public static File externalRoot() {
        return Environment.getExternalStorageDirectory();
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int index = -1;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024d;
            index++;
        }
        if (value >= 100) {
            return String.format(Locale.US, "%.0f %s", value, units[index]);
        }
        return String.format(Locale.US, "%.1f %s", value, units[index]);
    }

    /** 把绝对路径压缩成相对外部存储根目录的短路径。 */
    public static String briefPath(File file) {
        String root = externalRoot().getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(root)) {
            path = path.substring(root.length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public static long directorySize(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0L;
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            total += child.isDirectory() ? directorySize(child) : child.length();
        }
        return total;
    }

    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        return file.delete();
    }

    /** 只读取文件头部若干字节做哈希，用于快速判断重复文件。 */
    public static String hashHead(File file, int maxBytes) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[16 * 1024];
            int read = 0;
            while (read < maxBytes) {
                int len = Math.min(buffer.length, maxBytes - read);
                int n = in.read(buffer, 0, len);
                if (n <= 0) {
                    break;
                }
                digest.update(buffer, 0, n);
                read += n;
            }
            byte[] result = digest.digest();
            StringBuilder sb = new StringBuilder(result.length * 2);
            for (byte b : result) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
