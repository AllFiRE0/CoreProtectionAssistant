package ru.allfire.coreprotectionassistant;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import ru.allfire.coreprotectionassistant.commands.CommandManager;
import ru.allfire.coreprotectionassistant.config.ConfigManager;
import ru.allfire.coreprotectionassistant.config.Lang;
import ru.allfire.coreprotectionassistant.database.DatabaseManager;
import ru.allfire.coreprotectionassistant.hooks.CoreProtectHook;
import ru.allfire.coreprotectionassistant.hooks.PapiHook;
import ru.allfire.coreprotectionassistant.listeners.*;
import ru.allfire.coreprotectionassistant.managers.*;

import java.util.logging.Level;

public final class CoreProtectionAssistant extends JavaPlugin {
    
    private static CoreProtectionAssistant instance;
    
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private CoreProtectHook coreProtectHook;
    private PapiHook papiHook;
    
    private StaffManager staffManager;
    private WarnManager warnManager;
    private ReportManager reportManager;
    private ChatRuleManager chatRuleManager;
    private AbuseScoreManager abuseScoreManager;
    private ChatBotManager chatBotManager;
    
    private long loadTime;
    
    @Override
    public void onEnable() {
        instance = this;
        loadTime = System.currentTimeMillis();
        
        getLogger().info("§8[§cCoreProtectionAssistant§8] §7Starting...");
        
        if (!loadConfigurations()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        if (!initializeDatabase()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        initializeHooks();
        initializeManagers();
        registerListeners();
        registerCommands();
        registerPlaceholders();
        startScheduledTasks();
        
        new Metrics(this, 22800);
        
        loadTime = System.currentTimeMillis() - loadTime;
        getLogger().info("§8[§cCoreProtectionAssistant§8] §aEnabled in §f" + loadTime + "ms");
        getLogger().info("§8[§cCoreProtectionAssistant§8] §7Author: §fAllF1RE");
    }
    
    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (papiHook != null) {
            papiHook.unregister();
        }
        getLogger().info("§8[§cCoreProtectionAssistant§8] §cDisabled");
    }
    
    private boolean loadConfigurations() {
        try {
            configManager = new ConfigManager(this);
            configManager.loadAll();
            Lang.setLang(configManager.getLangConfig());
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configurations", e);
            return false;
        }
    }
    
    private boolean initializeDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            return databaseManager.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }
    
    private void initializeHooks() {
        coreProtectHook = new CoreProtectHook(this);
        coreProtectHook.init();
        papiHook = new PapiHook(this);
    }
    
    private void initializeManagers() {
        staffManager = new StaffManager(this);
        warnManager = new WarnManager(this);
        reportManager = new ReportManager(this);
        chatRuleManager = new ChatRuleManager(this);
        abuseScoreManager = new AbuseScoreManager(this);
        chatBotManager = new ChatBotManager(this);
        if (chatBotManager.isEnabled()) {
            getLogger().info("§8[§cCoreProtectionAssistant§8] §aChatBot enabled with §f" + 
                chatBotManager.getRulesCount() + " §arules");
        }
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GriefListener(this), this);
    }
    
    private void registerCommands() {
        CommandManager commandManager = new CommandManager(this);
        commandManager.registerAll();
    }
    
    private void registerPlaceholders() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papiHook.register();
        }
    }
    
    private void startScheduledTasks() {
        int interval = configManager.getMainConfig().getInt("warn_clear.check_interval_ticks", 12000);
        
        // Проверка условий автоснятия
        getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> warnManager.checkWarnClearConditions(),
            interval, interval
        );
        
        // Проверка просроченных варнов (каждые 60 секунд = 1200 тиков)
        getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> warnManager.checkExpiredWarnings(),
            1200L, 1200L
        );
        
        // Очистка старых жалоб
        getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> reportManager.cleanupOldReports(),
            20 * 60 * 30, 20 * 60 * 60 * 6
        );
    }
    
    public void reload() {
        configManager.reloadAll();
        Lang.setLang(configManager.getLangConfig());
        chatRuleManager.loadRules();
        reportManager.reload();
        if (chatBotManager != null) {
            chatBotManager.reload();
        }
        getLogger().info("§8[§cCoreProtectionAssistant§8] §aConfiguration reloaded");
    }
    
    public static CoreProtectionAssistant getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public CoreProtectHook getCoreProtectHook() { return coreProtectHook; }
    public StaffManager getStaffManager() { return staffManager; }
    public WarnManager getWarnManager() { return warnManager; }
    public ReportManager getReportManager() { return reportManager; }
    public ChatRuleManager getChatRuleManager() { return chatRuleManager; }
    public AbuseScoreManager getAbuseScoreManager() { return abuseScoreManager; }
    public ChatBotManager getChatBotManager() { return chatBotManager; }
    public long getLoadTime() { return loadTime; }
}
