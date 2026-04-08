package ru.allfire.coreprotectionassistant.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.io.IOException;
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
    
    public ConfigManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void loadAll() {
        loadMainConfig();
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
        langConfigFile = new File(plugin.getDataFolder(), "lang.yml");
        if (!langConfigFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langConfigFile);
    }
    
    private void loadChatRulesConfig() {
        chatRulesFile = new File(plugin.getDataFolder(), "chattrules.yml");
        if (!chatRulesFile.exists()) {
            plugin.saveResource("chattrules.yml", false);
        }
        chatRulesConfig = YamlConfiguration.loadConfiguration(chatRulesFile);
    }
    
    private void loadReportsConfig() {
        reportsFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportsFile.exists()) {
            plugin.saveResource("reports.yml", false);
        }
        reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
    }
    
    private void loadChatBotConfig() {
        chatBotFile = new File(plugin.getDataFolder(), "chatbot.yml");
        if (!chatBotFile.exists()) {
            plugin.saveResource("chatbot.yml", false);
        }
        chatBotConfig = YamlConfiguration.loadConfiguration(chatBotFile);
    }
    
    public void reloadAll() {
        loadMainConfig();
        loadLangConfig();
        loadChatRulesConfig();
        loadReportsConfig();
        loadChatBotConfig();
    }
    
    // ========== ГЕТТЕРЫ (добавь эти методы) ==========
    
    public FileConfiguration getMainConfig() {
        return mainConfig;
    }
    
    public FileConfiguration getLangConfig() {
        return langConfig;
    }
    
    public FileConfiguration getChatRulesConfig() {
        return chatRulesConfig;
    }
    
    public FileConfiguration getReportsConfig() {
        return reportsConfig;
    }
    
    public FileConfiguration getChatBotConfig() {
        return chatBotConfig;
    }
}
