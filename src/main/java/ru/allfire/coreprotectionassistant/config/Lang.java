package ru.allfire.coreprotectionassistant.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Lang {
    
    private static FileConfiguration langConfig;
    private static String cachedPrefix;
    private static boolean prefixEnabled;
    
    public static void setLang(FileConfiguration config) {
        langConfig = config;
        
        String rawPrefix = config.getString("prefix", "&8[&c&lCPA&8] &7»");
        
        if (rawPrefix == null || rawPrefix.isEmpty() || 
            rawPrefix.equalsIgnoreCase("none") || 
            rawPrefix.equalsIgnoreCase("false")) {
            prefixEnabled = false;
            cachedPrefix = "";
        } else {
            prefixEnabled = true;
            cachedPrefix = ChatColor.translateAlternateColorCodes('&', rawPrefix);
        }
    }
    
    /**
     * Получить сообщение с автоматической подстановкой префикса
     */
    public static String get(String path) {
        if (langConfig == null) {
            return "";
        }
        
        String message = langConfig.getString("messages." + path);
        
        // Если сообщение пустое или "none" - возвращаем пустую строку (сообщение отключено)
        if (message == null || message.isEmpty() || message.equalsIgnoreCase("none")) {
            return "";
        }
        
        // Заменяем %prefix%
        String result = message.replace("%prefix%", cachedPrefix);
        
        // Убираем лишний пробел в начале, если префикс отключен
        if (!prefixEnabled && result.startsWith(" ")) {
            result = result.substring(1);
        }
        
        return result;
    }
    
    /**
     * Получить сообщение с подстановкой переменных
     */
    public static String get(String path, String... replacements) {
        String message = get(path);
        
        // Если сообщение отключено - возвращаем пустую строку
        if (message.isEmpty()) {
            return "";
        }
        
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        
        return message;
    }
    
    /**
     * Отправить сообщение игроку (если оно не отключено)
     */
    public static void send(org.bukkit.command.CommandSender sender, String path) {
        String message = get(path);
        if (!message.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    /**
     * Отправить сообщение игроку с подстановкой переменных
     */
    public static void send(org.bukkit.command.CommandSender sender, String path, String... replacements) {
        String message = get(path, replacements);
        if (!message.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    public static String getPrefix() {
        return cachedPrefix;
    }
    
    public static boolean isPrefixEnabled() {
        return prefixEnabled;
    }
    
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
