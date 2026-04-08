package ru.allfire.coreprotectionassistant.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Getter
public class ConfigManager {
    
    private final CoreProtectionAssistant plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration langConfig;
    private FileConfiguration chatRulesConfig;
    private FileConfiguration chatBotConfig;
    private FileConfiguration reportsConfig;
    
    private File mainConfigFile;
    private File langConfigFile;
    private File chatRulesFile;
    private File chatBotFile;
    private File reportsFile;
    
    public ConfigManager(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void loadAll() {
        loadMainConfig();
        loadLangConfig();
        loadChatRulesConfig();
        loadReportsConfig();
    }
    
    private void loadMainConfig() {
        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
        
        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8)
                );
                mainConfig.setDefaults(defConfig);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load default config.yml");
        }
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
    
    public void reloadAll() {
        loadAll();
    }
    
    public void saveMainConfig() {
        try {
            mainConfig.save(mainConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getChatBotConfig() {
    if (chatBotConfig == null) {
        chatBotFile = new File(plugin.getDataFolder(), "chatbot.yml");
        if (!chatBotFile.exists()) {
            plugin.saveResource("chatbot.yml", false);
        }
        chatBotConfig = YamlConfiguration.loadConfiguration(chatBotFile);
    }
    return chatBotConfig;
    }

    public void reloadChatBotConfig() {
    if (chatBotFile == null) {
        chatBotFile = new File(plugin.getDataFolder(), "chatbot.yml");
    }
    chatBotConfig = YamlConfiguration.loadConfiguration(chatBotFile);
    }
}
