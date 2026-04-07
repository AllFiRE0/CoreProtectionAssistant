package ru.allfire.coreprotectionassistant.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Lang {
    
    private static FileConfiguration langConfig;
    private static String cachedPrefix;
    private static boolean prefixEnabled;
    
    public static void setLang(FileConfiguration config) {
        langConfig = config;
        
        // Загружаем префикс
        String rawPrefix = config.getString("prefix", "&8[&c&lCPA&8] &7»");
        
        // Если префикс пустой или содержит только "none"/"false" - отключаем его
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
     * @param path путь в lang.yml (например, "reload_success")
     * @return сообщение с префиксом (если включен) и цветами
     */
    public static String get(String path) {
        if (langConfig == null) {
            return "§cLang not loaded: " + path;
        }
        String message = langConfig.getString("messages." + path);
        if (message == null) {
            return "§cMissing lang: " + path;
        }
        
        // Заменяем %prefix% на префикс (или пустоту, если префикс отключен)
        String result = message.replace("%prefix%", cachedPrefix);
        
        // Убираем лишние пробелы в начале, если префикс пустой
        if (!prefixEnabled && result.startsWith(" ")) {
            result = result.substring(1);
        }
        
        return result;
    }
    
    /**
     * Получить сообщение с подстановкой переменных
     * @param path путь в lang.yml
     * @param replacements пары ключ-значение для замены (%key% -> value)
     * @return отформатированное сообщение
     */
    public static String get(String path, String... replacements) {
        String message = get(path);
        
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        
        return message;
    }
    
    /**
     * Получить "сырой" префикс (без цветов)
     */
    public static String getRawPrefix() {
        return langConfig.getString("prefix", "&8[&c&lCPA&8] &7»");
    }
    
    /**
     * Получить префикс с цветами
     */
    public static String getPrefix() {
        return cachedPrefix;
    }
    
    /**
     * Проверить, включен ли префикс
     */
    public static boolean isPrefixEnabled() {
        return prefixEnabled;
    }
    
    /**
     * Получить сообщение БЕЗ автоматической подстановки префикса
     * (для случаев, когда нужно вручную управлять префиксом)
     */
    public static String getRaw(String path) {
        if (langConfig == null) {
            return "§cLang not loaded: " + path;
        }
        return langConfig.getString("messages." + path, "§cMissing lang: " + path);
    }
    
    /**
     * Применить цветовые коды к строке
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
