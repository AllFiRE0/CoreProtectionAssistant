package ru.allfire.coreprotectionassistant.utils;

import org.bukkit.entity.Player;
import ru.allfire.coreprotectionassistant.CoreProtectionAssistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionParser {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    
    public static boolean evaluate(CoreProtectionAssistant plugin, Player player, String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        
        try {
            // Заменяем плейсхолдеры
            String processed = replacePlaceholders(plugin, player, condition);
            
            // Обрабатываем логические операторы
            processed = processLogicalOperators(processed);
            
            // Вычисляем результат
            return evaluateExpression(processed);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to evaluate condition: " + condition + " - " + e.getMessage());
            return false;
        }
    }
    
    private static String replacePlaceholders(CoreProtectionAssistant plugin, Player player, String text) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String value = resolvePlaceholder(plugin, player, placeholder);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    private static String resolvePlaceholder(CoreProtectionAssistant plugin, Player player, String placeholder) {
        // Заменяем {player} на имя игрока
        placeholder = placeholder.replace("{player}", player.getName());
        placeholder = placeholder.replace("{player_uuid}", player.getUniqueId().toString());
        
        // Если есть PAPI, используем его
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%" + placeholder + "%");
        }
        
        // Иначе обрабатываем свои плейсхолдеры
        return switch (placeholder) {
            case "player_name" -> player.getName();
            default -> "0";
        };
    }
    
    private static String processLogicalOperators(String expression) {
        return expression
            .replace(" AND ", " && ")
            .replace(" and ", " && ")
            .replace(" OR ", " || ")
            .replace(" or ", " || ")
            .replace("==", "==")
            .replace("!=", "!=")
            .replace(">=", ">=")
            .replace("<=", "<=")
            .replace(">", ">")
            .replace("<", "<");
    }
    
    private static boolean evaluateExpression(String expression) {
        // Простое сравнение чисел
        if (expression.contains(">")) {
            String[] parts = expression.split(">");
            if (parts.length == 2) {
                double left = parseDouble(parts[0].trim());
                double right = parseDouble(parts[1].trim());
                return left > right;
            }
        }
        
        if (expression.contains("<")) {
            String[] parts = expression.split("<");
            if (parts.length == 2) {
                double left = parseDouble(parts[0].trim());
                double right = parseDouble(parts[1].trim());
                return left < right;
            }
        }
        
        if (expression.contains(">=")) {
            String[] parts = expression.split(">=");
            if (parts.length == 2) {
                double left = parseDouble(parts[0].trim());
                double right = parseDouble(parts[1].trim());
                return left >= right;
            }
        }
        
        if (expression.contains("<=")) {
            String[] parts = expression.split("<=");
            if (parts.length == 2) {
                double left = parseDouble(parts[0].trim());
                double right = parseDouble(parts[1].trim());
                return left <= right;
            }
        }
        
        if (expression.contains("==")) {
            String[] parts = expression.split("==");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                return left.equals(right) || parseDouble(left) == parseDouble(right);
            }
        }
        
        if (expression.contains("!=")) {
            String[] parts = expression.split("!=");
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                return !left.equals(right) && parseDouble(left) != parseDouble(right);
            }
        }
        
        // Прямое булево значение
        return Boolean.parseBoolean(expression.trim());
    }
    
    private static double parseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
