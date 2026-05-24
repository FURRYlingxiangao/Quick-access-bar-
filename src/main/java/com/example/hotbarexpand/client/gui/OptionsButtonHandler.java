package com.example.hotbarexpand.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class OptionsButtonHandler {
    
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen)) return;
        
        OptionsScreen screen = (OptionsScreen) event.getScreen();
        
        // 在选项界面添加"快捷栏设置"按钮
        // 找到合适的位置放置按钮
        int buttonWidth = 150;
        int buttonHeight = 20;
        int x = screen.width / 2 - buttonWidth / 2;
        int y = Math.min(screen.height - 28, screen.height / 6 + 168);
        
        Button hotbarSettingsButton = Button.builder(
            Component.literal("快捷栏设置..."),
            button -> {
                Minecraft.getInstance().setScreen(new HotbarSettingsScreen(screen));
            }
        ).bounds(x, y, buttonWidth, buttonHeight).build();
        
        event.addListener(hotbarSettingsButton);
    }
}
