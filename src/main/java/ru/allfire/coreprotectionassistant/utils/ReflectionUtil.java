package ru.allfire.coreprotectionassistant.utils;

import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtil {
    
    private static final String VERSION = Bukkit.getServer().getClass().getPackage().getName()
        .replace(".", ",").split(",")[3];
    
    public static Class<?> getNMSClass(String name) {
        try {
            return Class.forName("net.minecraft.server." + VERSION + "." + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    public static Class<?> getCraftBukkitClass(String name) {
        try {
            return Class.forName("org.bukkit.craftbukkit." + VERSION + "." + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    public static Object getField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception ignored) {}
    }
    
    public static Object invokeMethod(Object obj, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            Method method = obj.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static Object invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = obj.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }
}
