package com.example.hotbarexpand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class HotbarManager {
    private static final int HOTBAR_COUNT = 9;
    private static final int SLOTS_PER_HOTBAR = 9;
    private static final int AUTO_SAVE_INTERVAL = 200;

    // 9个快捷栏，每个包含9个物品槽
    private static final List<ItemStack>[] hotbars = new ArrayList[HOTBAR_COUNT];
    // 9个副手物品槽
    private static final ItemStack[] offhandSlots = new ItemStack[HOTBAR_COUNT];
    
    // 当前激活的快捷栏索引（0-8）
    private static int currentHotbarIndex = 0;
    
    // 展开状态
    private static boolean isExpanded = false;
    
    // 切换冷却，防止同步干扰
    private static int switchCooldown = 0;
    private static int autoSaveCooldown = AUTO_SAVE_INTERVAL;
    private static boolean dirty = false;
    
    static {
        for (int i = 0; i < HOTBAR_COUNT; i++) {
            hotbars[i] = new ArrayList<>();
            for (int j = 0; j < SLOTS_PER_HOTBAR; j++) {
                hotbars[i].add(ItemStack.EMPTY);
            }
            offhandSlots[i] = ItemStack.EMPTY;
        }
    }
    
    public static List<ItemStack> getHotbar(int index) {
        if (index >= 0 && index < HOTBAR_COUNT) {
            return hotbars[index];
        }
        return hotbars[0];
    }
    
    public static ItemStack getOffhandItem(int index) {
        if (index >= 0 && index < HOTBAR_COUNT) {
            return offhandSlots[index];
        }
        return ItemStack.EMPTY;
    }
    
    public static void setOffhandItem(int index, ItemStack stack) {
        if (index >= 0 && index < HOTBAR_COUNT) {
            offhandSlots[index] = stack.copy();
            markDirty();
            
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
    
    public static int getCurrentHotbarIndex() {
        return currentHotbarIndex;
    }
    
    public static void setCurrentHotbarIndex(int index) {
        if (index >= 0 && index < HOTBAR_COUNT && index != currentHotbarIndex) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) return;
            
            // 设置切换冷却，防止同步干扰（必须在保存前设置）
            switchCooldown = 20;
            
            // 先保存当前快捷栏
            savePlayerInventoryToHotbar(currentHotbarIndex);
            
            // 更新索引
            currentHotbarIndex = index;
            
            // 加载新快捷栏到玩家背包
            loadHotbarToPlayer(index);
            markDirty();
            
            // 调试输出
            System.out.println("[HotbarExpand] Switched to hotbar " + (index + 1));
        }
    }
    
    public static void scrollToNext() {
        int nextIndex = (currentHotbarIndex + 1) % HOTBAR_COUNT;
        setCurrentHotbarIndex(nextIndex);
    }
    
    public static void scrollToPrevious() {
        int prevIndex = (currentHotbarIndex - 1 + HOTBAR_COUNT) % HOTBAR_COUNT;
        setCurrentHotbarIndex(prevIndex);
    }
    
    private static void savePlayerInventoryToHotbar(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        
        List<ItemStack> hotbar = hotbars[index];
        System.out.println("[HotbarExpand] Saving player inventory to hotbar " + (index + 1));
        for (int i = 0; i < SLOTS_PER_HOTBAR; i++) {
            ItemStack item = player.getInventory().getItem(i);
            System.out.println("[HotbarExpand] Slot " + i + ": " + item.getItem().getDescription().getString() + " x" + item.getCount());
            hotbar.set(i, item.copy());
        }
        // 保存副手物品
        offhandSlots[index] = player.getOffhandItem().copy();
        markDirty();
    }
    
    private static void loadHotbarToPlayer(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        
        List<ItemStack> hotbar = hotbars[index];
        System.out.println("[HotbarExpand] Loading hotbar " + (index + 1) + " to player");
        for (int i = 0; i < SLOTS_PER_HOTBAR; i++) {
            ItemStack item = hotbar.get(i);
            System.out.println("[HotbarExpand] Slot " + i + ": " + item.getItem().getDescription().getString() + " x" + item.getCount());
            player.getInventory().setItem(i, item.copy());
        }
        // 加载副手物品
        player.getInventory().offhand.set(0, offhandSlots[index].copy());
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

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        loadStateFromDisk();
        savePlayerInventoryToCurrentHotbar();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        savePlayerInventoryToCurrentHotbar();
        saveStateToDisk();
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

        if (dirty) {
            autoSaveCooldown--;
            if (autoSaveCooldown <= 0) {
                saveStateToDisk();
            }
        }
        
        // 减少切换冷却
        if (switchCooldown > 0) {
            switchCooldown--;
            return; // 冷却期间不进行同步
        }
        
        // 每5 tick同步一次
        if (minecraft.level != null && minecraft.level.getGameTime() % 5 == 0) {
            // 同步当前快捷栏
            List<ItemStack> currentHotbar = hotbars[currentHotbarIndex];
            for (int i = 0; i < SLOTS_PER_HOTBAR; i++) {
                ItemStack playerItem = player.getInventory().getItem(i);
                if (!ItemStack.matches(currentHotbar.get(i), playerItem)) {
                    currentHotbar.set(i, playerItem.copy());
                    markDirty();
                }
            }
            // 同步副手物品
            ItemStack playerOffhand = player.getOffhandItem();
            if (!ItemStack.matches(offhandSlots[currentHotbarIndex], playerOffhand)) {
                offhandSlots[currentHotbarIndex] = playerOffhand.copy();
                markDirty();
            }
            
            // 在展开状态下，同步所有非当前快捷栏的物品（用于显示更新）
            if (isExpanded) {
                for (int h = 0; h < 9; h++) {
                    if (h == currentHotbarIndex) continue; // 跳过当前快捷栏
                    
                    // 这里我们只是读取数据，不需要实际同步到玩家背包
                    // 因为非当前快捷栏的物品存储在 hotbars 数组中
                    // 当玩家打开背包GUI时，可以通过GUI修改这些物品
                }
            }
        }
    }

    private static void markDirty() {
        dirty = true;
        autoSaveCooldown = AUTO_SAVE_INTERVAL;
    }

    private static Path getStatePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("hotbarexpand-state.dat");
    }

    private static void loadStateFromDisk() {
        Path statePath = getStatePath();
        if (!Files.exists(statePath)) {
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(statePath, NbtAccounter.unlimitedHeap());
            if (root == null) {
                return;
            }

            currentHotbarIndex = Math.max(0, Math.min(HOTBAR_COUNT - 1, root.getInt("currentHotbarIndex")));
            for (int i = 0; i < HOTBAR_COUNT; i++) {
                CompoundTag hotbarTag = root.getCompound("hotbar_" + i);
                NonNullList<ItemStack> loadedHotbar = NonNullList.withSize(SLOTS_PER_HOTBAR, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(hotbarTag, loadedHotbar, Minecraft.getInstance().player.registryAccess());

                for (int slot = 0; slot < SLOTS_PER_HOTBAR; slot++) {
                    hotbars[i].set(slot, loadedHotbar.get(slot).copy());
                }

                CompoundTag offhandTag = root.getCompound("offhand_" + i);
                NonNullList<ItemStack> loadedOffhand = NonNullList.withSize(1, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(offhandTag, loadedOffhand, Minecraft.getInstance().player.registryAccess());
                offhandSlots[i] = loadedOffhand.getFirst().copy();
            }

            dirty = false;
            autoSaveCooldown = AUTO_SAVE_INTERVAL;
            System.out.println("[HotbarExpand] Loaded hotbar state from " + statePath);
        } catch (IOException exception) {
            System.out.println("[HotbarExpand] Failed to load hotbar state: " + exception.getMessage());
        }
    }

    private static void saveStateToDisk() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Path statePath = getStatePath();
        try {
            Files.createDirectories(statePath.getParent());

            CompoundTag root = new CompoundTag();
            root.putInt("currentHotbarIndex", currentHotbarIndex);

            for (int i = 0; i < HOTBAR_COUNT; i++) {
                NonNullList<ItemStack> hotbarToSave = NonNullList.withSize(SLOTS_PER_HOTBAR, ItemStack.EMPTY);
                for (int slot = 0; slot < SLOTS_PER_HOTBAR; slot++) {
                    hotbarToSave.set(slot, hotbars[i].get(slot).copy());
                }
                root.put("hotbar_" + i, ContainerHelper.saveAllItems(new CompoundTag(), hotbarToSave, minecraft.player.registryAccess()));

                NonNullList<ItemStack> offhandToSave = NonNullList.withSize(1, ItemStack.EMPTY);
                offhandToSave.set(0, offhandSlots[i].copy());
                root.put("offhand_" + i, ContainerHelper.saveAllItems(new CompoundTag(), offhandToSave, minecraft.player.registryAccess()));
            }

            NbtIo.writeCompressed(root, statePath);
            dirty = false;
            autoSaveCooldown = AUTO_SAVE_INTERVAL;
            System.out.println("[HotbarExpand] Saved hotbar state to " + statePath);
        } catch (IOException exception) {
            System.out.println("[HotbarExpand] Failed to save hotbar state: " + exception.getMessage());
        }
    }
}
