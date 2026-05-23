package com.example.hotbarexpand.client;

import com.example.hotbarexpand.client.gui.HotbarInventoryScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

public class HotbarManager {
    // 使用ArrayList支持动态扩展，初始9个快捷栏，每个包含9个物品槽
    private static final List<List<ItemStack>> hotbars = new ArrayList<>();
    // 副手物品槽列表
    private static final List<ItemStack> offhandSlots = new ArrayList<>();
    
    // 当前激活的快捷栏索引
    private static int currentHotbarIndex = 0;
    
    // 展开状态
    private static boolean isExpanded = false;
    
    // 切换冷却，防止同步干扰
    private static int switchCooldown = 0;
    
    static {
        // 初始化9个默认快捷栏
        for (int i = 0; i < 9; i++) {
            List<ItemStack> hotbar = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                hotbar.add(ItemStack.EMPTY);
            }
            hotbars.add(hotbar);
            offhandSlots.add(ItemStack.EMPTY);
        }
    }
    
    /**
     * 确保指定索引的快捷栏存在
     */
    private static void ensureHotbarExists(int index) {
        while (hotbars.size() <= index) {
            List<ItemStack> newHotbar = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                newHotbar.add(ItemStack.EMPTY);
            }
            hotbars.add(newHotbar);
            offhandSlots.add(ItemStack.EMPTY);
        }
    }
    
    public static List<ItemStack> getHotbar(int index) {
        if (index >= 0) {
            ensureHotbarExists(index);
            return hotbars.get(index);
        }
        return hotbars.get(0);
    }
    
    public static ItemStack getOffhandItem(int index) {
        if (index >= 0) {
            ensureHotbarExists(index);
            return offhandSlots.get(index);
        }
        return ItemStack.EMPTY;
    }
    
    public static void setOffhandItem(int index, ItemStack stack) {
        if (index >= 0) {
            ensureHotbarExists(index);
            offhandSlots.set(index, stack.copy());
            
            // 如果设置的是当前快捷栏，同时更新玩家副手
            if (index == currentHotbarIndex) {
                Minecraft minecraft = Minecraft.getInstance();
                Player player = minecraft.player;
                if (player != null) {
                    player.getInventory().offhand.set(0, stack.copy());
                }
            }
        }
    }
    
    /**
     * 获取快捷栏数量
     */
    public static int getHotbarCount() {
        return hotbars.size();
    }
    
    /**
     * 添加新的快捷栏
     */
    public static void addHotbar() {
        List<ItemStack> newHotbar = new ArrayList<>();
        for (int j = 0; j < 9; j++) {
            newHotbar.add(ItemStack.EMPTY);
        }
        hotbars.add(newHotbar);
        offhandSlots.add(ItemStack.EMPTY);
    }
    
    /**
     * 删除指定索引的快捷栏
     */
    public static void removeHotbar(int index) {
        if (index >= 0 && index < hotbars.size() && hotbars.size() > 1) {
            // 如果要删除的是当前正在使用的快捷栏，先保存玩家背包数据
            if (index == currentHotbarIndex) {
                savePlayerInventoryToHotbar(index);
            }
            
            // 从列表中移除
            hotbars.remove(index);
            offhandSlots.remove(index);
            
            // 调整当前索引
            if (currentHotbarIndex >= hotbars.size()) {
                currentHotbarIndex = hotbars.size() - 1;
            } else if (index < currentHotbarIndex) {
                // 如果删除的索引在当前索引之前，当前索引需要减1
                currentHotbarIndex--;
            }
            
            // 如果删除后还有快捷栏，且当前索引有效，加载该快捷栏到玩家背包
            if (hotbars.size() > 0 && currentHotbarIndex >= 0 && currentHotbarIndex < hotbars.size()) {
                loadHotbarToPlayer(currentHotbarIndex);
            }
        }
    }
    
    /**
     * 交换两个快捷栏的数据
     */
    public static void swapHotbars(int index1, int index2) {
        if (index1 < 0 || index1 >= hotbars.size() || index2 < 0 || index2 >= hotbars.size()) {
            return;
        }
        
        // 交换快捷栏物品
        List<ItemStack> tempHotbar = new ArrayList<>(hotbars.get(index1));
        hotbars.set(index1, hotbars.get(index2));
        hotbars.set(index2, tempHotbar);
        
        // 交换副手物品
        ItemStack tempOffhand = offhandSlots.get(index1).copy();
        offhandSlots.set(index1, offhandSlots.get(index2).copy());
        offhandSlots.set(index2, tempOffhand);
        
        // 如果交换的快捷栏中有当前正在使用的，需要同步到玩家背包
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player != null) {
            if (index1 == currentHotbarIndex || index2 == currentHotbarIndex) {
                loadHotbarToPlayer(currentHotbarIndex);
            }
        }
    }
    
    /**
     * 清空所有快捷栏数据（用于重新加载）
     */
    public static void clearAllHotbars() {
        hotbars.clear();
        offhandSlots.clear();
        currentHotbarIndex = 0;
    }
    
    public static int getCurrentHotbarIndex() {
        return currentHotbarIndex;
    }
    
    public static void setCurrentHotbarIndex(int index) {
        // 获取最大快捷栏数量（从GUI获取，默认9）
        int maxHotbars = getMaxHotbars();
        
        if (index >= 0 && index < maxHotbars && index != currentHotbarIndex) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;
            
            // 设置切换冷却，防止同步干扰（必须在保存前设置）
            switchCooldown = 20;
            
            // 先保存当前快捷栏
            savePlayerInventoryToHotbar(currentHotbarIndex);
            
            // 更新索引
            currentHotbarIndex = index;
            
            // 加载新快捷栏到玩家背包（所有快捷栏都加载到玩家背包）
            loadHotbarToPlayer(index);
            
            // 调试输出
            System.out.println("[HotbarExpand] Switched to hotbar " + (index + 1));
            // 确保库存面板中的滚动偏移使选中的快捷栏可见
            try {
                int visible = HotbarInventoryScreen.getVisibleHotbars();
                int currentOffset = HotbarInventoryScreen.getScrollOffset();
                if (index < currentOffset) {
                    HotbarInventoryScreen.setScrollOffset(index);
                } else if (index >= currentOffset + visible) {
                    HotbarInventoryScreen.setScrollOffset(index - visible + 1);
                }
            } catch (Exception e) {
                // 忽略 GUI 不可用的情况
            }
        }
    }

    public static boolean selectHotbarSlot(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return false;
        if (hotbarIdx < 0 || hotbarIdx >= getHotbarCount() || slotIdx < 0 || slotIdx >= 9) return false;
        if (hotbarIdx != currentHotbarIndex) {
            setCurrentHotbarIndex(hotbarIdx);
        }
        player.getInventory().selected = slotIdx;
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        return true;
    }

    public static boolean findAndSelectItem(ItemStack targetItem) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || targetItem.isEmpty()) return false;

        int currentHotbar = getCurrentHotbarIndex();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isSameItem(item, targetItem)) {
                return selectHotbarSlot(currentHotbar, slot);
            }
        }

        int hotbarCount = getHotbarCount();
        for (int hotbarIdx = 0; hotbarIdx < hotbarCount; hotbarIdx++) {
            if (hotbarIdx == currentHotbar) continue;
            List<ItemStack> hotbarItems = getHotbar(hotbarIdx);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = hotbarItems.get(slot);
                if (isSameItem(item, targetItem)) {
                    return selectHotbarSlot(hotbarIdx, slot);
                }
            }
        }

        return false;
    }

    private static boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1.isEmpty() || item2.isEmpty()) return false;
        var item1Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item1.getItem());
        var item2Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item2.getItem());
        return item1Key.getNamespace().equals(item2Key.getNamespace()) && item1Key.getPath().equals(item2Key.getPath());
    }
    
    /**
     * 获取最大快捷栏数量
     */
    private static int getMaxHotbars() {
        // 从HotbarInventoryScreen获取最大快捷栏数量
        try {
            Class<?> guiClass = Class.forName("com.example.hotbarexpand.client.gui.HotbarInventoryScreen");
            java.lang.reflect.Field field = guiClass.getDeclaredField("maxHotbars");
            field.setAccessible(true);
            return (int) field.get(null);
        } catch (Exception e) {
            return 9; // 默认9个
        }
    }
    
    public static void scrollToNext() {
        int maxHotbars = getMaxHotbars();
        int nextIndex = (currentHotbarIndex + 1) % maxHotbars;
        setCurrentHotbarIndex(nextIndex);
    }
    
    public static void scrollToPrevious() {
        int maxHotbars = getMaxHotbars();
        int prevIndex = (currentHotbarIndex - 1 + maxHotbars) % maxHotbars;
        setCurrentHotbarIndex(prevIndex);
    }
    
    private static void savePlayerInventoryToHotbar(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        
        // 确保索引有效
        if (index < 0) return;
        
        // 确保快捷栏存在
        ensureHotbarExists(index);
        
        List<ItemStack> hotbar = hotbars.get(index);
        System.out.println("[HotbarExpand] Saving player inventory to hotbar " + (index + 1));
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            System.out.println("[HotbarExpand] Slot " + i + ": " + item.getItem().getDescription().getString() + " x" + item.getCount());
            hotbar.set(i, item.copy());
        }
        // 保存副手物品
        offhandSlots.set(index, player.getOffhandItem().copy());
    }
    
    private static void loadHotbarToPlayer(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        
        // 确保索引有效
        if (index < 0) return;
        
        // 确保快捷栏存在
        ensureHotbarExists(index);
        
        List<ItemStack> hotbar = hotbars.get(index);
        System.out.println("[HotbarExpand] Loading hotbar " + (index + 1) + " to player");
        for (int i = 0; i < 9; i++) {
            ItemStack item = hotbar.get(i);
            System.out.println("[HotbarExpand] Slot " + i + ": " + item.getItem().getDescription().getString() + " x" + item.getCount());
            player.getInventory().setItem(i, item.copy());
        }
        // 加载副手物品
        player.getInventory().offhand.set(0, offhandSlots.get(index).copy());
        System.out.println("[HotbarExpand] Hotbar loaded, switchCooldown = " + switchCooldown);
    }
    
    public static void savePlayerInventoryToCurrentHotbar() {
        savePlayerInventoryToHotbar(currentHotbarIndex);
    }
    
    public static boolean isExpanded() {
        return isExpanded;
    }
    
    public static void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }
    
    public static float getSmoothProgress(float progress) {
        if (progress >= 1.0f) return 1.0f;
        if (progress <= 0.0f) return 0.0f;
        return 1.0f - (float) Math.pow(1.0f - progress, 3);
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        
        // 减少切换冷却
        if (switchCooldown > 0) {
            switchCooldown--;
            return; // 冷却期间不进行同步
        }
        
        // 每5 tick同步一次
        if (minecraft.level != null && minecraft.level.getGameTime() % 5 == 0) {
            // 确保当前索引的快捷栏存在
            ensureHotbarExists(currentHotbarIndex);
            
            // 同步当前快捷栏（所有索引都需要同步）
            List<ItemStack> currentHotbar = hotbars.get(currentHotbarIndex);
            for (int i = 0; i < 9; i++) {
                ItemStack playerItem = player.getInventory().getItem(i);
                if (!ItemStack.matches(currentHotbar.get(i), playerItem)) {
                    currentHotbar.set(i, playerItem.copy());
                }
            }
            // 同步副手物品
            ItemStack playerOffhand = player.getOffhandItem();
            if (!ItemStack.matches(offhandSlots.get(currentHotbarIndex), playerOffhand)) {
                offhandSlots.set(currentHotbarIndex, playerOffhand.copy());
            }
        }
    }
}
