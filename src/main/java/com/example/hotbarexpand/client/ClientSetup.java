package com.example.hotbarexpand.client;

import com.example.hotbarexpand.HotbarExpandMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = HotbarExpandMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    public static final String KEY_CATEGORY = "key.category.hotbarexpand";

    public static KeyMapping TOGGLE_EXPAND_KEY;
    public static KeyMapping HOTBAR_1_KEY;
    public static KeyMapping HOTBAR_2_KEY;
    public static KeyMapping HOTBAR_3_KEY;
    public static KeyMapping HOTBAR_4_KEY;
    public static KeyMapping HOTBAR_5_KEY;
    public static KeyMapping HOTBAR_6_KEY;
    public static KeyMapping HOTBAR_7_KEY;
    public static KeyMapping HOTBAR_8_KEY;
    public static KeyMapping HOTBAR_9_KEY;

    public static void setup() {
    }

    public static KeyMapping getHotbarKey(int index) {
        return switch (index) {
            case 0 -> HOTBAR_1_KEY;
            case 1 -> HOTBAR_2_KEY;
            case 2 -> HOTBAR_3_KEY;
            case 3 -> HOTBAR_4_KEY;
            case 4 -> HOTBAR_5_KEY;
            case 5 -> HOTBAR_6_KEY;
            case 6 -> HOTBAR_7_KEY;
            case 7 -> HOTBAR_8_KEY;
            case 8 -> HOTBAR_9_KEY;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        TOGGLE_EXPAND_KEY = new KeyMapping(
                "key.hotbarexpand.toggle_expand",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEY_CATEGORY
        );
        event.register(TOGGLE_EXPAND_KEY);

        HOTBAR_1_KEY = new KeyMapping("key.hotbarexpand.hotbar_1", GLFW.GLFW_KEY_1, KEY_CATEGORY);
        HOTBAR_2_KEY = new KeyMapping("key.hotbarexpand.hotbar_2", GLFW.GLFW_KEY_2, KEY_CATEGORY);
        HOTBAR_3_KEY = new KeyMapping("key.hotbarexpand.hotbar_3", GLFW.GLFW_KEY_3, KEY_CATEGORY);
        HOTBAR_4_KEY = new KeyMapping("key.hotbarexpand.hotbar_4", GLFW.GLFW_KEY_4, KEY_CATEGORY);
        HOTBAR_5_KEY = new KeyMapping("key.hotbarexpand.hotbar_5", GLFW.GLFW_KEY_5, KEY_CATEGORY);
        HOTBAR_6_KEY = new KeyMapping("key.hotbarexpand.hotbar_6", GLFW.GLFW_KEY_6, KEY_CATEGORY);
        HOTBAR_7_KEY = new KeyMapping("key.hotbarexpand.hotbar_7", GLFW.GLFW_KEY_7, KEY_CATEGORY);
        HOTBAR_8_KEY = new KeyMapping("key.hotbarexpand.hotbar_8", GLFW.GLFW_KEY_8, KEY_CATEGORY);
        HOTBAR_9_KEY = new KeyMapping("key.hotbarexpand.hotbar_9", GLFW.GLFW_KEY_9, KEY_CATEGORY);

        event.register(HOTBAR_1_KEY);
        event.register(HOTBAR_2_KEY);
        event.register(HOTBAR_3_KEY);
        event.register(HOTBAR_4_KEY);
        event.register(HOTBAR_5_KEY);
        event.register(HOTBAR_6_KEY);
        event.register(HOTBAR_7_KEY);
        event.register(HOTBAR_8_KEY);
        event.register(HOTBAR_9_KEY);
    }
}
