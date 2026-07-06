package online.thundercloud.pay.util;

import android.text.TextUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MonitorLogUtils {

    private static final Pattern ENTRY_SPLIT_PATTERN =
            Pattern.compile("\n(?=\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\r\\r\\r\\r)");
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private MonitorLogUtils() {
    }

    public static String keepRecentLogs(String logsStr, long maxAgeMs) {
        if (TextUtils.isEmpty(logsStr)) return "";

        long now = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.getDefault());
        String[] entries = ENTRY_SPLIT_PATTERN.split(logsStr.trim());
        StringBuilder builder = new StringBuilder();

        for (String entry : entries) {
            if (TextUtils.isEmpty(entry)) continue;
            long time = parseEntryTime(entry, format);
            if (time <= 0 || now - time > maxAgeMs) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(entry.trim());
        }
        return builder.toString();
    }

    public static String appendRecentLog(String existingLogs, String newEntry, long maxAgeMs) {
        String merged = TextUtils.isEmpty(existingLogs) ? newEntry : newEntry + "\n" + existingLogs;
        return keepRecentLogs(merged, maxAgeMs);
    }

    private static long parseEntryTime(String entry, SimpleDateFormat format) {
        if (entry.length() < TIMESTAMP_PATTERN.length()) return -1;
        String raw = entry.substring(0, TIMESTAMP_PATTERN.length());
        try {
            Date date = format.parse(raw);
            return date != null ? date.getTime() : -1;
        } catch (ParseException e) {
            return -1;
        }
    }
}
