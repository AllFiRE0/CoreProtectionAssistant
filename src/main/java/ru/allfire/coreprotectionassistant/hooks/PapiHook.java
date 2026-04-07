package ru.allfire.coreprotectionassistant.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.placeholder.CPAExpansion;

public class PapiHook {
    
    private final CoreProtectionAssistant plugin;
    private CPAExpansion expansion;
    
    public PapiHook(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    public void register() {
        if (expansion == null) {
            expansion = new CPAExpansion(plugin);
            expansion.register();
            plugin.getLogger().info("PlaceholderAPI expansion registered");
        }
    }
    
    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
    
    public String parse(Player player, String text) {
        if (expansion == null) return text;
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
