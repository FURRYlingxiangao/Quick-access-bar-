package com.example.hotbarexpand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
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

    // 在ClientTick中检测按键（用于非取消性操作）
    private static boolean[] keyWasDown = new boolean[10]; // 0-9
    
    private static void handleNumberKeys() {
        // 只在展开状态下处理
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        // 只更新keyWasDown状态，不再处理列表格子选择（避免黄框问题）
        for (int i = 1; i <= 9; i++) {
            int keyCode = 48 + i;
            keyWasDown[i] = org.lwjgl.glfw.GLFW.glfwGetKey(minecraft.getWindow().getWindow(), keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
    }
    
    // 使用InputEvent.Key处理Alt+数字键（可以取消事件）
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // 只在展开状态下处理
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;
        
        int key = event.getKey();
        // 数字键1-9 (GLFW_KEY_1 = 49)
        if (key >= 49 && key <= 57) {
            // 检查是否按下了Alt键
            if (Screen.hasAltDown()) {
                // 获取当前滚动偏移和实际快捷栏数量
                int scrollOffset = getScrollOffsetFromGUI();
                int maxHotbars = getMaxHotbarsFromGUI();
                int visibleHotbars = Math.min(9, maxHotbars - scrollOffset);
                
                // 获取按下的数字键在可见列表中的位置（0-8）
                int visibleIndex = key - 49; // 0-8
                
                // 检查是否在可见范围内
                if (visibleIndex < visibleHotbars) {
                    // 计算实际的快捷栏索引
                    int targetIndex = scrollOffset + visibleIndex;
                    System.out.println("[HotbarExpand] Alt+Number key: " + (visibleIndex + 1) + " in visible list, actual hotbar: " + (targetIndex + 1));
                    setCurrentHotbarIndex(targetIndex);
                    // 不设置selectedSlotInList，避免黄框残留
                }
                
                // 取消事件，阻止原版物品选择
                try {
                    java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
                    method.invoke(event, true);
                } catch (Exception e) {
                }
            } else {
                // 不按Alt时，如果展开状态，也取消数字键事件，防止影响物品栏
                // 但只在游戏界面（没有打开其他屏幕）时处理
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == null) {
                    try {
                        java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
                        method.invoke(event, true);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isExpanded()) return;
        if (event.getAction() != InputConstants.PRESS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (minecraft.screen != null) return;
        if (!isPickItemButton(event.getButton())) return;

        System.out.println("[HotbarExpand] Overlay pick click detected: button=" + event.getButton() + ", screen=" + minecraft.screen + ", expanded=" + isExpanded());

        ItemStack targetItem = getPickBlockItem();
        if (targetItem.isEmpty()) {
            System.out.println("[HotbarExpand] Overlay pick click targetItem is empty");
            return;
        }

        boolean found = HotbarManager.findAndSelectItem(targetItem);
        System.out.println("[HotbarExpand] Overlay pick click findAndSelectItem returned: " + found + ", target=" + targetItem.getItem());
        if (found) {
            event.setCanceled(true);
            System.out.println("[HotbarExpand] Overlay pick click canceled vanilla behavior");
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
        
        // 获取快捷栏数量和滚动偏移
        int maxHotbars = getMaxHotbarsFromGUI();
        int scrollOffset = getScrollOffsetFromGUI();
        
        if (Screen.hasAltDown()) {
            // 按住Alt时切换快捷栏
            int currentIndex = getCurrentHotbarIndex();
            int newIndex;
            if (scrollDelta > 0) {
                newIndex = currentIndex - 1;
                if (newIndex < 0) newIndex = maxHotbars - 1;
            } else {
                newIndex = currentIndex + 1;
                if (newIndex >= maxHotbars) newIndex = 0;
            }
            System.out.println("[HotbarExpand] Mouse scroll switching to hotbar: " + (newIndex + 1));
            setCurrentHotbarIndex(newIndex);

            // 自动调整滚动偏移，确保选中的快捷栏可见
            autoAdjustScrollOffset(newIndex, maxHotbars);

            // 取消事件，阻止原版滚轮行为
            event.setCanceled(true);
        } else {
            // 不按Alt时：如果滚到尽头，自动切换快捷栏
            boolean shouldSwitchHotbar = false;
            int newHotbarIndex = getCurrentHotbarIndex();
            int newSlot = selectedSlot;
            
            if (scrollDelta > 0 && selectedSlot == 0) {
                // 向上滚且当前在第一个槽位，切换到上一个快捷栏的最后一个槽位
                shouldSwitchHotbar = true;
                newHotbarIndex = getCurrentHotbarIndex() - 1;
                if (newHotbarIndex < 0) newHotbarIndex = maxHotbars - 1;
                newSlot = 8;
            } else if (scrollDelta < 0 && selectedSlot == 8) {
                // 向下滚且当前在最后一个槽位，切换到下一个快捷栏的第一个槽位
                shouldSwitchHotbar = true;
                newHotbarIndex = getCurrentHotbarIndex() + 1;
                if (newHotbarIndex >= maxHotbars) newHotbarIndex = 0;
                newSlot = 0;
            }
            
            if (shouldSwitchHotbar) {
                System.out.println("[HotbarExpand] Auto switching to hotbar: " + (newHotbarIndex + 1) + ", slot: " + newSlot);
                setCurrentHotbarIndex(newHotbarIndex);
                minecraft.player.getInventory().selected = newSlot;
                
                // 自动调整滚动偏移，确保选中的快捷栏可见
                autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                
                // 取消事件，阻止原版滚轮行为
                event.setCanceled(true);
            }
            // 其他情况让原版滚轮正常工作
        }
    }
    
    /**
     * 自动调整滚动偏移，确保指定的快捷栏在可见区域内
     */
    private static void autoAdjustScrollOffset(int hotbarIndex, int maxHotbars) {
        if (maxHotbars <= 9) return; // 不需要滚动
        
        int scrollOffset = getScrollOffsetFromGUI();
        int visibleHotbars = 9;
        
        // 如果选中的快捷栏在可见区域上方，向上滚动
        if (hotbarIndex < scrollOffset) {
            setScrollOffsetToGUI(hotbarIndex);
        }
        // 如果选中的快捷栏在可见区域下方，向下滚动
        else if (hotbarIndex >= scrollOffset + visibleHotbars) {
            setScrollOffsetToGUI(hotbarIndex - visibleHotbars + 1);
        }
    }
    
    /**
     * 设置滚动偏移到HotbarInventoryScreen
     */
    private static void setScrollOffsetToGUI(int offset) {
        try {
            Class<?> guiClass = Class.forName("com.example.hotbarexpand.client.gui.HotbarInventoryScreen");
            java.lang.reflect.Method method = guiClass.getMethod("setScrollOffset", int.class);
            method.invoke(null, offset);
        } catch (Exception e) {
            // 忽略错误
        }
    }

    // 保持原版hotbar始终渲染，不再取消

    // 使用 RenderGuiEvent.Post 渲染（在所有GUI层之后）
    private static boolean isPickItemButton(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return button == 2;
        }
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
        return minecraft.options.keyPickItem.isActiveAndMatches(mouseKey) || button == 2;
    }

    private static ItemStack getPickBlockItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return ItemStack.EMPTY;
        var hitResult = minecraft.hitResult;
        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var blockState = minecraft.level.getBlockState(blockPos);
            var block = blockState.getBlock();
            ItemStack pickBlock = block.getCloneItemStack(minecraft.level, blockPos, blockState);
            if (!pickBlock.isEmpty()) {
                return pickBlock;
            }
            var item = block.asItem();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }

        var pickResult = minecraft.player.pick(20.0, 0.0F, false);
        if (pickResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var blockState = minecraft.level.getBlockState(blockPos);
            var block = blockState.getBlock();
            ItemStack pickBlock = block.getCloneItemStack(minecraft.level, blockPos, blockState);
            if (!pickBlock.isEmpty()) {
                return pickBlock;
            }
            var item = block.asItem();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

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
        
        // 在原版快捷栏和副手框之间渲染白条（显示当前在列表中的位置）
        if (smoothExpandProgress > 0.01f) {
            renderPositionIndicator(guiGraphics, minecraft, screenWidth, screenHeight, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
        }
        
        // 只渲染右下角的快捷栏列表（垂直排列，固定1-9顺序）
        // 不再渲染主快捷栏复制，保留原版快捷栏
        if (smoothExpandProgress > 0.01f) {
            renderHotbarListAtBottomRight(guiGraphics, minecraft, screenWidth, screenHeight, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
        }
        
        RenderSystem.disableBlend();
    }
    
    /**
     * 在原版快捷栏和副手框之间渲染白条指示器
     * 显示当前快捷栏在列表中的位置
     * 限宽6格，纵向无限排列（即每列6个，向右新开列）
     */
    private static void renderPositionIndicator(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        
        int centerX = screenWidth / 2;
        int baseY = screenHeight - hotbarHeight;
        
        // 获取实际快捷栏数量
        int maxHotbars = getMaxHotbarsFromGUI();
        
        // 白条的位置：在快捷栏和副手框之间
        int barWidth = 4; // 白条宽度
        int barHeight = 3; // 每个小段高度
        int barGapX = 2; // 横向间隔
        int barGapY = 1; // 纵向间隔
        int gap = 2; // 与快捷栏/副手框的间隙
        int maxPerCol = 6; // 每列最多6格
        
        // 计算需要多少列
        int numCols = (maxHotbars + maxPerCol - 1) / maxPerCol; // 向上取整
        
        // 计算白条区域的总宽度
        int totalWidth = numCols * barWidth + (numCols - 1) * barGapX;
        
        int baseBarX;
        if (isLeftHanded) {
            // 左手模式：副手在右边，白条在快捷栏右侧
            baseBarX = centerX + hotbarWidth / 2 + gap;
        } else {
            // 右手模式：副手在左边，白条在副手框右侧（即快捷栏左侧）
            // 从右向左排列，最右边是第一列
            baseBarX = centerX - hotbarWidth / 2 - offhandWidth - gap - totalWidth;
        }
        
        // 计算起始Y位置（垂直居中）
        int colHeight = maxPerCol * barHeight + (maxPerCol - 1) * barGapY;
        int startY = baseY + (hotbarHeight - colHeight) / 2;
        
        // 渲染所有小段（按列渲染，纵向排列）
        for (int i = 0; i < maxHotbars; i++) {
            int col = i / maxPerCol; // 列索引
            int row = i % maxPerCol; // 行索引
            
            int barX;
            if (isLeftHanded) {
                // 左手模式：从左向右排列列
                barX = baseBarX + col * (barWidth + barGapX);
            } else {
                // 右手模式：从左向右排列列（最右边是第一列）
                barX = baseBarX + col * (barWidth + barGapX);
            }
            
            int segmentY = startY + row * (barHeight + barGapY);
            int color = (i == currentIndex) ? 0xFFFFFFFF : 0xFF808080; // 当前位置白色，其他灰色
            guiGraphics.fill(barX, segmentY, barX + barWidth, segmentY + barHeight, color);
        }
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
    
    // 在屏幕右下角渲染快捷栏列表（垂直排列，固定1-9顺序，从上到下）
    // 最多显示9个，超过9个时通过滚动查看
    private static void renderHotbarListAtBottomRight(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        
        // 计算起始位置（绝对右下角，紧贴右边缘）
        int listCenterX = screenWidth - hotbarWidth / 2;
        
        // 获取实际快捷栏数量和滚动偏移
        int maxHotbars = getMaxHotbarsFromGUI();
        int scrollOffset = getScrollOffsetFromGUI();
        int visibleHotbars = Math.min(9, maxHotbars);
        
        // 固定1-9顺序，从上到下排列
        // 根据滚动偏移显示不同的9个快捷栏
        for (int displayIdx = 0; displayIdx < visibleHotbars; displayIdx++) {
            int actualIdx = scrollOffset + displayIdx; // 实际的快捷栏索引
            if (actualIdx >= maxHotbars) break;
            
            // 从上到下：displayIdx=0在最上面
            // 从屏幕底部开始计算位置（右下角）
            int targetY = screenHeight - (visibleHotbars - displayIdx) * hotbarHeight;
            
            // 动画：从下方滑入（延迟效果：下面的先出现）
            float slotProgress = Math.min(1.0f, Math.max(0.0f, smoothExpandProgress * 1.5f - (visibleHotbars - 1 - displayIdx) * 0.08f));
            int startY = screenHeight + hotbarHeight;
            int renderY = (int) (startY + (targetY - startY) * HotbarManager.getSmoothProgress(slotProgress));
            
            int offhandYOffset = 0;
            int itemXOffset = 3;
            
            // 只有当前快捷栏（currentIndex）用黄色高亮
            boolean isCurrentHotbar = (actualIdx == currentIndex);
            boolean isHighlighted = isCurrentHotbar;

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
            String numberStr = String.valueOf(actualIdx + 1);
            // 2位数居中渲染 - 编号在副手格左侧，居中显示
            int textWidth = minecraft.font.width(numberStr);
            // 副手格宽度29，编号在副手格左侧，向左90像素
            int numberXOffset = isLeftHanded ? 27 + (14 - textWidth) / 2 : -29 - 14 - 190 + (14 - textWidth) / 2;
            guiGraphics.drawString(minecraft.font, numberStr, listCenterX + hotbarWidth / 2 + numberXOffset, renderY + 6, numberColor);
            
            // 渲染物品
            renderItems(guiGraphics, listCenterX - hotbarWidth / 2, renderY, actualIdx);
            
            // 渲染副手物品
            renderOffhandItem(guiGraphics, minecraft, listCenterX, renderY, hotbarWidth, offhandWidth, isLeftHanded, itemXOffset, offhandYOffset, actualIdx);
            
            // 如果是当前快捷栏，渲染选中框（显示当前选中的物品槽位）
            if (isCurrentHotbar) {
                int selectedSlot = minecraft.player.getInventory().selected;
                renderHotbarSelection(guiGraphics, listCenterX - hotbarWidth / 2, renderY, selectedSlot);
            }
        }
        
        // 如果快捷栏数量超过9个，渲染滚动指示器（小白条）
        if (maxHotbars > 9) {
            renderScrollIndicator(guiGraphics, screenWidth, screenHeight, hotbarHeight, scrollOffset, maxHotbars, visibleHotbars, smoothExpandProgress);
        }
    }
    
    /**
     * 渲染滚动指示器（小白条）
     */
    private static void renderScrollIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int hotbarHeight, int scrollOffset, int maxHotbars, int visibleHotbars, float smoothExpandProgress) {
        // 滚动指示器位置：在快捷栏列表的左侧，往右2像素
        int indicatorX = screenWidth - 182 - 8 - 4 + 10 - 5 + 2; // 快捷栏左侧留出空间，往右2像素
        int listHeight = visibleHotbars * hotbarHeight;
        int startY = screenHeight - listHeight;
        
        // 计算指示器高度和位置
        float ratio = (float) visibleHotbars / maxHotbars;
        int indicatorHeight = Math.max(8, (int)(listHeight * ratio));
        int maxScroll = maxHotbars - visibleHotbars;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int indicatorY = startY + (int)((listHeight - indicatorHeight) * scrollRatio);
        
        // 渲染指示器背景（灰色）
        guiGraphics.fill(indicatorX, startY, indicatorX + 3, startY + listHeight, 0x40808080);
        // 渲染指示器滑块（白色）
        guiGraphics.fill(indicatorX, indicatorY, indicatorX + 3, indicatorY + indicatorHeight, 0xFFFFFFFF);
    }
    
    /**
     * 从HotbarInventoryScreen获取滚动偏移
     */
    private static int getScrollOffsetFromGUI() {
        try {
            Class<?> guiClass = Class.forName("com.example.hotbarexpand.client.gui.HotbarInventoryScreen");
            java.lang.reflect.Method method = guiClass.getMethod("getScrollOffset");
            return (int) method.invoke(null);
        } catch (Exception e) {
            return 0; // 默认从0开始
        }
    }
    
    /**
     * 从HotbarInventoryScreen获取最大快捷栏数量
     */
    private static int getMaxHotbarsFromGUI() {
        try {
            Class<?> guiClass = Class.forName("com.example.hotbarexpand.client.gui.HotbarInventoryScreen");
            java.lang.reflect.Field field = guiClass.getDeclaredField("maxHotbars");
            field.setAccessible(true);
            return (int) field.get(null);
        } catch (Exception e) {
            return 9; // 默认9个
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
