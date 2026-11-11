package com.cleanbox.app.util;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public final class StorageUtils {

    private StorageUtils() {
    }

    /** @return [总容量, 可用容量]，单位字节。 */
    public static long[] usage() {
        try {
            File path = FileUtils.externalRoot();
            StatFs stat = new StatFs(path.getAbsolutePath());
            return new long[]{stat.getTotalBytes(), stat.getAvailableBytes()};
        } catch (Exception e) {
            return new long[]{0L, 0L};
        }
    }

    public static String formatPercent(long used, long total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(used * 100d / total) + "%";
    }

    public static boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }
}
