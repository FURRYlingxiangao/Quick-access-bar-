package com.example.hotbarexpand.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

public class ExpandedHotbarOverlay {
    private static final ResourceLocation HOTBAR_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/hotbar.png");
    private static final ResourceLocation HOTBAR_SELECTION_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/hotbar_selection.png");
    private static final ResourceLocation HOTBAR_OFFHAND_LEFT_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/hotbar_offhand_left.png");
    private static final ResourceLocation HOTBAR_OFFHAND_RIGHT_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/hotbar_offhand_right.png");
    private static final ResourceLocation HOTBAR_LAYER = ResourceLocation.withDefaultNamespace("hotbar");

    // 展开动画相关
    private static float expandProgress = 0.0f;
    private static final float EXPAND_SPEED = 0.12f;
    private static boolean isExpanding = false;
    private static boolean targetExpanded = false;
    
    // 列表中选中的格子索引（0-8）
    private static int selectedSlotInList = 0;

    public static boolean isExpanded() {
        return HotbarManager.isExpanded();
    }

    public static void setExpanded(boolean expanded) {
        if (expanded != targetExpanded) {
            if (!expanded) {
                HotbarManager.savePlayerInventoryToCurrentHotbar();
            }
            targetExpanded = expanded;
            isExpanding = true;
            HotbarManager.setExpanded(expanded);
        }
    }

    public static int getCurrentHotbarIndex() {
        return HotbarManager.getCurrentHotbarIndex();
    }

