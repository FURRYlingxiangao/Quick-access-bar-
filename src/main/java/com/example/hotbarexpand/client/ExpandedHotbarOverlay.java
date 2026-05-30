package com.example.hotbarexpand.client;

import com.example.hotbarexpand.client.HotbarConfig;
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
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

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

    // 组内选中的快捷栏索引（用于非1x1模式）
    private static int selectedHotbarInGroup = 0;

    // 主界面快捷栏列表的滚动偏移（独立于背包界面）
    private static int mainScrollOffset = 0;

    // 自动关闭计时器
    private static long autoHideStartTime = 0;
    private static boolean isAutoHiding = false;

    // 切换时展开计时器
    private static long switchExpandStartTime = 0;
    private static boolean isSwitchExpanding = false;

    // 透明度过渡动画
    private static float currentAlpha = 1.0f;
    private static final float ALPHA_TRANSITION_SPEED = 0.1f;

    public static boolean isExpanded() {
        return HotbarManager.isExpanded();
    }

    /**
     * 检查指定快捷栏是否在当前组内（考虑补齐情况）
     */
    private static boolean isInCurrentGroup(int hotbarIdx) {
        int currentIndex = getCurrentHotbarIndex();
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        int layoutSize = layout.getTotalHotbars();
        int groupStart = (currentIndex / layoutSize) * layoutSize;
        int maxHotbars = HotbarManager.getHotbarCount();

        // 计算当前组的所有索引（包括补齐的）
        for (int i = 0; i < layoutSize; i++) {
            int idx = getPaddedHotbarIndex(groupStart + i, maxHotbars);
            if (idx == hotbarIdx) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取补齐后的快捷栏索引，如果超出范围则用列表首位循环补齐
     */
    private static int getPaddedHotbarIndex(int index, int maxHotbars) {
        if (maxHotbars <= 0) {
            return 0;
        }
        if (index < maxHotbars) {
            return index;
        }
        // 超出范围，用列表首位循环补齐
        return index % maxHotbars;
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

    /**
     * 重置展开状态（游戏模式切换时调用）
     */
    public static void resetExpandedState() {
        targetExpanded = false;
        expandProgress = 0.0f;
        isExpanding = false;
        HotbarManager.setExpanded(false);
        System.out.println("[HotbarExpand] Expanded state reset");
    }

    private static int tickCounter = 0;
    private static int lastHotbarIndex = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 100 == 0) {
            System.out.println("[HotbarExpand] onClientTick running, expanded=" + isExpanded() + ", expanding=" + isExpanding + " progress=" + expandProgress);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int currentIndex = getCurrentHotbarIndex();

        // 处理展开模式
        HotbarConfig.ExpandMode expandMode = HotbarConfig.getExpandMode();
        switch (expandMode) {
            case AUTO_HIDE -> {
                // 自动关闭模式：展开后一段时间自动关闭
                if (targetExpanded && !isExpanding && expandProgress >= 1.0f) {
                    if (!isAutoHiding) {
                        autoHideStartTime = System.currentTimeMillis();
                        isAutoHiding = true;
                    } else {
                        long elapsed = System.currentTimeMillis() - autoHideStartTime;
                        if (elapsed >= HotbarConfig.getAutoHideDelay()) {
                            setExpanded(false);
                            isAutoHiding = false;
                        }
                    }
                } else if (!targetExpanded) {
                    isAutoHiding = false;
                }
            }
            case SWITCH_EXPAND -> {
                // 切换时展开模式：切换快捷栏时自动展开，完成后自动收起
                if (currentIndex != lastHotbarIndex) {
                    // 快捷栏切换了
                    lastHotbarIndex = currentIndex;
                    if (!targetExpanded) {
                        setExpanded(true);
                        isSwitchExpanding = true;
                        switchExpandStartTime = System.currentTimeMillis();
                    } else if (isSwitchExpanding) {
                        // 已经在展开状态，重置计时器
                        switchExpandStartTime = System.currentTimeMillis();
                    }
                }

                if (isSwitchExpanding && targetExpanded && !isExpanding && expandProgress >= 1.0f) {
                    long elapsed = System.currentTimeMillis() - switchExpandStartTime;
                    if (elapsed >= HotbarConfig.getAutoHideDelay()) {
                        setExpanded(false);
                        isSwitchExpanding = false;
                    }
                }
            }
            case ALWAYS_SHOW -> {
                // 一直显示模式：手动切换，不自动收起也不自动展开
                // 什么也不做，完全依赖手动切换
            }
        }

        // 处理展开动画
        if (isExpanding) {
            if (targetExpanded) {
                expandProgress += EXPAND_SPEED * HotbarConfig.getAnimationSpeed();
                if (expandProgress >= 1.0f) {
                    expandProgress = 1.0f;
                    isExpanding = false;
                }
            } else {
                expandProgress -= EXPAND_SPEED * HotbarConfig.getAnimationSpeed();
                if (expandProgress <= 0.0f) {
                    expandProgress = 0.0f;
                    isExpanding = false;
                }
            }
        }

        if (ClientSetup.TOGGLE_EXPAND_KEY != null && ClientSetup.TOGGLE_EXPAND_KEY.consumeClick()) {
            if (Screen.hasControlDown()) {
                System.out.println("[HotbarExpand] Toggle expand key pressed");
                // 手动切换时重置自动隐藏状态
                isAutoHiding = false;
                isSwitchExpanding = false;
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
        // 处理Ctrl+数字键切换布局（不需要展开状态）
        int key = event.getKey();
        if (key >= 49 && key <= 52 && Screen.hasControlDown()) { // Ctrl+1,2,3,4
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) { // 只在游戏界面处理
                int layoutNum = key - 49; // 0,1,2,3 对应 1,2,3,4
                HotbarConfig.LayoutMode newLayout;
                switch (layoutNum) {
                    case 0:
                        newLayout = HotbarConfig.LayoutMode.ONE_X_ONE;
                        break;
                    case 1:
                        newLayout = HotbarConfig.LayoutMode.ONE_X_TWO;
                        break;
                    case 2:
                        newLayout = HotbarConfig.LayoutMode.TWO_X_ONE;
                        break;
                    case 3:
                        newLayout = HotbarConfig.LayoutMode.TWO_X_TWO;
                        break;
                    default:
                        return;
                }
                HotbarConfig.setLayout(newLayout);
                // 切换布局时重置展开状态，避免跨组逻辑异常
                if (isExpanded()) {
                    setExpanded(false);
                    expandProgress = 0.0f;
                    targetExpanded = false;
                    isExpanding = false;
                }
                System.out.println("[HotbarExpand] Ctrl+" + (layoutNum + 1) + " switched layout to: " + newLayout.displayName);

                // 取消事件
                try {
                    java.lang.reflect.Method method = event.getClass().getMethod("setCanceled", boolean.class);
                    method.invoke(event, true);
                } catch (Exception e) {
                }
                return;
            }
        }

        // 只在展开状态下处理其他按键
        if (!isExpanded() && !isExpanding) return;
        if (expandProgress < 0.3f) return;

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

        // 无论是否展开，都尝试查找并选择物品
        boolean found = HotbarManager.findAndSelectItem(targetItem);
        System.out.println("[HotbarExpand] Overlay pick click findAndSelectItem returned: " + found + ", target=" + targetItem.getItem());
        if (found) {
            // 取消原版行为，防止冲突
            event.setCanceled(true);
            System.out.println("[HotbarExpand] Overlay pick click canceled vanilla behavior");
        } else {
            // 创造模式下：如果没有找到物品，将物品给予玩家
            if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.CREATIVE) {
                event.setCanceled(true);
                // 给予玩家物品（设置到当前选中的槽位）
                ItemStack giveStack = targetItem.copy();
                giveStack.setCount(giveStack.getMaxStackSize());
                int selectedSlot = minecraft.player.getInventory().selected;
                minecraft.player.getInventory().setItem(selectedSlot, giveStack);
                // 同步到服务器
                HotbarManager.syncItemToServer(selectedSlot, giveStack);
                // 保存到当前快捷栏
                HotbarManager.setHotbarItem(HotbarManager.getCurrentHotbarIndex(), selectedSlot, giveStack);
                System.out.println("[HotbarExpand] Creative mode: gave item " + giveStack.getItem() + " to slot " + selectedSlot);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        // 观察者模式下不拦截滚轮
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR) {
            return;
        }
        
        // 非1x1模式下，完全接管滚轮行为（包括收起状态）
        if (HotbarConfig.getLayout() != HotbarConfig.LayoutMode.ONE_X_ONE) {
            // 收起状态下也处理滚轮，进行组内循环
            // 只有在展开/收起动画过程中才不处理
            if (isExpanding && expandProgress > 0.0f && expandProgress < 0.3f) return;
            
            double scrollDelta = event.getScrollDeltaY();
            
            if (minecraft.player == null) return;
            
            int selectedSlot = minecraft.player.getInventory().selected;
            int maxHotbars = HotbarManager.getHotbarCount();
            HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
            int currentIndex = getCurrentHotbarIndex();
            
            if (Screen.hasAltDown()) {
                // 按住Alt时直接切换快捷栏（无论是否展开）
                handleAltScroll(minecraft, scrollDelta, currentIndex, maxHotbars, event);
            } else {
                // 不按Alt时：根据布局模式处理滚动（收起和展开都处理）
                handleScrollByLayout(event, minecraft, scrollDelta, selectedSlot, currentIndex, maxHotbars, layout);
            }
            return;
        }
        
        // 1x1模式下：收起时单栏循环不跨栏，展开时普通滚轮和Alt+滚轮都可以跨栏
        if (!isExpanded() && !isExpanding) {
            // 1x1收起状态：单栏内循环，不跨栏
            // 不处理滚轮事件，保持原版行为（slot 0-8之间循环）
        } else {
            // 1x1展开状态：普通滚轮滚到头跨栏，Alt+滚轮直接跨栏
            if (expandProgress < 0.3f) return;
            
            double scrollDelta = event.getScrollDeltaY();
            if (minecraft.player == null) return;
            
            int selectedSlot = minecraft.player.getInventory().selected;
            int maxHotbars = getMaxHotbarsFromGUI();
            int currentIndex = getCurrentHotbarIndex();
            
            if (Screen.hasAltDown()) {
                // Alt+滚轮直接跨栏
                handleAltScroll(minecraft, scrollDelta, currentIndex, maxHotbars, event);
            } else {
                // 普通滚轮：滚到头时跨栏
                // 处理向上滚动（slot减小到0）- 切换到上一个快捷栏
                if (scrollDelta > 0 && selectedSlot == 0) {
                    int newIndex = currentIndex - 1;
                    if (newIndex < 0) newIndex = maxHotbars - 1;
                    minecraft.player.getInventory().selected = 8;
                    setCurrentHotbarIndex(newIndex);
                    autoAdjustScrollOffset(newIndex, maxHotbars);
                    System.out.println("[HotbarExpand] 1x1 expanded scroll up to previous hotbar: " + (newIndex + 1));
                    event.setCanceled(true);
                }
                // 处理向下滚动（slot增大到8）- 切换到下一个快捷栏
                else if (scrollDelta < 0 && selectedSlot == 8) {
                    int newIndex = currentIndex + 1;
                    if (newIndex >= maxHotbars) newIndex = 0;
                    minecraft.player.getInventory().selected = 0;
                    setCurrentHotbarIndex(newIndex);
                    autoAdjustScrollOffset(newIndex, maxHotbars);
                    System.out.println("[HotbarExpand] 1x1 expanded scroll down to next hotbar: " + (newIndex + 1));
                    event.setCanceled(true);
                }
            }
        }
    }
    
    private static void handleAltScroll(Minecraft minecraft, double scrollDelta, int currentIndex, int maxHotbars, InputEvent.MouseScrollingEvent event) {
        // 如果超过9个快捷栏，Alt+滚轮仅在前9个中循环
        int effectiveMaxHotbars = HotbarConfig.isExceedingAltLimit() ? Math.min(9, maxHotbars) : maxHotbars;
        int effectiveCurrentIndex = currentIndex >= effectiveMaxHotbars ? 0 : currentIndex;

        int newIndex;
        if (scrollDelta > 0) {
            newIndex = effectiveCurrentIndex - 1;
            if (newIndex < 0) newIndex = effectiveMaxHotbars - 1;
        } else {
            newIndex = effectiveCurrentIndex + 1;
            if (newIndex >= effectiveMaxHotbars) newIndex = 0;
        }
        System.out.println("[HotbarExpand] Alt+Scroll switching to hotbar: " + (newIndex + 1) + " (effective max: " + effectiveMaxHotbars + ")");
        setCurrentHotbarIndex(newIndex);
        autoAdjustScrollOffset(newIndex, maxHotbars);
        event.setCanceled(true);
    }
    
    /**
     * 根据布局模式处理滚动
     */
    private static void handleScrollByLayout(InputEvent.MouseScrollingEvent event, Minecraft minecraft, double scrollDelta,
            int selectedSlot, int currentIndex, int maxHotbars, HotbarConfig.LayoutMode layout) {

        // 计算当前组的大小（使用补齐后的逻辑大小）
        int layoutSize = layout.getTotalHotbars();
        int groupStart = (currentIndex / layoutSize) * layoutSize;

        // 计算当前在组内的位置（考虑补齐情况）
        int currentHotbarInGroup = -1;
        for (int i = 0; i < layoutSize; i++) {
            if (getPaddedHotbarIndex(groupStart + i, maxHotbars) == currentIndex) {
                currentHotbarInGroup = i;
                break;
            }
        }
        // 如果找不到，默认在第一个位置
        if (currentHotbarInGroup < 0) {
            currentHotbarInGroup = 0;
        }

        // 处理向上滚动（slot减小）
        if (scrollDelta > 0) {
            if (selectedSlot > 0) {
                // 在当前快捷栏内正常滚动
                int newSlot = selectedSlot - 1;
                minecraft.player.getInventory().selected = newSlot;
                event.setCanceled(true);
                return;
            } else if (selectedSlot == 0) {
                // 已经在第一个槽位，检查是否是组内第一个快捷栏
                if (currentHotbarInGroup > 0) {
                    // 不是组内第一个，切换到组内上一个快捷栏的最后一个槽位
                    int newHotbarIndex = getPaddedHotbarIndex(groupStart + currentHotbarInGroup - 1, maxHotbars);
                    minecraft.player.getInventory().selected = 8;
                    setCurrentHotbarIndex(newHotbarIndex);
                    autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                    System.out.println("[HotbarExpand] Scroll up to previous hotbar in group: " + (newHotbarIndex + 1));
                } else {
                    // 是组内第一个快捷栏
                    if (!isExpanded()) {
                        // 列表收起时：循环到组内最后一个快捷栏（使用补齐逻辑）
                        int newHotbarIndex = getPaddedHotbarIndex(groupStart + layoutSize - 1, maxHotbars);
                        minecraft.player.getInventory().selected = 8;
                        setCurrentHotbarIndex(newHotbarIndex);
                        autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                        System.out.println("[HotbarExpand] Group cycle to last hotbar: " + (newHotbarIndex + 1));
                    } else {
                        // 列表展开时：切换到上一组
                        int newGroupStart;
                        if (groupStart == 0) {
                            // 当前是第一组，循环到最后一组
                            newGroupStart = ((maxHotbars - 1) / layoutSize) * layoutSize;
                        } else {
                            newGroupStart = groupStart - layoutSize;
                        }
                        // 找到目标组的最后一个真实快捷栏（不使用补齐）
                        int newHotbarIndex = Math.min(newGroupStart + layoutSize - 1, maxHotbars - 1);
                        minecraft.player.getInventory().selected = 8;
                        setCurrentHotbarIndex(newHotbarIndex);
                        autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                        System.out.println("[HotbarExpand] Switch to previous group, hotbar: " + (newHotbarIndex + 1));
                    }
                }
                event.setCanceled(true);
                return;
            }
        }
        // 处理向下滚动（slot增大）
        else if (scrollDelta < 0) {
            if (selectedSlot < 8) {
                // 在当前快捷栏内正常滚动
                int newSlot = selectedSlot + 1;
                minecraft.player.getInventory().selected = newSlot;
                event.setCanceled(true);
                return;
            } else if (selectedSlot == 8) {
                // 已经在最后一个槽位，检查是否是组内最后一个快捷栏
                if (currentHotbarInGroup < layoutSize - 1) {
                    // 不是组内最后一个，切换到组内下一个快捷栏的第一个槽位
                    int newHotbarIndex = getPaddedHotbarIndex(groupStart + currentHotbarInGroup + 1, maxHotbars);
                    minecraft.player.getInventory().selected = 0;
                    setCurrentHotbarIndex(newHotbarIndex);
                    autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                    System.out.println("[HotbarExpand] Scroll down to next hotbar in group: " + (newHotbarIndex + 1));
                } else {
                    // 是组内最后一个快捷栏
                    if (!isExpanded()) {
                        // 列表收起时：循环到组内第一个快捷栏（使用补齐逻辑）
                        int newHotbarIndex = getPaddedHotbarIndex(groupStart, maxHotbars);
                        minecraft.player.getInventory().selected = 0;
                        setCurrentHotbarIndex(newHotbarIndex);
                        autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                        System.out.println("[HotbarExpand] Group cycle to first hotbar: " + (newHotbarIndex + 1));
                    } else {
                        // 列表展开时：切换到下一组
                        int newGroupStart = groupStart + layoutSize;
                        // 如果超出范围，从列表开头开始
                        if (newGroupStart >= maxHotbars) {
                            newGroupStart = 0;
                        }
                        // 找到目标组的第一个真实快捷栏
                        int newHotbarIndex = newGroupStart;
                        minecraft.player.getInventory().selected = 0;
                        setCurrentHotbarIndex(newHotbarIndex);
                        autoAdjustScrollOffset(newHotbarIndex, maxHotbars);
                        System.out.println("[HotbarExpand] Switch to next group, hotbar: " + (newHotbarIndex + 1));
                    }
                }
                event.setCanceled(true);
                return;
            }
        }
    }
    
    /**
     * 获取当前显示的快捷栏范围
     * @return int[2] {minIdx, maxIdx}
     */
    private static int[] getVisibleHotbarRange(int currentIndex, int maxHotbars, HotbarConfig.LayoutMode layout) {
        int layoutSize = layout.getTotalHotbars();
        
        switch (layout) {
            case ONE_X_ONE:
                // 1x1: 只显示当前快捷栏
                return new int[]{currentIndex, currentIndex};
            case ONE_X_TWO:
            case TWO_X_ONE:
                // 1x2和2x1: 显示2个快捷栏
                int groupStart = (currentIndex / 2) * 2;
                int groupEnd = Math.min(groupStart + 1, maxHotbars - 1);
                return new int[]{groupStart, groupEnd};
            case TWO_X_TWO:
                // 2x2: 显示4个快捷栏
                int groupStart4 = (currentIndex / 4) * 4;
                int groupEnd4 = Math.min(groupStart4 + 3, maxHotbars - 1);
                return new int[]{groupStart4, groupEnd4};
            default:
                return new int[]{currentIndex, currentIndex};
        }
    }
    
    /**
     * 自动调整滚动偏移，确保指定的快捷栏在可见区域内
     */
    private static void autoAdjustScrollOffset(int hotbarIndex, int maxHotbars) {
        int visibleHotbars = HotbarConfig.getMaxVisibleHotbars();
        if (maxHotbars <= visibleHotbars) return; // 不需要滚动

        int scrollOffset = getMainScrollOffset();

        // 如果选中的快捷栏在可见区域上方，向上滚动
        if (hotbarIndex < scrollOffset) {
            setMainScrollOffset(hotbarIndex);
        }
        // 如果选中的快捷栏在可见区域下方，向下滚动
        else if (hotbarIndex >= scrollOffset + visibleHotbars) {
            setMainScrollOffset(hotbarIndex - visibleHotbars + 1);
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

    /**
     * 取消原版快捷栏渲染（非1x1模式下）并移动其他GUI元素
     */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();

        // 观察者模式下不拦截原版快捷栏
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR) {
            return;
        }

        // 冒险/生存/创造模式都拦截原版快捷栏（由本模组渲染）
        // 包括打开背包界面时也要拦截，防止显示原版快捷栏
        if (HotbarConfig.getLayout() == HotbarConfig.LayoutMode.ONE_X_ONE) return;

        // 取消原版快捷栏渲染
        if (event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            event.setCanceled(true);
        }

        // 1x2和2x2模式下，将经验值条、血量、饱食度等向上移动
        // 只在主界面（screen == null）时移动，背包界面不移动
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        if ((layout == HotbarConfig.LayoutMode.ONE_X_TWO || layout == HotbarConfig.LayoutMode.TWO_X_TWO) && minecraft.screen == null) {
            // 方块字幕额外多移动24像素
            int offsetY = isOverlayMessageLayer(event.getName()) ? 24:24;

            // 需要向上移动的元素
            if (isMovableGuiLayer(event.getName())) {
                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(0, -offsetY, 0);
            }
        }
    }
    
    /**
     * 检查是否是方块字幕层（手持物品名称）
     */
    private static boolean isOverlayMessageLayer(ResourceLocation layerName) {
        return layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME);
    }
    
    /**
     * 检查是否需要移动的GUI层
     */
    private static boolean isMovableGuiLayer(ResourceLocation layerName) {
        return layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR) ||
               layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL) ||
               layerName.equals(VanillaGuiLayers.PLAYER_HEALTH) ||
               layerName.equals(VanillaGuiLayers.FOOD_LEVEL) ||
               layerName.equals(VanillaGuiLayers.ARMOR_LEVEL) ||
               layerName.equals(VanillaGuiLayers.AIR_LEVEL) ||
               layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME) ||
               layerName.equals(VanillaGuiLayers.JUMP_METER);
    }
    
    /**
     * GUI层渲染后恢复矩阵
     */
    @SubscribeEvent
    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // 观察者模式下不处理
        if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR) {
            return;
        }

        if (HotbarConfig.getLayout() == HotbarConfig.LayoutMode.ONE_X_ONE) return;

        // 只在主界面（screen == null）时恢复矩阵，背包界面不处理
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        if ((layout == HotbarConfig.LayoutMode.ONE_X_TWO || layout == HotbarConfig.LayoutMode.TWO_X_TWO) && minecraft.screen == null) {
            if (isMovableGuiLayer(event.getName())) {
                event.getGuiGraphics().pose().popPose();
            }
        }
    }

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

        // 遵守原版快捷栏隐藏逻辑
        if (minecraft.options.hideGui) return; // F1键隐藏GUI
        if (minecraft.gameMode == null) return; // 游戏模式未加载时不显示
        if (minecraft.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR) return; // 观察者模式显示原版快捷栏
        if (minecraft.getCameraEntity() != minecraft.player) return; // 第三人称视角观察其他实体时不显示
        // 注意：不再检查 minecraft.screen != null，允许在打开其他界面时渲染主界面快捷栏

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int hotbarWidth = 182;
        int hotbarHeight = 22;
        int offhandWidth = 29;

        // 主快捷栏始终使用底部居中位置和正常透明度
        // 原版快捷栏渲染在 screenHeight - 22 位置
        int mainBaseX = screenWidth / 2;
        int mainBaseY = screenHeight - 22;
        
        // 在1x2和2x2布局下，主快捷栏需要向上移动23像素，与经验条等GUI元素对齐
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        if (layout == HotbarConfig.LayoutMode.ONE_X_TWO || layout == HotbarConfig.LayoutMode.TWO_X_TWO) {
            mainBaseY -= 23;
        }
        
        float mainAlpha = 1.0f;

        // 根据显示位置计算拓展快捷栏的基础位置和透明度
        int[] position = calculatePosition(HotbarConfig.getDisplayPosition(), screenWidth, screenHeight, hotbarWidth, hotbarHeight);
        int expandBaseX = position[0];
        int expandBaseY = position[1];
        float expandAlpha = updateAlphaTransition();

        int currentIndex = getCurrentHotbarIndex();
        float smoothExpandProgress = HotbarManager.getSmoothProgress(expandProgress);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染小白格指示器（始终显示，不依赖列表展开状态）- 使用主快捷栏位置
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, mainAlpha);
        renderPositionIndicator(guiGraphics, minecraft, screenWidth, screenHeight, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress, mainAlpha);

        // 根据配置渲染主快捷栏布局（无论列表是否展开都渲染）- 始终使用底部居中位置和正常透明度
        renderMainHotbarByLayout(guiGraphics, minecraft, mainBaseX, mainBaseY, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress, mainAlpha);

        // 只渲染拓展快捷栏列表（垂直排列，固定1-9顺序）- 使用配置的位置和透明度
        if (smoothExpandProgress > 0.01f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, expandAlpha);
            renderHotbarListAtPosition(guiGraphics, minecraft, screenWidth, screenHeight, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress, expandAlpha);
        }

        RenderSystem.disableBlend();
    }

    /**
     * 根据显示位置计算基础位置
     */
    private static int[] calculatePosition(HotbarConfig.DisplayPosition position, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight) {
        int centerX = screenWidth / 2;
        int edgePadding = 2; // 紧贴边缘的间距

        // 向上偏移12像素，使列表更靠近屏幕边缘
        int yOffset = 12;
        
        switch (position) {
            case TOP_LEFT:
                // 左上：紧贴左上角
                // 返回第一个快捷栏的中心位置（紧贴顶部，无间距）
                return new int[]{hotbarWidth / 2, hotbarHeight / 2 - yOffset};
            case TOP_CENTER:
                // 中上：顶部居中，紧贴顶部
                // 返回第一个快捷栏的中心位置（紧贴顶部，无间距）
                return new int[]{centerX, hotbarHeight / 2 - yOffset};
            case TOP_RIGHT:
                // 右上：紧贴右上角
                // 返回第一个快捷栏的中心位置（紧贴顶部，无间距）
                return new int[]{screenWidth - hotbarWidth / 2, hotbarHeight / 2 - yOffset};
            case BOTTOM_RIGHT:
                // 右下：紧贴右下角
                // 返回最后一个快捷栏的中心位置（紧贴底部，无间距）
                return new int[]{screenWidth - hotbarWidth / 2, screenHeight - hotbarHeight / 2 - yOffset};
            case BOTTOM_LEFT:
                // 左下：紧贴左下角
                // 返回最后一个快捷栏的中心位置（紧贴底部，无间距）
                return new int[]{hotbarWidth / 2, screenHeight - hotbarHeight / 2 - yOffset};
            case ABOVE_HOTBAR:
                // 主快捷栏上部：在主快捷栏上方，避开方块字幕
                // 主快捷栏Y = screenHeight - 22 (或 -46 在1x2/2x2布局)
                // 字幕高度约20，留5像素间距
                // 返回中心X，最后一个快捷栏的中心Y（从底部往上）
                return new int[]{centerX, screenHeight - 22 - 20 - 5 - hotbarHeight / 2 - yOffset};
            default:
                return new int[]{centerX, screenHeight - 22 - 20 - 5 - hotbarHeight / 2 - yOffset};
        }
    }

    /**
     * 计算目标透明度
     */
    private static float calculateTargetAlpha() {
        HotbarConfig.TransparencyMode mode = HotbarConfig.getTransparencyMode();
        float baseAlpha = 1.0f - (HotbarConfig.getTransparency() / 100f);

        switch (mode) {
            case ALWAYS_TRANSPARENT:
                // 一直半透明
                return baseAlpha;
            case NORMAL_WHEN_SWITCH:
                // 切换时正常，平常半透明
                if (isSwitchExpanding || isExpanding || expandProgress > 0.01f) {
                    return 1.0f;
                }
                return baseAlpha;
            case ALWAYS_OPAQUE:
            default:
                // 一直正常
                return 1.0f;
        }
    }

    /**
     * 更新透明度过渡动画，返回当前帧的透明度
     */
    private static float updateAlphaTransition() {
        float targetAlpha = calculateTargetAlpha();
        float speed = HotbarConfig.getAlphaTransitionSpeed() * ALPHA_TRANSITION_SPEED;

        if (currentAlpha < targetAlpha) {
            currentAlpha += speed;
            if (currentAlpha > targetAlpha) {
                currentAlpha = targetAlpha;
            }
        } else if (currentAlpha > targetAlpha) {
            currentAlpha -= speed;
            if (currentAlpha < targetAlpha) {
                currentAlpha = targetAlpha;
            }
        }

        return currentAlpha;
    }
    
    /**
     * 根据配置的布局模式渲染主快捷栏
     */
    private static void renderMainHotbarByLayout(GuiGraphics guiGraphics, Minecraft minecraft, int baseX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress, float alpha) {
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();

        // 设置透明度
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        switch (layout) {
            case ONE_X_ONE:
                // 1x1: 原版样式，只渲染当前快捷栏
                renderMainHotbarWithOffhand(guiGraphics, minecraft, baseX, baseY, hotbarWidth, hotbarHeight, offhandWidth, currentIndex);
                break;
            case ONE_X_TWO:
                // 1x2: 2个快捷栏垂直排列
                renderOneXTwoLayout(guiGraphics, minecraft, baseX, baseY, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
                break;
            case TWO_X_ONE:
                // 2x1: 2个快捷栏水平排列
                renderTwoXOneLayout(guiGraphics, minecraft, baseX, baseY, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
                break;
            case TWO_X_TWO:
                // 2x2: 4个快捷栏网格排列
                renderTwoXTwoLayout(guiGraphics, minecraft, baseX, baseY, hotbarWidth, hotbarHeight, offhandWidth, currentIndex, smoothExpandProgress);
                break;
        }
    }
    
    /**
     * 渲染1x2布局（2个快捷栏垂直排列）
     */
    private static void renderOneXTwoLayout(GuiGraphics guiGraphics, Minecraft minecraft, int baseX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        int maxHotbars = HotbarManager.getHotbarCount();
        int groupSize = HotbarConfig.LayoutMode.ONE_X_TWO.getTotalHotbars(); // 2

        // 计算当前组的起始索引（固定显示当前组），使用补齐逻辑
        int groupStart = (currentIndex / groupSize) * groupSize;
        int hotbar1Idx = getPaddedHotbarIndex(groupStart, maxHotbars);
        int hotbar2Idx = getPaddedHotbarIndex(groupStart + 1, maxHotbars);

        // 根据显示位置调整Y坐标
        int adjustedBaseY = adjustYForPosition(baseY, hotbarHeight * 2, 2);

        // 渲染第一个快捷栏 - 副手在左侧
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, baseX, adjustedBaseY, hotbarWidth, hotbarHeight, offhandWidth, hotbar1Idx, true);

        // 渲染第二个快捷栏 - 副手在右侧
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, baseX, adjustedBaseY + hotbarHeight, hotbarWidth, hotbarHeight, offhandWidth, hotbar2Idx, false);
    }
    
    /**
     * 渲染2x1布局（2个快捷栏水平排列）
     */
    private static void renderTwoXOneLayout(GuiGraphics guiGraphics, Minecraft minecraft, int baseX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        int maxHotbars = HotbarManager.getHotbarCount();
        int groupSize = HotbarConfig.LayoutMode.TWO_X_ONE.getTotalHotbars(); // 2

        // 计算当前组的起始索引（固定显示当前组），使用补齐逻辑
        int groupStart = (currentIndex / groupSize) * groupSize;
        int hotbar1Idx = getPaddedHotbarIndex(groupStart, maxHotbars);
        int hotbar2Idx = getPaddedHotbarIndex(groupStart + 1, maxHotbars);

        // 两个快捷栏水平排列，中间留间隙
        // 布局：副手(左) + 快捷栏1 + gap + 快捷栏2 + 副手(右)
        int gap = 4;
        int totalWidth = offhandWidth + hotbarWidth + gap + hotbarWidth + offhandWidth;
        int startX = baseX - totalWidth / 2;

        // 根据显示位置调整Y坐标
        int adjustedBaseY = adjustYForPosition(baseY, hotbarHeight, 1);

        // 渲染第一个快捷栏（左侧）- 副手在左侧
        // 快捷栏1的中心位置 = startX + 副手宽度 + 快捷栏宽度/2
        int hotbar1CenterX = startX + offhandWidth + hotbarWidth / 2;
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, hotbar1CenterX, adjustedBaseY, hotbarWidth, hotbarHeight, offhandWidth, hotbar1Idx, true);

        // 渲染第二个快捷栏（右侧）- 副手在右侧
        // 快捷栏2的中心位置 = startX + 副手宽度 + 快捷栏宽度 + gap + 快捷栏宽度/2
        int hotbar2CenterX = startX + offhandWidth + hotbarWidth + gap + hotbarWidth / 2;
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, hotbar2CenterX, adjustedBaseY, hotbarWidth, hotbarHeight, offhandWidth, hotbar2Idx, false);
    }
    
    /**
     * 渲染2x2布局（4个快捷栏网格排列）
     */
    private static void renderTwoXTwoLayout(GuiGraphics guiGraphics, Minecraft minecraft, int baseX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress) {
        int hotbarCount = HotbarManager.getHotbarCount();
        int groupSize = HotbarConfig.LayoutMode.TWO_X_TWO.getTotalHotbars(); // 4

        // 计算当前组的起始索引（固定显示当前组），使用补齐逻辑
        int groupStart = (currentIndex / groupSize) * groupSize;
        int[] hotbarIndices = new int[4];
        for (int i = 0; i < 4; i++) {
            hotbarIndices[i] = getPaddedHotbarIndex(groupStart + i, hotbarCount);
        }

        // 网格排列：2列2行
        int gap = 4;
        int totalWidth = hotbarWidth * 2 + gap;
        int startX = baseX - totalWidth / 2;

        // 根据显示位置调整Y坐标
        int adjustedBaseY = adjustYForPosition(baseY, hotbarHeight * 2, 2);

        // 第一行 - 左侧快捷栏副手在左，右侧快捷栏副手在右
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, startX + hotbarWidth / 2, adjustedBaseY, hotbarWidth, hotbarHeight, offhandWidth, hotbarIndices[0], true);
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, startX + hotbarWidth + gap + hotbarWidth / 2, adjustedBaseY, hotbarWidth, hotbarHeight, offhandWidth, hotbarIndices[1], false);

        // 第二行 - 左侧快捷栏副手在左，右侧快捷栏副手在右
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, startX + hotbarWidth / 2, adjustedBaseY + hotbarHeight, hotbarWidth, hotbarHeight, offhandWidth, hotbarIndices[2], true);
        renderHotbarWithOffhandForLayout(guiGraphics, minecraft, startX + hotbarWidth + gap + hotbarWidth / 2, adjustedBaseY + hotbarHeight, hotbarWidth, hotbarHeight, offhandWidth, hotbarIndices[3], false);
    }

    /**
     * 根据显示位置调整Y坐标
     */
    private static int adjustYForPosition(int baseY, int totalHeight, int rows) {
        HotbarConfig.DisplayPosition position = HotbarConfig.getDisplayPosition();
        switch (position) {
            case TOP_CENTER:
            case TOP_LEFT:
            case TOP_RIGHT:
                // 顶部位置：向下偏移
                return baseY + totalHeight - rows * 22;
            case BOTTOM_RIGHT:
            case BOTTOM_LEFT:
            case ABOVE_HOTBAR:
            default:
                // 底部位置：保持原样
                return baseY;
        }
    }
    
    /**
     * 为布局渲染快捷栏（带副手）
     * @param offhandOnLeft true表示副手在左侧，false表示副手在右侧
     */
    private static void renderHotbarWithOffhandForLayout(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int y, int hotbarWidth, int hotbarHeight, int offhandWidth, int hotbarIdx, boolean offhandOnLeft) {
        int itemXOffset = 3;
        int offhandYOffset = 0;
        
        // 渲染副手（左右间距对称，都是2像素）
        if (offhandOnLeft) {
            int offhandX = centerX - hotbarWidth / 2 - offhandWidth - 2;
            renderOffhandForLayout(guiGraphics, minecraft, offhandX, y, offhandWidth, hotbarIdx);
        } else {
            int offhandX = centerX + hotbarWidth / 2 + 10;
            renderOffhandForLayout(guiGraphics, minecraft, offhandX, y, offhandWidth, hotbarIdx);
        }
        
        // 渲染快捷栏背景
        renderHotbarBackground(guiGraphics, centerX - hotbarWidth / 2, y);

        // 渲染选中框（只显示当前选中的快捷栏）
        boolean isCurrent = hotbarIdx == getCurrentHotbarIndex();
        if (isCurrent) {
            int selectedSlot = minecraft.player.getInventory().selected;
            renderHotbarSelection(guiGraphics, centerX - hotbarWidth / 2, y, selectedSlot);
        }

        // 渲染编号（当前快捷栏用黄色，其他用白色）
        // 使用原始索引显示栏编号，确保即使栏位置变化也能正确显示
        int numberColor = isCurrent ? 0xFFFF00 : 0xFFFFFF;
        int displayIndex = HotbarManager.getHotbarOriginalIndex(hotbarIdx);
        String numberStr = String.valueOf(displayIndex);
        int textWidth = minecraft.font.width(numberStr);
        int numberX = offhandOnLeft ? 
            centerX - hotbarWidth / 2 - offhandWidth - 20 + (14 - textWidth) / 2 :
            centerX + hotbarWidth / 2 + offhandWidth + 6 + (14 - textWidth) / 2;
        guiGraphics.drawString(minecraft.font, numberStr, numberX, y + 6, numberColor);
        
        // 渲染物品
        renderItems(guiGraphics, centerX - hotbarWidth / 2, y, hotbarIdx);
    }
    
    /**
     * 为布局渲染选中框
     */
    private static void renderHotbarSelectionForLayout(GuiGraphics guiGraphics, int x, int y, int hotbarIdx, Minecraft minecraft) {
        if (hotbarIdx == getCurrentHotbarIndex()) {
            int selectedSlot = minecraft.player.getInventory().selected;
            renderHotbarSelection(guiGraphics, x, y, selectedSlot);
        }
    }
    
    /**
     * 为布局渲染副手
     */
    private static void renderOffhandForLayout(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y, int offhandWidth, int hotbarIdx) {
        ItemStack offhandItem = HotbarManager.getOffhandItem(hotbarIdx);
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        int itemXOffset = 3;
        int offhandYOffset = 0;
        
        // 使用与原版一致的渲染方式，确保透明度正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        if (isLeftHanded) {
            guiGraphics.blit(HOTBAR_OFFHAND_RIGHT_LOCATION, x, y + offhandYOffset, 0, 0, 29, 24, 29, 24);
        } else {
            guiGraphics.blit(HOTBAR_OFFHAND_LEFT_LOCATION, x, y + offhandYOffset, 0, 0, 29, 24, 29, 24);
        }
        
        if (!offhandItem.isEmpty()) {
            guiGraphics.renderItem(offhandItem, x + itemXOffset, y + offhandYOffset + 4);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, x + itemXOffset, y + offhandYOffset + 4);
        }
    }
    
    /**
     * 在经验条下方渲染小白格指示器
     * 显示当前快捷栏在列表中的位置
     * 宽度与快捷栏宽度一致，所有小白格作为一个整体条，保留格之间的间距
     * 生存创造统一
     */
    private static void renderPositionIndicator(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress, float alpha) {
        // 获取实际快捷栏数量
        int maxHotbars = getMaxHotbarsFromGUI();
        if (maxHotbars <= 1) return; // 只有1个快捷栏时不显示指示器

        // 位置指示器始终使用主快捷栏底部居中位置，不受配置影响
        int centerX = screenWidth / 2;

        // 小白格尺寸和间距
        int barHeight = 3; // 每个小段高度
        int barGap = 1; // 格之间的间距
        // 宽度与快捷栏宽度一致（根据布局调整）
        int barTotalWidth = getLayoutBarWidth(hotbarWidth);

        // 计算每个小白格的宽度（考虑间距后均匀分布）
        int barWidth = (barTotalWidth - (maxHotbars - 1) * barGap) / maxHotbars;
        // 确保至少1像素宽度
        if (barWidth < 1) barWidth = 1;

        // 计算实际总宽度（包含间距）
        int actualWidth = barWidth * maxHotbars + (maxHotbars - 1) * barGap;

        // 计算起始X位置（居中）
        int startX = centerX - actualWidth / 2;

        // 计算Y位置：与主快捷栏保持一致
        // 主快捷栏始终使用 screenHeight - 22 作为基础Y坐标
        int baseY = screenHeight - 22;
        
        // 在1x2和2x2布局下，位置指示器需要向上移动23像素（与主快捷栏保持一致）
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        if (layout == HotbarConfig.LayoutMode.ONE_X_TWO || layout == HotbarConfig.LayoutMode.TWO_X_TWO) {
            baseY -= 23;
        }
        
        int expBarY = baseY - 6; // 经验条在快捷栏上方约6像素
        int barY = expBarY + 3; // 经验条下方0像素

        // 应用透明度
        int alphaInt = (int)(alpha * 255) << 24;

        // 渲染所有小白格（保留间距）
        for (int i = 0; i < maxHotbars; i++) {
            int barX = startX + i * (barWidth + barGap);
            // 当前选中的快捷栏用白色，其他用灰色
            int baseColor = (i == currentIndex) ? 0xFFFFFF : 0x808080;
            int color = alphaInt | baseColor;
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, color);
        }
    }
    
    /**
     * 获取当前布局下的快捷栏宽度（与快捷栏实际宽度一致）
     */
    private static int getLayoutBarWidth(int hotbarWidth) {
        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        switch (layout) {
            case TWO_X_ONE:
                // 2x1: 2个快捷栏水平排列，宽度为 182*2
                return hotbarWidth * 2;
            case TWO_X_TWO:
                // 2x2: 2x2网格，宽度为 182*2
                return hotbarWidth * 2;
            case ONE_X_TWO:
                // 1x2: 2个快捷栏垂直排列，宽度为182
                return hotbarWidth;
            case ONE_X_ONE:
            default:
                // 1x1: 原版宽度
                return hotbarWidth;
        }
    }
    
    // 渲染主快捷栏（带副手格和编号）
    private static void renderMainHotbarWithOffhand(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int baseY, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIdx) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;

        // 渲染副手格
        int offhandYOffset = 0;
        int itemXOffset = 3;

        // 启用混合模式确保透明度与原版一致
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

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
        
        // 渲染编号（黄色）- 使用原始索引显示
        int displayIndex = HotbarManager.getHotbarOriginalIndex(currentIdx);
        int numberX = isLeftHanded ? centerX + hotbarWidth / 2 + 27 : centerX - hotbarWidth / 2 - 37;
        guiGraphics.drawString(minecraft.font, String.valueOf(displayIndex), numberX, baseY + 6, 0xFFFF00);
        
        // 渲染物品
        renderItems(guiGraphics, centerX - hotbarWidth / 2, baseY, currentIdx);
    }
    
    // 在屏幕指定位置渲染快捷栏列表（垂直排列，固定1-9顺序，从上到下）
    // 最多显示配置的数量，超过时通过滚动查看
    private static void renderHotbarListAtPosition(GuiGraphics guiGraphics, Minecraft minecraft, int screenWidth, int screenHeight, int hotbarWidth, int hotbarHeight, int offhandWidth, int currentIndex, float smoothExpandProgress, float alpha) {
        boolean isLeftHanded = minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;

        // 根据显示位置计算列表位置
        HotbarConfig.DisplayPosition displayPosition = HotbarConfig.getDisplayPosition();
        int[] basePosition = calculatePosition(displayPosition, screenWidth, screenHeight, hotbarWidth, hotbarHeight);
        int listCenterX = basePosition[0];
        int baseY = basePosition[1];
        
        // 判断列表在左侧还是右侧
        boolean listOnLeft;
        switch (displayPosition) {
            case TOP_LEFT:
            case BOTTOM_LEFT:
                listOnLeft = true;
                break;
            case TOP_CENTER:
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
            case ABOVE_HOTBAR:
            default:
                listOnLeft = false;
                break;
        }

        // 获取实际快捷栏数量和滚动偏移（主界面使用独立的滚动偏移）
        int maxHotbars = getMaxHotbarsFromGUI();
        int scrollOffset = getMainScrollOffset();
        int maxVisible = HotbarConfig.getMaxVisibleHotbars();
        int visibleHotbars = Math.min(maxVisible, maxHotbars);

        // 判断是顶部位置还是底部位置
        boolean isTopPosition = displayPosition == HotbarConfig.DisplayPosition.TOP_CENTER ||
                               displayPosition == HotbarConfig.DisplayPosition.TOP_LEFT ||
                               displayPosition == HotbarConfig.DisplayPosition.TOP_RIGHT;
        boolean isBottomPosition = displayPosition == HotbarConfig.DisplayPosition.BOTTOM_LEFT ||
                                  displayPosition == HotbarConfig.DisplayPosition.BOTTOM_RIGHT ||
                                  displayPosition == HotbarConfig.DisplayPosition.ABOVE_HOTBAR;

        // 固定1-9顺序，从上到下排列
        // 根据滚动偏移显示不同的9个快捷栏
        for (int displayIdx = 0; displayIdx < visibleHotbars; displayIdx++) {
            int actualIdx = scrollOffset + displayIdx; // 实际的快捷栏索引
            if (actualIdx >= maxHotbars) break;

            // 计算Y位置
            int targetY;
            if (isTopPosition) {
                // 顶部位置：从上到下排列，从baseY开始向下
                targetY = baseY + displayIdx * hotbarHeight;
            } else if (isBottomPosition) {
                // 底部位置：从baseY开始向上排列（baseY是最下面一个快捷栏的位置）
                // displayIdx=0在最上面，displayIdx=visibleHotbars-1在最下面（baseY）
                targetY = baseY - (visibleHotbars - 1 - displayIdx) * hotbarHeight;
            } else {
                // 默认：底部位置
                targetY = baseY - (visibleHotbars - 1 - displayIdx) * hotbarHeight;
            }

            // 动画：从下方滑入（延迟效果：下面的先出现）
            float slotProgress = Math.min(1.0f, Math.max(0.0f, smoothExpandProgress * 1.5f - (visibleHotbars - 1 - displayIdx) * 0.08f));
            int startY;
            if (isTopPosition) {
                startY = -hotbarHeight;
            } else {
                startY = screenHeight + hotbarHeight;
            }
            int renderY = (int) (startY + (targetY - startY) * HotbarManager.getSmoothProgress(slotProgress));

            int offhandYOffset = 0;
            int itemXOffset = 3;

            // 只有当前快捷栏（currentIndex）用黄色高亮
            boolean isCurrentHotbar = (actualIdx == currentIndex);
            boolean isHighlighted = isCurrentHotbar;

            // 渲染副手格（高亮项用黄色）
            // 启用混合模式确保透明度与原版一致
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (isHighlighted) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 0.0f, alpha);
            } else {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            }

            // 根据列表位置决定副手格位置：列表在左侧时副手在右侧，列表在右侧时副手在左侧
            if (listOnLeft) {
                // 列表在左侧：副手在右侧
                if (isLeftHanded) {
                    guiGraphics.blit(HOTBAR_OFFHAND_RIGHT_LOCATION, listCenterX + hotbarWidth / 2 + 4, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
                } else {
                    guiGraphics.blit(HOTBAR_OFFHAND_LEFT_LOCATION, listCenterX + hotbarWidth / 2 + 4, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
                }
            } else {
                // 列表在右侧：副手在左侧
                if (isLeftHanded) {
                    guiGraphics.blit(HOTBAR_OFFHAND_RIGHT_LOCATION, listCenterX - hotbarWidth / 2 - offhandWidth, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
                } else {
                    guiGraphics.blit(HOTBAR_OFFHAND_LEFT_LOCATION, listCenterX - hotbarWidth / 2 - offhandWidth, renderY + offhandYOffset, 0, 0, 29, 24, 29, 24);
                }
            }

            if (isHighlighted) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            }

            // 渲染快捷栏背景（当前快捷栏或选中项用黄色高亮）
            if (isHighlighted) {
                renderHotbarBackgroundWithColor(guiGraphics, listCenterX - hotbarWidth / 2, renderY, 1.0f, 1.0f, 0.0f, alpha);
            } else {
                renderHotbarBackgroundWithAlpha(guiGraphics, listCenterX - hotbarWidth / 2, renderY, alpha);
            }

            // 渲染编号（白色，高亮项用黄色）
            // 使用原始索引显示栏编号，确保即使栏位置变化也能正确显示
            int alphaInt = (int)(alpha * 255) << 24;
            int baseNumberColor = isHighlighted ? 0xFFFF00 : 0xFFFFFF;
            int numberColor = alphaInt | (baseNumberColor & 0xFFFFFF);
            int displayIndex = HotbarManager.getHotbarOriginalIndex(actualIdx);
            String numberStr = String.valueOf(displayIndex);
            int textWidth = minecraft.font.width(numberStr);

            // 根据列表位置决定编号位置：列表在左侧时编号在右侧，列表在右侧时编号在左侧
            int numberX;
            if (listOnLeft) {
                // 列表在左侧：编号在快捷栏右侧（副手右侧）
                numberX = listCenterX + hotbarWidth / 2 + offhandWidth + 6 + (14 - textWidth) / 2;
            } else {
                // 列表在右侧：编号在快捷栏左侧（副手左侧）
                numberX = listCenterX - hotbarWidth / 2 - offhandWidth - 14 + (14 - textWidth) / 2;
            }
            guiGraphics.drawString(minecraft.font, numberStr, numberX, renderY + 6, numberColor);

            // 渲染物品前重新设置透明度（因为drawString可能重置了OpenGL状态）
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

            // 渲染物品
            renderItemsWithAlpha(guiGraphics, listCenterX - hotbarWidth / 2, renderY, actualIdx, alpha);

            // 渲染副手物品（根据列表位置调整）
            if (listOnLeft) {
                // 列表在左侧：副手在右侧
                int offhandX = listCenterX + hotbarWidth / 2 + 4;
                ItemStack offhandItem = HotbarManager.getOffhandItem(actualIdx);
                if (!offhandItem.isEmpty()) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 100);
                    // 应用透明度
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                    guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
                    guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
                    guiGraphics.pose().popPose();
                }
            } else {
                // 列表在右侧：副手在左侧，使用原方法
                renderOffhandItemWithAlpha(guiGraphics, minecraft, listCenterX, renderY, hotbarWidth, offhandWidth, isLeftHanded, itemXOffset, offhandYOffset, actualIdx, alpha);
            }

            // 如果是当前快捷栏，渲染选中框（显示当前选中的物品槽位）
            if (isCurrentHotbar) {
                int selectedSlot = minecraft.player.getInventory().selected;
                renderHotbarSelectionWithAlpha(guiGraphics, listCenterX - hotbarWidth / 2, renderY, selectedSlot, alpha);
            }
        }

        // 循环结束后重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 如果快捷栏数量超过可见数量，渲染滚动指示器（小白条）
        if (maxHotbars > visibleHotbars) {
            renderScrollIndicatorAtPosition(guiGraphics, screenWidth, screenHeight, hotbarHeight, scrollOffset, maxHotbars, visibleHotbars, smoothExpandProgress, alpha, displayPosition);
        }
    }
    
    /**
     * 渲染滚动指示器（小白条）
     */
    private static void renderScrollIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int hotbarHeight, int scrollOffset, int maxHotbars, int visibleHotbars, float smoothExpandProgress) {
        // 隐藏小白条（滚动指示器）- 备用代码，如需恢复请取消注释
    }

    /**
     * 根据位置渲染滚动指示器（小白条）
     */
    private static void renderScrollIndicatorAtPosition(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int hotbarHeight, int scrollOffset, int maxHotbars, int visibleHotbars, float smoothExpandProgress, float alpha, HotbarConfig.DisplayPosition displayPosition) {
        // 当前隐藏滚动指示器，如需恢复可取消注释以下代码
        /*
        int listCenterX;
        switch (displayPosition) {
            case BOTTOM_LEFT:
            case TOP_LEFT:
                listCenterX = 182 / 2 + 20;
                break;
            case BOTTOM_RIGHT:
            case TOP_RIGHT:
                listCenterX = screenWidth - 182 / 2 - 20;
                break;
            case TOP_CENTER:
            case ABOVE_HOTBAR:
            default:
                listCenterX = screenWidth - 182 / 2 - 20;
                break;
        }

        int indicatorX = listCenterX - 182 / 2 - 8;
        int listHeight = visibleHotbars * hotbarHeight;
        int startY;
        if (displayPosition == HotbarConfig.DisplayPosition.TOP_CENTER ||
            displayPosition == HotbarConfig.DisplayPosition.TOP_LEFT ||
            displayPosition == HotbarConfig.DisplayPosition.TOP_RIGHT) {
            startY = hotbarHeight * 2 + hotbarHeight;
        } else {
            startY = screenHeight - listHeight;
        }

        // 计算指示器高度和位置
        float ratio = (float) visibleHotbars / maxHotbars;
        int indicatorHeight = Math.max(8, (int)(listHeight * ratio));
        int maxScroll = maxHotbars - visibleHotbars;
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int indicatorY = startY + (int)((listHeight - indicatorHeight) * scrollRatio);

        int alphaInt = (int)(alpha * 255) << 24;

        // 渲染指示器背景（灰色）
        guiGraphics.fill(indicatorX, startY, indicatorX + 3, startY + listHeight, alphaInt | 0x808080);
        // 渲染指示器滑块（白色）
        guiGraphics.fill(indicatorX, indicatorY, indicatorX + 3, indicatorY + indicatorHeight, alphaInt | 0xFFFFFF);
        */
    }
    
    /**
     * 获取主界面滚动偏移
     * 主界面使用独立的滚动偏移，基于配置的最大显示数量
     */
    private static int getMainScrollOffset() {
        return mainScrollOffset;
    }

    /**
     * 设置主界面滚动偏移
     */
    private static void setMainScrollOffset(int offset) {
        int maxHotbars = HotbarManager.getHotbarCount();
        int visibleHotbars = HotbarConfig.getMaxVisibleHotbars();
        int maxScroll = Math.max(0, maxHotbars - visibleHotbars);
        mainScrollOffset = Math.min(maxScroll, Math.max(0, offset));
    }

    /**
     * 重置主界面滚动偏移
     */
    public static void resetMainScrollOffset() {
        mainScrollOffset = 0;
        System.out.println("[HotbarExpand] Main scroll offset reset");
    }

    /**
     * 从HotbarInventoryScreen获取滚动偏移（背包界面使用）
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
     * 获取最大快捷栏数量
     * 优先从HotbarManager获取（包含生存模式判断）
     */
    private static int getMaxHotbarsFromGUI() {
        // 直接从HotbarManager获取，它会处理生存模式的逻辑
        return HotbarManager.getHotbarCount();
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

    // 渲染副手物品（带透明度）
    private static void renderOffhandItemWithAlpha(GuiGraphics guiGraphics, Minecraft minecraft, int listCenterX, int renderY, int hotbarWidth, int offhandWidth, boolean isLeftHanded, int itemXOffset, int offhandYOffset, int hotbarIndex, float alpha) {
        ItemStack offhandItem = HotbarManager.getOffhandItem(hotbarIndex);
        if (offhandItem.isEmpty()) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);

        // 应用透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        if (isLeftHanded) {
            int offhandX = listCenterX + hotbarWidth / 2 + 4;
            guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
        } else {
            int offhandX = listCenterX - hotbarWidth / 2 - offhandWidth;
            guiGraphics.renderItem(offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, offhandX + itemXOffset, renderY + offhandYOffset + 4);
        }

        guiGraphics.pose().popPose();
    }

    private static void renderHotbarBackground(GuiGraphics guiGraphics, int x, int y) {
        // 使用与原版一致的渲染方式，确保透明度正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
    }

    private static void renderHotbarBackgroundWithAlpha(GuiGraphics guiGraphics, int x, int y, float alpha) {
        // 使用与原版一致的渲染方式，确保透明度正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
    }

    private static void renderHotbarBackgroundWithColor(GuiGraphics guiGraphics, int x, int y, float r, float g, float b) {
        // 使用与原版一致的渲染方式，确保透明度正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
    }

    private static void renderHotbarBackgroundWithColor(GuiGraphics guiGraphics, int x, int y, float r, float g, float b, float alpha) {
        // 使用与原版一致的渲染方式，确保透明度正确
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, alpha);
        guiGraphics.blit(HOTBAR_LOCATION, x, y, 0, 0, 182, 22, 182, 22);
    }

    private static void renderHotbarSelection(GuiGraphics guiGraphics, int x, int y, int selectedSlot) {
        int selectX = x - 1 + selectedSlot * 20;
        int selectY = y - 1;
        guiGraphics.blit(HOTBAR_SELECTION_LOCATION, selectX, selectY, 0, 0, 24, 24, 24, 24);
    }

    private static void renderHotbarSelectionWithAlpha(GuiGraphics guiGraphics, int x, int y, int selectedSlot, float alpha) {
        int selectX = x - 1 + selectedSlot * 20;
        int selectY = y - 1;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
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

    private static void renderItemsWithAlpha(GuiGraphics guiGraphics, int x, int y, int hotbarIndex, float alpha) {
        List<ItemStack> hotbar = HotbarManager.getHotbar(hotbarIndex);
        Minecraft minecraft = Minecraft.getInstance();

        for (int i = 0; i < 9; i++) {
            int slotX = x + 3 + i * 20;
            int slotY = y + 3;
            ItemStack itemStack = hotbar.get(i);
            if (!itemStack.isEmpty()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 100);
                
                // 使用RenderSystem设置透明度
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                
                guiGraphics.renderItem(itemStack, slotX, slotY);
                
                // 渲染装饰前再次设置透明度（因为renderItem可能重置状态）
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                guiGraphics.renderItemDecorations(minecraft.font, itemStack, slotX, slotY);
                
                guiGraphics.pose().popPose();
            }
        }
    }
}
