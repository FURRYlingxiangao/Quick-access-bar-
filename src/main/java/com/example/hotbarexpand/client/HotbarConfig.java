package com.example.hotbarexpand.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HotbarConfig {
    // 快捷栏布局模式
    public enum LayoutMode {
        ONE_X_ONE(1, 1, "1x1"),
        ONE_X_TWO(1, 2, "1x2"),
        TWO_X_ONE(2, 1, "2x1"),
        TWO_X_TWO(2, 2, "2x2");

        public final int columns;
        public final int rows;
        public final String displayName;

        LayoutMode(int columns, int rows, String displayName) {
            this.columns = columns;
            this.rows = rows;
            this.displayName = displayName;
        }

        public int getTotalHotbars() {
            return columns * rows;
        }

        public static LayoutMode byName(String name) {
            for (LayoutMode mode : values()) {
                if (mode.displayName.equals(name)) {
                    return mode;
                }
            }
            return ONE_X_ONE;
        }
    }

    // 显示位置
    public enum DisplayPosition {
        TOP_LEFT("左上"),
        TOP_CENTER("中上"),
        TOP_RIGHT("右上"),
        BOTTOM_RIGHT("右下"),
        BOTTOM_LEFT("左下"),
        ABOVE_HOTBAR("主快捷栏上部");

        public final String displayName;

        DisplayPosition(String displayName) {
            this.displayName = displayName;
        }

        public static DisplayPosition byName(String name) {
            for (DisplayPosition pos : values()) {
                if (pos.displayName.equals(name)) {
                    return pos;
                }
            }
            return ABOVE_HOTBAR;
        }
    }

    // 展开模式
    public enum ExpandMode {
        ALWAYS_SHOW("一直显示"),
        AUTO_HIDE("自动关闭"),
        SWITCH_EXPAND("切换时展开");

        public final String displayName;

        ExpandMode(String displayName) {
            this.displayName = displayName;
        }

        public static ExpandMode byName(String name) {
            for (ExpandMode mode : values()) {
                if (mode.displayName.equals(name)) {
                    return mode;
                }
            }
            return ALWAYS_SHOW;
        }
    }

    // 透明模式
    public enum TransparencyMode {
        ALWAYS_TRANSPARENT("一直半透明"),
        NORMAL_WHEN_SWITCH("切换时正常"),
        ALWAYS_OPAQUE("一直正常");

        public final String displayName;

        TransparencyMode(String displayName) {
            this.displayName = displayName;
        }

        public static TransparencyMode byName(String name) {
            for (TransparencyMode mode : values()) {
                if (mode.displayName.equals(name)) {
                    return mode;
                }
            }
            return ALWAYS_OPAQUE;
        }
    }

    private static LayoutMode currentLayout = LayoutMode.ONE_X_ONE;
    private static DisplayPosition displayPosition = DisplayPosition.ABOVE_HOTBAR;
    private static ExpandMode expandMode = ExpandMode.ALWAYS_SHOW;
    private static TransparencyMode transparencyMode = TransparencyMode.ALWAYS_OPAQUE;

    // 快捷栏列表最大显示数量（默认9，最小1，最大18）
    private static int maxVisibleHotbars = 9;
    private static final int MIN_VISIBLE_HOTBARS = 1;
    private static final int MAX_VISIBLE_HOTBARS = 18;

    // 展开动画开关（默认开启）
    private static boolean animationEnabled = true;

    // 切换音效开关（默认开启）
    private static boolean soundEnabled = true;

    // 位置指示器开关（默认开启）
    private static boolean indicatorEnabled = true;

    // 自动保存开关（默认开启）
    private static boolean autoSaveEnabled = true;

    // 滚轮循环开关（默认开启）
    private static boolean scrollLoopEnabled = true;

    // 动画速度（默认1.0，范围0.1-3.0）
    private static float animationSpeed = 1.0f;
    private static final float MIN_ANIMATION_SPEED = 0.1f;
    private static final float MAX_ANIMATION_SPEED = 3.0f;

    // 列表展开延迟（默认0，范围0-500毫秒）
    private static int expandDelay = 0;
    private static final int MIN_EXPAND_DELAY = 0;
    private static final int MAX_EXPAND_DELAY = 500;

    // 指示器透明度（默认100%，范围20%-100%）
    private static int indicatorOpacity = 100;
    private static final int MIN_INDICATOR_OPACITY = 20;
    private static final int MAX_INDICATOR_OPACITY = 100;

    // 自动关闭延迟（默认3000毫秒，范围1000-10000毫秒）
    private static int autoHideDelay = 3000;
    private static final int MIN_AUTO_HIDE_DELAY = 1000;
    private static final int MAX_AUTO_HIDE_DELAY = 10000;

    // 透明度（默认50%，范围10%-90%）
    private static int transparency = 50;
    private static final int MIN_TRANSPARENCY = 10;
    private static final int MAX_TRANSPARENCY = 90;

    // 透明度过渡速度（默认1.0，范围0.1-5.0）
    private static float alphaTransitionSpeed = 1.0f;
    private static final float MIN_ALPHA_TRANSITION_SPEED = 0.1f;
    private static final float MAX_ALPHA_TRANSITION_SPEED = 5.0f;

    // 设置界面快捷键（默认Ctrl+N）
    private static KeyMapping settingsKey = new KeyMapping(
        "key.hotbarexpand.settings",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_N,
        "key.categories.hotbarexpand"
    );

    public static LayoutMode getLayout() {
        return currentLayout;
    }

    public static void setLayout(LayoutMode layout) {
        currentLayout = layout;
        saveConfig();
    }

    // ===== 显示位置设置 =====
    public static DisplayPosition getDisplayPosition() {
        return displayPosition;
    }

    public static void setDisplayPosition(DisplayPosition position) {
        displayPosition = position;
        saveConfig();
    }

    // ===== 展开模式设置 =====
    public static ExpandMode getExpandMode() {
        return expandMode;
    }

    public static void setExpandMode(ExpandMode mode) {
        expandMode = mode;
        saveConfig();
    }

    // ===== 透明模式设置 =====
    public static TransparencyMode getTransparencyMode() {
        return transparencyMode;
    }

    public static void setTransparencyMode(TransparencyMode mode) {
        transparencyMode = mode;
        saveConfig();
    }

    public static KeyMapping getSettingsKey() {
        return settingsKey;
    }

    /**
     * 获取快捷栏列表最大显示数量
     */
    public static int getMaxVisibleHotbars() {
        return maxVisibleHotbars;
    }

    /**
     * 设置快捷栏列表最大显示数量
     * @param count 数量（1-18）
     */
    public static void setMaxVisibleHotbars(int count) {
        maxVisibleHotbars = Math.max(MIN_VISIBLE_HOTBARS, Math.min(MAX_VISIBLE_HOTBARS, count));
        saveConfig();
    }

    /**
     * 获取最小显示数量
     */
    public static int getMinVisibleHotbars() {
        return MIN_VISIBLE_HOTBARS;
    }

    /**
     * 获取最大显示数量
     */
    public static int getMaxVisibleHotbarsLimit() {
        return MAX_VISIBLE_HOTBARS;
    }

    /**
     * 检查当前设置是否超过9个（影响Alt+滚轮功能）
     */
    public static boolean isExceedingAltLimit() {
        return maxVisibleHotbars > 9;
    }

    // ===== 动画设置 =====
    public static boolean isAnimationEnabled() {
        return animationEnabled;
    }

    public static void setAnimationEnabled(boolean enabled) {
        animationEnabled = enabled;
        saveConfig();
    }

    // ===== 音效设置 =====
    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
        saveConfig();
    }

    // ===== 位置指示器设置 =====
    public static boolean isIndicatorEnabled() {
        return indicatorEnabled;
    }

    public static void setIndicatorEnabled(boolean enabled) {
        indicatorEnabled = enabled;
        saveConfig();
    }

    // ===== 自动保存设置 =====
    public static boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }

    public static void setAutoSaveEnabled(boolean enabled) {
        autoSaveEnabled = enabled;
        saveConfig();
    }

    // ===== 滚轮循环设置 =====
    public static boolean isScrollLoopEnabled() {
        return scrollLoopEnabled;
    }

    public static void setScrollLoopEnabled(boolean enabled) {
        scrollLoopEnabled = enabled;
        saveConfig();
    }

    // ===== 动画速度设置 =====
    public static float getAnimationSpeed() {
        return animationSpeed;
    }

    public static void setAnimationSpeed(float speed) {
        animationSpeed = Math.max(MIN_ANIMATION_SPEED, Math.min(MAX_ANIMATION_SPEED, speed));
        saveConfig();
    }

    public static float getMinAnimationSpeed() {
        return MIN_ANIMATION_SPEED;
    }

    public static float getMaxAnimationSpeed() {
        return MAX_ANIMATION_SPEED;
    }

    // ===== 展开延迟设置 =====
    public static int getExpandDelay() {
        return expandDelay;
    }

    public static void setExpandDelay(int delay) {
        expandDelay = Math.max(MIN_EXPAND_DELAY, Math.min(MAX_EXPAND_DELAY, delay));
        saveConfig();
    }

    public static int getMinExpandDelay() {
        return MIN_EXPAND_DELAY;
    }

    public static int getMaxExpandDelay() {
        return MAX_EXPAND_DELAY;
    }

    // ===== 指示器透明度设置 =====
    public static int getIndicatorOpacity() {
        return indicatorOpacity;
    }

    public static void setIndicatorOpacity(int opacity) {
        indicatorOpacity = Math.max(MIN_INDICATOR_OPACITY, Math.min(MAX_INDICATOR_OPACITY, opacity));
        saveConfig();
    }

    public static int getMinIndicatorOpacity() {
        return MIN_INDICATOR_OPACITY;
    }

    public static int getMaxIndicatorOpacity() {
        return MAX_INDICATOR_OPACITY;
    }

    // ===== 自动关闭延迟设置 =====
    public static int getAutoHideDelay() {
        return autoHideDelay;
    }

    public static void setAutoHideDelay(int delay) {
        autoHideDelay = Math.max(MIN_AUTO_HIDE_DELAY, Math.min(MAX_AUTO_HIDE_DELAY, delay));
        saveConfig();
    }

    public static int getMinAutoHideDelay() {
        return MIN_AUTO_HIDE_DELAY;
    }

    public static int getMaxAutoHideDelay() {
        return MAX_AUTO_HIDE_DELAY;
    }

    // ===== 透明度设置 =====
    public static int getTransparency() {
        return transparency;
    }

    public static void setTransparency(int value) {
        transparency = Math.max(MIN_TRANSPARENCY, Math.min(MAX_TRANSPARENCY, value));
        saveConfig();
    }

    public static int getMinTransparency() {
        return MIN_TRANSPARENCY;
    }

    public static int getMaxTransparency() {
        return MAX_TRANSPARENCY;
    }

    // ===== 透明度过渡速度设置 =====
    public static float getAlphaTransitionSpeed() {
        return alphaTransitionSpeed;
    }

    public static void setAlphaTransitionSpeed(float speed) {
        alphaTransitionSpeed = Math.max(MIN_ALPHA_TRANSITION_SPEED, Math.min(MAX_ALPHA_TRANSITION_SPEED, speed));
        saveConfig();
    }

    public static float getMinAlphaTransitionSpeed() {
        return MIN_ALPHA_TRANSITION_SPEED;
    }

    public static float getMaxAlphaTransitionSpeed() {
        return MAX_ALPHA_TRANSITION_SPEED;
    }

    public static void loadConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        Path configDir = Paths.get(Minecraft.getInstance().gameDirectory.toString(), "config", "hotbarexpand");
        try {
            if (!configDir.toFile().exists()) {
                configDir.toFile().mkdirs();
            }

            Path configFile = configDir.resolve("layout_config.dat");
            if (configFile.toFile().exists()) {
                CompoundTag tag = NbtIo.read(configFile);
                if (tag != null) {
                    String layoutName = tag.getString("layout");
                    currentLayout = LayoutMode.byName(layoutName);
                    // 加载最大显示数量（如果不存在则使用默认值9）
                    if (tag.contains("maxVisibleHotbars")) {
                        maxVisibleHotbars = tag.getInt("maxVisibleHotbars");
                        // 确保在有效范围内
                        maxVisibleHotbars = Math.max(MIN_VISIBLE_HOTBARS, Math.min(MAX_VISIBLE_HOTBARS, maxVisibleHotbars));
                    }
                    // 加载功能开关
                    if (tag.contains("animationEnabled")) {
                        animationEnabled = tag.getBoolean("animationEnabled");
                    }
                    if (tag.contains("soundEnabled")) {
                        soundEnabled = tag.getBoolean("soundEnabled");
                    }
                    if (tag.contains("indicatorEnabled")) {
                        indicatorEnabled = tag.getBoolean("indicatorEnabled");
                    }
                    if (tag.contains("autoSaveEnabled")) {
                        autoSaveEnabled = tag.getBoolean("autoSaveEnabled");
                    }
                    if (tag.contains("scrollLoopEnabled")) {
                        scrollLoopEnabled = tag.getBoolean("scrollLoopEnabled");
                    }
                    // 加载新增设置
                    if (tag.contains("animationSpeed")) {
                        animationSpeed = tag.getFloat("animationSpeed");
                    }
                    if (tag.contains("expandDelay")) {
                        expandDelay = tag.getInt("expandDelay");
                    }
                    if (tag.contains("indicatorOpacity")) {
                        indicatorOpacity = tag.getInt("indicatorOpacity");
                    }
                    // 加载显示位置
                    if (tag.contains("displayPosition")) {
                        displayPosition = DisplayPosition.byName(tag.getString("displayPosition"));
                    }
                    // 加载展开模式
                    if (tag.contains("expandMode")) {
                        expandMode = ExpandMode.byName(tag.getString("expandMode"));
                    }
                    // 加载透明模式
                    if (tag.contains("transparencyMode")) {
                        transparencyMode = TransparencyMode.byName(tag.getString("transparencyMode"));
                    }
                    // 加载自动关闭延迟
                    if (tag.contains("autoHideDelay")) {
                        autoHideDelay = tag.getInt("autoHideDelay");
                    }
                    // 加载透明度
                    if (tag.contains("transparency")) {
                        transparency = tag.getInt("transparency");
                    }
                    // 加载透明度过渡速度
                    if (tag.contains("alphaTransitionSpeed")) {
                        alphaTransitionSpeed = tag.getFloat("alphaTransitionSpeed");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[HotbarExpand] Failed to load config: " + e.getMessage());
        }
    }

    public static void saveConfig() {
        Path configDir = Paths.get(Minecraft.getInstance().gameDirectory.toString(), "config", "hotbarexpand");
        try {
            if (!configDir.toFile().exists()) {
                configDir.toFile().mkdirs();
            }

            Path configFile = configDir.resolve("layout_config.dat");
            CompoundTag tag = new CompoundTag();
            tag.putString("layout", currentLayout.displayName);
            tag.putInt("maxVisibleHotbars", maxVisibleHotbars);
            tag.putBoolean("animationEnabled", animationEnabled);
            tag.putBoolean("soundEnabled", soundEnabled);
            tag.putBoolean("indicatorEnabled", indicatorEnabled);
            tag.putBoolean("autoSaveEnabled", autoSaveEnabled);
            tag.putBoolean("scrollLoopEnabled", scrollLoopEnabled);
            tag.putFloat("animationSpeed", animationSpeed);
            tag.putInt("expandDelay", expandDelay);
            tag.putInt("indicatorOpacity", indicatorOpacity);
            // 保存新增配置
            tag.putString("displayPosition", displayPosition.displayName);
            tag.putString("expandMode", expandMode.displayName);
            tag.putString("transparencyMode", transparencyMode.displayName);
            tag.putInt("autoHideDelay", autoHideDelay);
            tag.putInt("transparency", transparency);
            tag.putFloat("alphaTransitionSpeed", alphaTransitionSpeed);
            NbtIo.write(tag, configFile);
        } catch (IOException e) {
            System.err.println("[HotbarExpand] Failed to save config: " + e.getMessage());
        }
    }
}
