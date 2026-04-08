package ru.allfire.coreprotectionassistant.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Getter
public class ConfigManager {
    
    private final CoreProtectionAssistant plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration langConfig;
    private FileConfiguration chatRulesConfig;
    private FileConfiguration reportsConfig;
    private FileConfiguration chatBotConfig;
    
    private File mainConfigFile;
    private File langConfigFile;
    private File chatRulesFile;
    private File reportsFile;
    private File chatBotFile;
    
    private String language;
    
    public ConfigManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void loadAll() {
        loadMainConfig();
        
        // Определяем язык из конфига
        language = mainConfig.getString("language", "ru");
        
        loadLangConfig();
        loadChatRulesConfig();
        loadReportsConfig();
        loadChatBotConfig();
    }
    
    private void loadMainConfig() {
        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
    }
    
    private void loadLangConfig() {
        // Пробуем загрузить из папки языка
        File langFolder = new File(plugin.getDataFolder(), language);
        langConfigFile = new File(langFolder, "lang.yml");
        
        if (!langConfigFile.exists()) {
            // Если нет в папке языка, пробуем из корня
            langConfigFile = new File(plugin.getDataFolder(), "lang.yml");
            if (!langConfigFile.exists()) {
                plugin.saveResource("lang.yml", false);
            }
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langConfigFile);
        plugin.getLogger().info("Loaded language: " + language + " from " + langConfigFile.getPath());
    }
    
    private void loadChatRulesConfig() {
        File langFolder = new File(plugin.getDataFolder(), language);
        chatRulesFile = new File(langFolder, "chattrules.yml");
        
        if (!chatRulesFile.exists()) {
            chatRulesFile = new File(plugin.getDataFolder(), "chattrules.yml");
            if (!chatRulesFile.exists()) {
                plugin.saveResource("chattrules.yml", false);
            }
        }
        
        chatRulesConfig = YamlConfiguration.loadConfiguration(chatRulesFile);
    }
    
    private void loadReportsConfig() {
        File langFolder = new File(plugin.getDataFolder(), language);
        reportsFile = new File(langFolder, "reports.yml");
        
        if (!reportsFile.exists()) {
            reportsFile = new File(plugin.getDataFolder(), "reports.yml");
            if (!reportsFile.exists()) {
                plugin.saveResource("reports.yml", false);
            }
        }
        
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
    }
    
    private void loadChatBotConfig() {
        File langFolder = new File(plugin.getDataFolder(), language);
        chatBotFile = new File(langFolder, "chatbot.yml");
        
        if (!chatBotFile.exists()) {
            chatBotFile = new File(plugin.getDataFolder(), "chatbot.yml");
            if (!chatBotFile.exists()) {
                plugin.saveResource("chatbot.yml", false);
            }
        }
        
        chatBotConfig = YamlConfiguration.loadConfiguration(chatBotFile);
    }
    
    public void reloadAll() {
        loadMainConfig();
        language = mainConfig.getString("language", "ru");
        loadLangConfig();
        loadChatRulesConfig();
        loadReportsConfig();
        loadChatBotConfig();
    }
    
    public FileConfiguration getMainConfig() { return mainConfig; }
    public FileConfiguration getLangConfig() { return langConfig; }
    public FileConfiguration getChatRulesConfig() { return chatRulesConfig; }
    public FileConfiguration getReportsConfig() { return reportsConfig; }
    public FileConfiguration getChatBotConfig() { return chatBotConfig; }
    public String getLanguage() { return language; }
}
