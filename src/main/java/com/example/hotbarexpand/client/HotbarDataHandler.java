package com.example.hotbarexpand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

/**
 * 快捷栏数据处理器 - 负责保存和加载快捷栏数据到本地文件
 */
public class HotbarDataHandler {
    
    private static final String DATA_FILE_NAME = "hotbarexpand_data.dat";
    private static String lastServerAddress = null;
    
    /**
     * 当客户端玩家加入服务器时加载数据
     */
    @SubscribeEvent
    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        System.out.println("[HotbarExpand] Player logging in, loading hotbar data...");
        // 记录当前服务器地址，用于退出时保存
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null) {
            lastServerAddress = minecraft.getCurrentServer().ip.replace(":", "_");
        } else if (minecraft.hasSingleplayerServer()) {
            lastServerAddress = "singleplayer_" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        loadHotbarData();
    }
    
    /**
     * 当客户端玩家离开服务器时保存数据
     */
    @SubscribeEvent
    public static void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        System.out.println("[HotbarExpand] Player logging out, saving hotbar data...");
        saveHotbarData();
        lastServerAddress = null;
    }
    
    /**
     * 保存快捷栏数据到文件
     */
    public static void saveHotbarData() {
        try {
            File saveDir = getSaveDirectory();
            if (saveDir == null) {
                System.out.println("[HotbarExpand] Cannot get save directory");
                return;
            }
            
            File dataFile = new File(saveDir, DATA_FILE_NAME);
            CompoundTag rootTag = new CompoundTag();
            
            // 保存当前选中的快捷栏索引
            rootTag.putInt("CurrentHotbarIndex", HotbarManager.getCurrentHotbarIndex());
            
            // 保存所有快捷栏数据
            ListTag hotbarsList = new ListTag();
            int hotbarCount = HotbarManager.getHotbarCount();
            
            for (int i = 0; i < hotbarCount; i++) {
                CompoundTag hotbarTag = new CompoundTag();
                hotbarTag.putInt("Index", i);
                
                // 保存9个槽位的物品
                ListTag itemsList = new ListTag();
                List<ItemStack> hotbar = HotbarManager.getHotbar(i);
                int itemCount = 0;
                for (int j = 0; j < 9; j++) {
                    ItemStack item = hotbar.get(j);
                    if (!item.isEmpty()) {
                        CompoundTag itemTag = new CompoundTag();
                        itemTag.putInt("Slot", j);
                        // 使用 save 方法，传入 Provider（可以为 null）
                        CompoundTag savedItem = (CompoundTag) item.save(null);
                        if (savedItem != null) {
                            itemTag.put("Item", savedItem);
                            itemsList.add(itemTag);
                            itemCount++;
                        }
                    }
                }
                hotbarTag.put("Items", itemsList);
                
                // 保存副手物品
                ItemStack offhandItem = HotbarManager.getOffhandItem(i);
                if (!offhandItem.isEmpty()) {
                    CompoundTag savedOffhand = (CompoundTag) offhandItem.save(null);
                    if (savedOffhand != null) {
                        hotbarTag.put("Offhand", savedOffhand);
                    }
                }
                
                hotbarsList.add(hotbarTag);
                System.out.println("[HotbarExpand] Saving hotbar " + (i + 1) + " with " + itemCount + " items");
            }
            
            rootTag.put("Hotbars", hotbarsList);
            rootTag.putInt("HotbarCount", hotbarCount);
            
            // 删除旧的保存文件（如果存在），确保数据完全重新写入
            if (dataFile.exists()) {
                dataFile.delete();
            }
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                net.minecraft.nbt.NbtIo.writeCompressed(rootTag, fos);
            }
            
            System.out.println("[HotbarExpand] Saved " + hotbarCount + " hotbars to " + dataFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("[HotbarExpand] Error saving hotbar data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从文件加载快捷栏数据
     */
    public static void loadHotbarData() {
        try {
            File saveDir = getSaveDirectory();
            if (saveDir == null) {
                System.out.println("[HotbarExpand] Cannot get save directory");
                return;
            }
            
            File dataFile = new File(saveDir, DATA_FILE_NAME);
            if (!dataFile.exists()) {
                System.out.println("[HotbarExpand] No saved data found");
                return;
            }
            
            CompoundTag rootTag;
            try (FileInputStream fis = new FileInputStream(dataFile)) {
                rootTag = net.minecraft.nbt.NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());
            }
            
            // 加载当前选中的快捷栏索引
            int currentIndex = rootTag.getInt("CurrentHotbarIndex");
            
            // 加载保存的快捷栏数量
            int savedHotbarCount = rootTag.getInt("HotbarCount");
            
            // 加载所有快捷栏数据
            ListTag hotbarsList = rootTag.getList("Hotbars", Tag.TAG_COMPOUND);
            int loadedCount = 0;
            
            // 先清空现有数据，确保从干净状态开始
            HotbarManager.clearAllHotbars();
            
            // 确保加载正确数量的快捷栏
            for (int i = 0; i < savedHotbarCount; i++) {
                HotbarManager.getHotbar(i); // 这会创建空快捷栏
            }
            
            for (int i = 0; i < hotbarsList.size(); i++) {
                CompoundTag hotbarTag = hotbarsList.getCompound(i);
                int hotbarIndex = hotbarTag.getInt("Index");
                
                // 确保快捷栏存在
                List<ItemStack> hotbar = HotbarManager.getHotbar(hotbarIndex);
                
                // 加载9个槽位的物品
                ListTag itemsList = hotbarTag.getList("Items", Tag.TAG_COMPOUND);
                for (int j = 0; j < itemsList.size(); j++) {
                    CompoundTag itemTag = itemsList.getCompound(j);
                    int slot = itemTag.getInt("Slot");
                    if (itemTag.contains("Item")) {
                        CompoundTag itemData = itemTag.getCompound("Item");
                        // 使用 parse 方法加载物品
                        ItemStack item = ItemStack.parse(null, itemData).orElse(ItemStack.EMPTY);
                        if (slot >= 0 && slot < 9) {
                            hotbar.set(slot, item);
                        }
                    }
                }
                
                // 加载副手物品
                if (hotbarTag.contains("Offhand")) {
                    CompoundTag offhandTag = hotbarTag.getCompound("Offhand");
                    ItemStack offhandItem = ItemStack.parse(null, offhandTag).orElse(ItemStack.EMPTY);
                    HotbarManager.setOffhandItem(hotbarIndex, offhandItem);
                }
                
                loadedCount++;
            }
            
            // 设置当前选中的快捷栏
            HotbarManager.setCurrentHotbarIndex(currentIndex);
            
            System.out.println("[HotbarExpand] Loaded " + loadedCount + " hotbars (expected " + savedHotbarCount + ") from " + dataFile.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("[HotbarExpand] Error loading hotbar data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取保存目录
     */
    private static File getSaveDirectory() {
        try {
            // 获取Minecraft实例
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return null;
            
            // 获取当前世界的服务器数据目录
            File gameDir = minecraft.gameDirectory;
            File savesDir = new File(gameDir, "saves");
            
            // 优先使用记录的lastServerAddress（用于退出时保存）
            if (lastServerAddress != null) {
                if (lastServerAddress.startsWith("singleplayer_")) {
                    String worldName = lastServerAddress.substring("singleplayer_".length());
                    File worldDir = new File(savesDir, worldName);
                    File dataDir = new File(worldDir, "hotbarexpand");
                    if (!dataDir.exists()) {
                        dataDir.mkdirs();
                    }
                    return dataDir;
                } else {
                    File serverDir = new File(new File(gameDir, "hotbarexpand"), lastServerAddress);
                    if (!serverDir.exists()) {
                        serverDir.mkdirs();
                    }
                    return serverDir;
                }
            }
            
            // 如果是单人游戏，使用世界名称
            if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
                String worldName = minecraft.getSingleplayerServer().getWorldData().getLevelName();
                File worldDir = new File(savesDir, worldName);
                File dataDir = new File(worldDir, "hotbarexpand");
                if (!dataDir.exists()) {
                    dataDir.mkdirs();
                }
                return dataDir;
            }
            // 如果是多人游戏，使用服务器地址
            else if (minecraft.getCurrentServer() != null) {
                String serverAddress = minecraft.getCurrentServer().ip.replace(":", "_");
                File serverDir = new File(new File(gameDir, "hotbarexpand"), serverAddress);
                if (!serverDir.exists()) {
                    serverDir.mkdirs();
                }
                return serverDir;
            }
        } catch (Exception e) {
            System.out.println("[HotbarExpand] Error getting save directory: " + e.getMessage());
        }
        // 回退：使用游戏目录下的 hotbarexpand 目录，确保可以保存
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.gameDirectory != null) {
                File fallback = new File(minecraft.gameDirectory, "hotbarexpand");
                if (!fallback.exists()) fallback.mkdirs();
                return fallback;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
