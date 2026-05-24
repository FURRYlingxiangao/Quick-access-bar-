package com.example.hotbarexpand.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class OptionsButtonHandler {
    private static final Component HOTBAR_SETTINGS_TEXT = Component.literal("快捷栏设置...");
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 28;
    
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen)) return;
        
        OptionsScreen screen = (OptionsScreen) event.getScreen();

        Button hotbarSettingsButton = Button.builder(
            HOTBAR_SETTINGS_TEXT,
            button -> {
                Minecraft.getInstance().setScreen(new HotbarSettingsScreen(screen));
            }
        ).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        updateButtonPosition(screen, hotbarSettingsButton);
        
        event.addListener(hotbarSettingsButton);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen screen)) return;

        for (var child : screen.children()) {
            if (child instanceof Button button && HOTBAR_SETTINGS_TEXT.equals(button.getMessage())) {
                updateButtonPosition(screen, button);
                break;
            }
        }
    }

    private static void updateButtonPosition(OptionsScreen screen, AbstractWidget button) {
        int x = screen.width / 2 - BUTTON_WIDTH / 2;
        int y = Math.min(screen.height - BOTTOM_MARGIN, screen.height / 6 + 168);
        button.setX(x);
        button.setY(y);
        button.setWidth(BUTTON_WIDTH);
        button.setHeight(BUTTON_HEIGHT);
    }
}