    public static void setCurrentHotbarIndex(int index) {
        HotbarManager.setCurrentHotbarIndex(index);
    }

    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 100 == 0) {
            System.out.println("[HotbarExpand] onClientTick running, expanded=" + isExpanded() + ", expanding=" + isExpanding + " progress=" + expandProgress);
        }
        
        if (isExpanding) {
            if (targetExpanded) {
                expandProgress += EXPAND_SPEED;
                if (expandProgress >= 1.0f) {
                    expandProgress = 1.0f;
                    isExpanding = false;
                }
            } else {
                expandProgress -= EXPAND_SPEED;
                if (expandProgress <= 0.0f) {
                    expandProgress = 0.0f;
                    isExpanding = false;
                }
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (ClientSetup.TOGGLE_EXPAND_KEY != null && ClientSetup.TOGGLE_EXPAND_KEY.consumeClick()) {
            if (Screen.hasControlDown()) {
                System.out.println("[HotbarExpand] Toggle expand key pressed");
                setExpanded(!targetExpanded);
            }
        }
        
        // 处理数字键
        handleNumberKeys();
    }

    private static void handleNumberKeys() {
        // 只在展开状态下处理（不按Alt时选择列表格子）
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        boolean canSelectListEntry = minecraft.screen == null && !Screen.hasAltDown();
        for (int i = 0; i < 9; i++) {
            KeyMapping mapping = ClientSetup.getHotbarKey(i);
            if (mapping == null) continue;

            while (mapping.consumeClick()) {
                if (canSelectListEntry) {
                    selectedSlotInList = i;
                    System.out.println("[HotbarExpand] Selected slot in list: " + (i + 1));
                }
            }
        }
    }
    
    // 使用InputEvent.Key处理Alt+数字键（可以取消事件）
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // 只在展开状态下处理
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;

        if (!Screen.hasAltDown()) return;

        for (int i = 0; i < 9; i++) {
            KeyMapping mapping = ClientSetup.getHotbarKey(i);
            if (matchesKeyMapping(event, mapping)) {
                System.out.println("[HotbarExpand] Alt+Hotbar key: " + (i + 1) + ", switching hotbar");
                setCurrentHotbarIndex(i);
                selectedSlotInList = i;
                cancelInputEvent(event);
                break;
            }
        }
    }
    
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;
        
        double scrollDelta = event.getScrollDeltaY();
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        int selectedSlot = minecraft.player.getInventory().selected;
        
        if (Screen.hasAltDown()) {
            // 按住Alt时切换快捷栏
            int currentIndex = getCurrentHotbarIndex();
            int newIndex;
            if (scrollDelta > 0) {
                newIndex = (currentIndex - 1 + 9) % 9;
            } else {
                newIndex = (currentIndex + 1) % 9;
            }
            System.out.println("[HotbarExpand] Mouse scroll switching to hotbar: " + (newIndex + 1));
            setCurrentHotbarIndex(newIndex);
            selectedSlotInList = newIndex; // 更新选中的列表格子
            // 取消事件，阻止原版滚轮行为
            try {
                java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
                method.invoke(event, true);
            } catch (Exception e) {
            }
        } else {
            // 不按Alt时：如果滚到尽头，自动切换快捷栏
            boolean shouldSwitchHotbar = false;
            int newHotbarIndex = getCurrentHotbarIndex();
            int newSlot = selectedSlot;
            
            if (scrollDelta > 0 && selectedSlot == 0) {
                // 向上滚且当前在第一个槽位，切换到上一个快捷栏的最后一个槽位
                shouldSwitchHotbar = true;
                newHotbarIndex = (getCurrentHotbarIndex() - 1 + 9) % 9;
                newSlot = 8;
            } else if (scrollDelta < 0 && selectedSlot == 8) {
                // 向下滚且当前在最后一个槽位，切换到下一个快捷栏的第一个槽位
                shouldSwitchHotbar = true;
                newHotbarIndex = (getCurrentHotbarIndex() + 1) % 9;
                newSlot = 0;
            }
            
            if (shouldSwitchHotbar) {
                System.out.println("[HotbarExpand] Auto switching to hotbar: " + (newHotbarIndex + 1) + ", slot: " + newSlot);
                setCurrentHotbarIndex(newHotbarIndex);
                selectedSlotInList = newHotbarIndex;
                minecraft.player.getInventory().selected = newSlot;
                // 取消事件，阻止原版滚轮行为
                try {
                    java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
                    method.invoke(event, true);
                } catch (Exception e) {
                }
            }
            // 其他情况让原版滚轮正常工作
        }
    }

    private static boolean matchesKeyMapping(InputEvent.Key event, KeyMapping mapping) {
        if (mapping == null) return false;

        InputConstants.Key boundKey = mapping.getKey();
        if (boundKey.getType() == InputConstants.Type.KEYSYM) {
            return event.getKey() == boundKey.getValue();
        }
        if (boundKey.getType() == InputConstants.Type.SCANCODE) {
            return event.getScanCode() == boundKey.getValue();
        }
        return false;
    }

    private static void cancelInputEvent(Object event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
            method.invoke(event, true);
        } catch (Exception ignored) {
        }
    }

    // 保持原版hotbar始终渲染，不再取消

    // 使用 RenderGuiEvent.Post 渲染（在所有GUI层之后）
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (minecraft.options.hideGui) return;

        // 只要有展开进度就渲染
        if (expandProgress <= 0.0f) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int hotbarWidth = 182;
        int hotbarHeight = 22;
        int offhandWidth = 29;

        int centerX = screenWidth / 2;
        int baseY = screenHeight - hotbarHeight;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        int currentIndex = getCurrentHotbarIndex();
        float smoothExpandProgress = HotbarManager.getSmoothProgress(expandProgress);
        
        // 只渲染右下角的9个快捷栏列表（垂直排列，固定1-9顺序）
        // 不再渲染主快捷栏复制，保留原版快捷栏
        if (smoothExpandProgress > 0.01f) {
            renderHotbarListAtBottomRight(guiGraphics, minecraft, screenWidth, screenHeight, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
        }
        
        RenderSystem.disableBlend();
    }
    
    // 渲染主快捷栏（带副手格和编号）
    private static void renderMainHotbarWithOffhand(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIdx) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        
        // 渲染副手格
        int offhandYOffset = 0;
        int itemXOffset = 3;
        if (isLeftHanded) {
            int offhandX = centerX + hotbarWidth / 2 + 4;
            guiGraphics.blit(HOTBAR_OFFHAND_RIGHT_LOCATION, offhandX, baseY + offhandYOffset, 0, 0, 29, 24, 29, 24);
            ItemStack offhandItem = minecraft.player.getOffhandItem();
            if (!offhandItem.isEmpty()) {
                guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, baseY + offhandYOffset + 4);
                guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, baseY + offhandYOffset + 4);
            }
        } else {
            int offhandX = centerX - hotbarWidth / 2 - offhandWidth;
            guiGraphics.blit(HOTBAR_OFFHAND_LEFT_LOCATION, offhandX, baseY + offhandYOffset, 0, 0, 29, 24, 29, 24);
            ItemStack offhandItem = minecraft.player.getOffhandItem();
            if (!offhandItem.isEmpty()) {
                guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, baseY + offhandYOffset + 4);
                guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, baseY + offhandYOffset + 4);
            }
        }
        
        // 渲染主快捷栏背景
        renderHotbarBackground(guiGraphics, centerX - hotbarWidth / 2, baseY);
        
        // 渲染选中框
        renderHotbarSelection(guiGraphics, centerX - hotbarWidth / 2, baseY, minecraft.player.getInventory().selected);
        
        // 渲染编号（黄色）
        int numberX = isLeftHanded ? centerX + hotbarWidth / 2 + 27 : centerX - hotbarWidth / 2 - 37;
        guiGraphics.drawString(minecraft.font, String.valueOf(currentIdx + 1), numberX, baseY + 6, 0xFFFF00);
        
        // 渲染物品
        renderItems(guiGraphics, centerX - hotbarWidth / 2, baseY, currentIdx);
    }
    
    // 在屏幕右下角渲染9个快捷栏列表（垂直排列，固定1-9顺序，从上到下）
    private static void renderHotbarListAtBottomRight(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        
        // 计算起始位置（绝对右下角，紧贴右边缘）
        int listCenterX = screenWidth - hotbarWidth / 2;
        
        // 固定1-9顺序，从上到下排列
        // 第1个在最上面，第9个在最下面（靠近屏幕底部）
        for (int i = 0; i < 9; i++) {
            // 从上到下：i=0在最上面，i=8在最下面
            int slotInList = i;
            // 从屏幕底部开始计算位置（右下角）
            // 第9个(i=8)在最下面，第1个(i=0)在最上面
            int targetY = screenHeight - (9 - slotInList) * hotbarHeight;
            
            // 动画：从下方滑入（延迟效果：下面的先出现）
            float slotProgress = Math.min(1.0f, Math.max(0.0f, smoothExpandProgress * 1.5f - (8 - slotInList) * 0.08f));
            int startY = screenHeight + hotbarHeight;
            int renderY = (int) (startY + (targetY - startY) * HotbarManager.getSmoothProgress(slotProgress));
            
            int offhandYOffset = 0;
            int itemXOffset = 3;
            
            // 当前快捷栏（currentIndex）和选中的列表格子（selectedSlotInList）都用黄色高亮
            boolean isCurrentHotbar = (i == currentIndex);
            boolean isSelectedSlot = (i == selectedSlotInList);
            boolean isHighlighted = isCurrentHotbar || isSelectedSlot;

            // 渲染副手格（高亮项用黄色）
            if (isHighlighted) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 0.0f, 1.0f);
            }
            if (isLeftHanded) {
                guiGraphics.blit(HOTBAR_OFFHAND_RIGHT_LOCATION, listCenterX + hotbarWidth / 2 + 4, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
            } else {
                guiGraphics.blit(HOTBAR_OFFHAND_LEFT_LOCATION, listCenterX - hotbarWidth / 2 - offhandWidth, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
            }
            if (isHighlighted) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
            
            // 渲染快捷栏背景（当前快捷栏或选中项用黄色高亮）
            if (isHighlighted) {
                renderHotbarBackgroundWithColor(guiGraphics, listCenterX - hotbarWidth / 2, renderY, 1.0f, 1.0f, 0.0f);
            } else {
                renderHotbarBackground(guiGraphics, listCenterX - hotbarWidth / 2, renderY);
            }
            
            // 渲染编号（白色，高亮项用黄色）
            int numberColor = isHighlighted ? 0xFFFF00 : 0xFFFFFF;
            int numberX = isLeftHanded ? listCenterX + hotbarWidth / 2 + 27 : listCenterX - hotbarWidth / 2 - 37;
            guiGraphics.drawString(minecraft.font, String.valueOf(i + 1), numberX, renderY + 6, numberColor);
            
            // 渲染物品
            renderItems(guiGraphics, listCenterX - hotbarWidth / 2, renderY, i);
            
            // 渲染副手物品
            renderOffhandItem(guiGraphics, minecraft, listCenterX, renderY, hotbarWidth, offhandWidth, isLeftHanded, itemXOffset, offhandYOffset, i);
            
            // 如果是当前快捷栏，渲染选中框（显示当前选中的物品槽位）
            if (isCurrentHotbar) {
                int selectedSlot = minecraft.player.getInventory().selected;
                renderHotbarSelection(guiGraphics, listCenterX - hotbarWidth / 2, renderY, selectedSlot);
            }
        }
    }
    
    // 渲染副手物品
    private static void renderOffhandItem(GuiGraphics guiGraphics, Minecraft minecraft, int listCenterX, int renderY, int hotbarWidth, int offhandWidth, boolean isLeftHanded, int itemXOffset, int offhandYOffset, int hotbarIndex) {
        ItemStack offhandItem = HotbarManager.getOffhandItem(hotbarIndex);
        if (offhandItem.isEmpty()) return;
        
        if (isLeftHanded) {
            int offhandX = listCenterX + hotbarWidth / 2 + 4;
            guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
        } else {
            int offhandX = listCenterX - hotbarWidth / 2 - offhandWidth;
            guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
        }
    }

    private static void renderHotbarBackground(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
    }
    
    private static void renderHotbarBackgroundWithColor(GuiGraphics guiGraphics, int x, int y, float r, float g, float b) {
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private static void renderHotbarSelection(GuiGraphics guiGraphics, int x, int y, int selectedSlot) {
        int selectX = x - 1 + selectedSlot * 20;
        int selectY = y - 1;
        guiGraphics.blit(HOTBAR_SELECTION_LOCATION, selectX, selectY, 0, 0, 24, 24, 24, 24);
    }

    private static void renderItems(GuiGraphics guiGraphics, int x, int y, int hotbarIndex) {
        List<ItemStack> hotbar = HotbarManager.getHotbar(hotbarIndex);
        for (int i = 0; i < 9; i++) {
            int slotX = x + 3 + i * 20;
            int slotY = y + 3;
            ItemStack itemStack = hotbar.get(i);
            if (!itemStack.isEmpty()) {
                guiGraphics.renderItem(itemStack, slotX, slotY);
                guiGraphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, slotX, slotY);
            }
        }
    }
}
