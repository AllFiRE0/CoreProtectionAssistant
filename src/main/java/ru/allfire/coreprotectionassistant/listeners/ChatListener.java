package ru.allfire.coreprotectionassistant.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;
import ru.allfire.coreprotectionassistant.managers.ChatRuleManager.ChatRuleProcessResult;

public class ChatListener implements Listener {
    
    private final CoreProtectionAssistant plugin;
    
    public ChatListener(CoreProtectionAssistant plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(event.message());
        
        ChatRuleProcessResult result = plugin.getChatRuleManager().processChat(player, message);
        
        if (result.cancelled()) {
            event.setCancelled(true);
            player.sendMessage(plugin.getChatRuleManager().processChat(player, message)
                .processedMessage() != null ? 
                net.kyori.adventure.text.Component.text(result.processedMessage()) : 
                net.kyori.adventure.text.Component.empty());
        }
    }
}
