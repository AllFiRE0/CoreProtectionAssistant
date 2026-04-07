package ru.allfire.coreprotectionassistant.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    
    public static String formatDate(long timestamp) {
        if (timestamp <= 0) return "Never";
        return DATE_FORMAT.format(new Date(timestamp));
    }
    
    public static String formatDateTime(long timestamp) {
        if (timestamp <= 0) return "Never";
        return DATE_TIME_FORMAT.format(new Date(timestamp));
    }
    
    public static String formatTime(long timestamp) {
        if (timestamp <= 0) return "Never";
        return TIME_FORMAT.format(new Date(timestamp));
    }
    
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        
        StringBuilder sb = new StringBuilder();
        
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    public static String formatDurationShort(long millis) {
        if (millis <= 0) return "0s";
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        
        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return seconds + "s";
    }
    
    public static String formatTicks(long ticks) {
        return formatDuration(ticks * 50);
    }
    
    public static long parseTimeToMillis(String time) {
        try {
            time = time.toLowerCase();
            
            if (time.endsWith("d")) {
                return Long.parseLong(time.replace("d", "")) * 24 * 60 * 60 * 1000;
            }
            if (time.endsWith("h")) {
                return Long.parseLong(time.replace("h", "")) * 60 * 60 * 1000;
            }
            if (time.endsWith("m")) {
                return Long.parseLong(time.replace("m", "")) * 60 * 1000;
            }
            if (time.endsWith("s")) {
                return Long.parseLong(time.replace("s", "")) * 1000;
            }
            
            return Long.parseLong(time) * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public static long parseTimeToTicks(String time) {
        return parseTimeToMillis(time) / 50;
    }
}
