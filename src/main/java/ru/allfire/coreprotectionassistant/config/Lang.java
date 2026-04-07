package ru.allfire.coreprotectionassistant.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Lang {
    
    private static FileConfiguration langConfig;
    
    public static void setLang(FileConfiguration config) {
        langConfig = config;
    }
    
    public static String get(String path) {
        if (langConfig == null) {
            return "§cLang not loaded: " + path;
        }
        String message = langConfig.getString("messages." + path);
        if (message == null) {
            return "§cMissing lang: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public static String getPrefix() {
        return langConfig.getString("prefix", "&8[&cCPA&8] &7»");
    }
    
    public static String getRaw(String path) {
        return langConfig.getString(path, "");
    }
}
