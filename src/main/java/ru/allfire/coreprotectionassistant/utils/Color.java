package ru.allfire.coreprotectionassistant.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Color {
    
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("&([A-Fa-f0-9]{6})");
    
    public static String colorize(String text) {
        if (text == null) return "";
        
        // Обработка &#RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);
        
        text = buffer.toString();
        
        // Обработка &RRGGBB
        matcher = AMPERSAND_HEX_PATTERN.matcher(text);
        buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);
        
        // Обработка стандартных &кодов
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
    
    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }
    
    public static String legacyToMiniMessage(String text) {
        // Конвертация &кодов в MiniMessage формат
        return colorize(text)
            .replace("§0", "<black>")
            .replace("§1", "<dark_blue>")
            .replace("§2", "<dark_green>")
            .replace("§3", "<dark_aqua>")
            .replace("§4", "<dark_red>")
            .replace("§5", "<dark_purple>")
            .replace("§6", "<gold>")
            .replace("§7", "<gray>")
            .replace("§8", "<dark_gray>")
            .replace("§9", "<blue>")
            .replace("§a", "<green>")
            .replace("§b", "<aqua>")
            .replace("§c", "<red>")
            .replace("§d", "<light_purple>")
            .replace("§e", "<yellow>")
            .replace("§f", "<white>")
            .replace("§k", "<obfuscated>")
            .replace("§l", "<bold>")
            .replace("§m", "<strikethrough>")
            .replace("§n", "<underline>")
            .replace("§o", "<italic>")
            .replace("§r", "<reset>");
    }
}
