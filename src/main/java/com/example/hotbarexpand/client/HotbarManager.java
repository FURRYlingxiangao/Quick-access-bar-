package com.example.hotbarexpand.client;

import com.example.hotbarexpand.client.gui.HotbarInventoryScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

public class HotbarManager {
    // 存储非生存模式下的快捷栏数据
    private static final List<List<ItemStack>> hotbars = new ArrayList<>();
    // 副手物品槽列表
    private static final List<ItemStack> offhandSlots = new ArrayList<>();

    // 当前激活的快捷栏索引（0-3，生存模式下）
    private static int currentHotbarIndex = 0;

    // 展开状态
    private static boolean isExpanded = false;

    // 生存模式最大快捷栏数量
    private static final int SURVIVAL_MAX_HOTBARS = 4;

    // 记录上一次的游戏模式，用于检测切换
    private static GameType lastGameType = null;

    // 记录每个快捷栏选中的槽位（0-8）
    private static final List<Integer> hotbarSelectedSlots = new ArrayList<>();

    // 防止onClientTick覆盖刚加载的快捷栏
    private static int syncCooldownTicks = 0;
    private static final int SYNC_COOLDOWN = 10; // 10 tick冷却时间

    // 生存模式下快捷栏与背包行的固定对应关系 (HotSwap算法)
    // 栏0(栏1) = 快捷栏槽位 0-8
    // 栏1(栏2) = 背包第3行槽位 9-17
    // 栏2(栏3) = 背包第2行槽位 18-26
    // 栏3(栏4) = 背包第1行槽位 27-35
    // HotSwap的槽位映射：用于滚动时计算目标槽位
    private static final int[] SLOTS_SCROLL_DOWN = {0, 9, 18, 27};   // 向下滚动(栏1->栏2->栏3->栏4)
    private static final int[] SLOTS_SCROLL_UP = {0, 27, 18, 9};     // 向上滚动(栏1->栏4->栏3->栏2)

    static {
        // 初始化9个默认快捷栏（用于非生存模式）
        for (int i = 0; i < 9; i++) {
            List<ItemStack> hotbar = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                hotbar.add(ItemStack.EMPTY);
            }
            hotbars.add(hotbar);
            offhandSlots.add(ItemStack.EMPTY);
            hotbarSelectedSlots.add(0);
        }
    }

    /**
     * 检查当前是否为生存模式
     */
    public static boolean isSurvivalMode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) return false;
        return minecraft.gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    /**
     * 获取当前模式下的最大快捷栏数量
     */
    public static int getMaxHotbarCount() {
        if (isSurvivalMode()) {
            return SURVIVAL_MAX_HOTBARS;
        }
        return hotbars.size();
    }

    /**
     * 确保指定索引的快捷栏存在（非生存模式使用）
     */
    private static void ensureHotbarExists(int index) {
        while (hotbars.size() <= index) {
            List<ItemStack> newHotbar = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                newHotbar.add(ItemStack.EMPTY);
            }
            hotbars.add(newHotbar);
            offhandSlots.add(ItemStack.EMPTY);
            hotbarSelectedSlots.add(0);
        }
    }

    /**
     * 获取指定快捷栏的物品
     * 生存模式：直接从对应背包行读取
     * 非生存模式：从存储的数据读取
     */
    public static List<ItemStack> getHotbar(int index) {
        if (index < 0) index = 0;

        // 生存模式下，直接从原版背包读取对应行
        if (isSurvivalMode()) {
            return getSurvivalHotbarFromInventory(index);
        }

        // 非生存模式，从存储的数据读取
        ensureHotbarExists(index);
        return hotbars.get(index);
    }

    /**
     * 从原版背包获取指定快捷栏对应的物品 (身份追踪版本)
     * index 是栏的身份(0=栏1, 1=栏2, ...)，根据身份找到当前位置并获取物品
     */
    private static List<ItemStack> getSurvivalHotbarFromInventory(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        List<ItemStack> result = new ArrayList<>();

        // 如果玩家不存在，返回空列表
        if (player == null) {
            for (int i = 0; i < 9; i++) {
                result.add(ItemStack.EMPTY);
            }
            return result;
        }

        // 确保索引在有效范围内
        if (index < 0 || index >= SURVIVAL_MAX_HOTBARS) {
            index = 0;
        }

        // 根据身份找到当前位置
        int currentLocation = getHotbarLocation(index);

        // 根据当前位置获取物品
        int startSlot = getSlotOffsetForHotbar(currentLocation);

        for (int i = 0; i < 9; i++) {
            int slot = startSlot + i;
            if (slot < player.getInventory().items.size()) {
                result.add(player.getInventory().items.get(slot).copy());
            } else {
                result.add(ItemStack.EMPTY);
            }
        }
        return result;
    }

    /**
     * 获取指定快捷栏对应的背包槽位偏移量 (固定位置)
     * @param hotbarIndex 快捷栏索引 (0-3)
     * @return 背包槽位起始索引
     */
    public static int getSlotOffsetForHotbar(int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= SURVIVAL_MAX_HOTBARS) {
            return 0;
        }
        // 栏1(索引0)在快捷栏槽位0-8
        // 栏2-4使用SLOTS_SCROLL_DOWN映射
        if (hotbarIndex == 0) return 0;
        return SLOTS_SCROLL_DOWN[hotbarIndex];
    }

    /**
     * 获取副手物品（动态追踪版本）
     * 根据栏的原始索引，动态找到当前位置的副手物品
     */
    public static ItemStack getOffhandItem(int index) {
        // 生存模式下，所有快捷栏共享同一个副手（原版副手）
        if (isSurvivalMode()) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player != null) {
                return player.getInventory().offhand.get(0).copy();
            }
            return ItemStack.EMPTY;
        }

        // 非生存模式
        if (index < 0) return ItemStack.EMPTY;
        ensureHotbarExists(index);
        return offhandSlots.get(index);
    }

    /**
     * 设置副手物品（仅非生存模式有效）
     */
    public static void setOffhandItem(int index, ItemStack stack) {
        if (isSurvivalMode()) {
            // 生存模式下直接设置原版副手
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player != null) {
                player.getInventory().offhand.set(0, stack.copy());
            }
            return;
        }

        // 非生存模式
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
        return getMaxHotbarCount();
    }

    /**
     * 添加新的快捷栏（仅非生存模式）
     */
    public static void addHotbar() {
        if (isSurvivalMode()) return; // 生存模式下不能添加

        List<ItemStack> newHotbar = new ArrayList<>();
        for (int j = 0; j < 9; j++) {
            newHotbar.add(ItemStack.EMPTY);
        }
        hotbars.add(newHotbar);
        offhandSlots.add(ItemStack.EMPTY);
        // 保持选中槽位列表同步
        hotbarSelectedSlots.add(0);
    }

    /**
     * 删除指定索引的快捷栏（仅非生存模式）
     */
    public static void removeHotbar(int index) {
        if (isSurvivalMode()) return; // 生存模式下不能删除
        if (index < 0 || index >= hotbars.size() || hotbars.size() <= 1) return;

        // 调整当前索引
        if (currentHotbarIndex >= hotbars.size() - 1) {
            currentHotbarIndex = hotbars.size() - 2;
        } else if (index < currentHotbarIndex) {
            currentHotbarIndex--;
        }

        // 从列表中移除
        hotbars.remove(index);
        offhandSlots.remove(index);
        if (index >= 0 && index < hotbarSelectedSlots.size()) {
            hotbarSelectedSlots.remove(index);
        }

        // 加载当前快捷栏到玩家背包
        loadHotbarToPlayer(currentHotbarIndex);
    }

    /**
     * 交换两个快捷栏的数据（仅非生存模式）
     */
    public static void swapHotbars(int index1, int index2) {
        if (isSurvivalMode()) return; // 生存模式下不能交换
        if (index1 < 0 || index1 >= hotbars.size() || index2 < 0 || index2 >= hotbars.size()) return;

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
        hotbarSelectedSlots.clear();
        currentHotbarIndex = 0;
        // 重置栏身份映射
        resetHotbarIdentityMap();
        // 初始化为默认9个空快捷栏的选中槽位
        for (int i = 0; i < 9; i++) {
            hotbarSelectedSlots.add(0);
        }
    }

    public static int getCurrentHotbarIndex() {
        return currentHotbarIndex;
    }

    /**
     * 获取当前选中的槽位索引（在选中栏中的位置）
     */
    public static int getSelectedSlotInHotbar() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return 0;
        return player.getInventory().selected;
    }

    // 存储栏1的物品，用于在切换到其他栏时恢复
    private static final ItemStack[] hotbar1Backup = new ItemStack[9];
    // 标记当前是否正在显示选中栏的物品（而非栏1）
    private static boolean isShowingSelectedHotbar = false;

    // ==================== 栏追踪功能 ====================
    // 栏身份到位置的映射：hotbarIdentityMap[身份] = 当前位置
    // 身份0=栏1, 身份1=栏2, 身份2=栏3, 身份3=栏4
    private static final int[] hotbarIdentityMap = new int[SURVIVAL_MAX_HOTBARS];
    // 初始化：每个身份初始位于对应位置
    static {
        for (int i = 0; i < SURVIVAL_MAX_HOTBARS; i++) {
            hotbarIdentityMap[i] = i;
        }
    }

    /**
     * 获取栏身份当前所在的位置
     * @param identity 栏身份 (0=栏1, 1=栏2, 2=栏3, 3=栏4)
     * @return 当前位置索引
     */
    public static int getHotbarLocation(int identity) {
        if (identity < 0 || identity >= SURVIVAL_MAX_HOTBARS) {
            return identity;
        }
        return hotbarIdentityMap[identity];
    }

    /**
     * 获取位置对应的栏身份
     * @param location 位置索引
     * @return 栏身份
     */
    public static int getIdentityAtLocation(int location) {
        if (location < 0 || location >= SURVIVAL_MAX_HOTBARS) {
            return location;
        }
        // 查找哪个身份位于这个位置
        for (int identity = 0; identity < SURVIVAL_MAX_HOTBARS; identity++) {
            if (hotbarIdentityMap[identity] == location) {
                return identity;
            }
        }
        return location;
    }

    /**
     * 获取栏的显示编号
     * @param hotbarIdentity 栏的身份 (0=栏1, 1=栏2, ...)
     * @return 显示编号 (1, 2, 3, 4)
     */
    public static int getHotbarOriginalIndex(int hotbarIdentity) {
        // 直接返回身份+1作为显示编号
        // 身份是固定的，不会随位置变化
        return hotbarIdentity + 1;
    }

    /**
     * 交换两个栏的位置
     * @param identity1 第一个栏的身份
     * @param identity2 第二个栏的身份
     */
    public static void swapHotbarLocations(int identity1, int identity2) {
        if (identity1 < 0 || identity1 >= SURVIVAL_MAX_HOTBARS ||
            identity2 < 0 || identity2 >= SURVIVAL_MAX_HOTBARS) {
            return;
        }
        // 交换位置
        int temp = hotbarIdentityMap[identity1];
        hotbarIdentityMap[identity1] = hotbarIdentityMap[identity2];
        hotbarIdentityMap[identity2] = temp;

        System.out.println("[HotbarExpand] Swapped hotbar " + (identity1 + 1) + " and " + (identity2 + 1));
        System.out.println("[HotbarExpand] New mappings:");
        for (int i = 0; i < SURVIVAL_MAX_HOTBARS; i++) {
            System.out.println("[HotbarExpand]   Hotbar " + (i + 1) + " is at position " + (hotbarIdentityMap[i] + 1));
        }
    }

    /**
     * 获取栏身份映射数组
     * @return 身份映射数组的副本
     */
    public static int[] getHotbarIdentityMap() {
        int[] copy = new int[SURVIVAL_MAX_HOTBARS];
        System.arraycopy(hotbarIdentityMap, 0, copy, 0, SURVIVAL_MAX_HOTBARS);
        return copy;
    }

    /**
     * 设置栏身份映射数组
     * @param map 新的身份映射数组
     */
    public static void setHotbarIdentityMap(int[] map) {
        if (map == null || map.length != SURVIVAL_MAX_HOTBARS) {
            System.out.println("[HotbarExpand] Invalid identity map, ignoring");
            return;
        }
        System.arraycopy(map, 0, hotbarIdentityMap, 0, SURVIVAL_MAX_HOTBARS);
        System.out.println("[HotbarExpand] Identity map loaded:");
        for (int i = 0; i < SURVIVAL_MAX_HOTBARS; i++) {
            System.out.println("[HotbarExpand]   Hotbar " + (i + 1) + " is at position " + (hotbarIdentityMap[i] + 1));
        }
    }

    /**
     * 重置栏身份映射为默认值
     */
    public static void resetHotbarIdentityMap() {
        for (int i = 0; i < SURVIVAL_MAX_HOTBARS; i++) {
            hotbarIdentityMap[i] = i;
        }
        System.out.println("[HotbarExpand] Identity map reset to default");
    }

    /**
     * 同步栏追踪状态（在物品交换后调用）
     * 根据当前背包内容更新身份映射
     */
    public static void syncHotbarTracking() {
        // 在HotSwap交换后，栏的位置已经改变
        // 需要更新身份映射来反映新的位置
        // 但HotSwap交换的是物品，不是栏的身份
        // 所以我们需要检测哪些位置的内容发生了变化

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 记录每个身份期望的内容（基于交换前的状态）
        // 这里我们假设交换是成对发生的
        // 简化处理：如果检测到位置0和位置9的内容互换，就交换栏1和栏2的身份映射

        System.out.println("[HotbarExpand] Hotbar tracking synced");
    }

    /**
     * 设置当前快捷栏索引
     * 创造模式：保存当前栏到列表，加载新栏到玩家背包
     * 生存模式：使用HotSwap算法交换背包中的物品
     */
    public static void setCurrentHotbarIndex(int index) {
        int maxHotbars = getMaxHotbarCount();

        if (index < 0 || index >= maxHotbars || index == currentHotbarIndex) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 保存当前快捷栏选中的槽位
        if (!isSurvivalMode()) {
            ensureHotbarExists(currentHotbarIndex);
        }
        // 确保 hotbarSelectedSlots 有足够的大小
        while (hotbarSelectedSlots.size() <= currentHotbarIndex) {
            hotbarSelectedSlots.add(0);
        }
        hotbarSelectedSlots.set(currentHotbarIndex, player.getInventory().selected);
        System.out.println("[HotbarExpand] Saved slot " + player.getInventory().selected + " for hotbar " + (currentHotbarIndex + 1));

        if (isSurvivalMode()) {
            // 生存模式：使用HotSwap算法
            setCurrentHotbarIndexSurvival(index);
        } else {
            // 创造模式：使用保存/加载方式
            setCurrentHotbarIndexCreative(index);
        }

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

    /**
     * 创造模式：设置当前快捷栏索引（保存/加载方式）
     */
    private static void setCurrentHotbarIndexCreative(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 保存当前选中的槽位
        int currentSelectedSlot = player.getInventory().selected;

        // 保存当前快捷栏到列表（从玩家背包读取，确保包含最新的物品）
        savePlayerInventoryToHotbar(currentHotbarIndex);
        System.out.println("[HotbarExpand] Saved hotbar " + (currentHotbarIndex + 1) + " from player inventory");

        // 打印保存的内容（调试用）
        List<ItemStack> savedHotbar = hotbars.get(currentHotbarIndex);
        for (int i = 0; i < 9; i++) {
            if (!savedHotbar.get(i).isEmpty()) {
                System.out.println("[HotbarExpand]   Slot " + i + ": " + savedHotbar.get(i).getItem() + " x" + savedHotbar.get(i).getCount());
            }
        }

        // 更新索引
        currentHotbarIndex = index;

        // 打印要加载的内容（调试用）
        List<ItemStack> loadHotbar = hotbars.get(index);
        System.out.println("[HotbarExpand] Loading hotbar " + (index + 1) + ":");
        for (int i = 0; i < 9; i++) {
            if (!loadHotbar.get(i).isEmpty()) {
                System.out.println("[HotbarExpand]   Slot " + i + ": " + loadHotbar.get(i).getItem() + " x" + loadHotbar.get(i).getCount());
            }
        }

        // 加载新快捷栏到玩家背包
        loadHotbarToPlayer(index);

        // 设置冷却时间，防止onClientTick立即覆盖
        syncCooldownTicks = SYNC_COOLDOWN;
        System.out.println("[HotbarExpand] Set sync cooldown to " + SYNC_COOLDOWN + " ticks");

        // 保持选中槽位不变（创造模式下物品加载到相同位置）
        ensureHotbarExists(index);
        hotbarSelectedSlots.set(index, currentSelectedSlot);
        player.getInventory().selected = currentSelectedSlot;
        System.out.println("[HotbarExpand] Kept slot " + currentSelectedSlot + " for hotbar " + (index + 1));

        System.out.println("[HotbarExpand] Switched to hotbar " + (index + 1) + " (Creative mode)");
    }

    /**
     * 生存模式：设置当前快捷栏索引（HotSwap算法）
     */
    private static void setCurrentHotbarIndexSurvival(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 保存当前选中的槽位（相对于快捷栏的位置）
        int currentSelectedSlot = player.getInventory().selected;

        // HotSwap算法：在切换前，如果当前不是栏1，先交换回栏1
        if (currentHotbarIndex != 0) {
            // 将当前栏的物品交换回背包
            for (int i = 0; i < 9; i++) {
                int targetSlot = getIndex(i, currentHotbarIndex);
                minecraft.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId,
                    targetSlot,
                    i,
                    net.minecraft.world.inventory.ClickType.SWAP,
                    player
                );
            }
            // 更新栏位置映射
            swapHotbarLocations(0, currentHotbarIndex);
            System.out.println("[HotbarExpand] HotSwap: swapped back hotbar " + (currentHotbarIndex + 1));
        }

        // 更新索引
        currentHotbarIndex = index;

        // HotSwap算法：如果目标不是栏1，交换目标栏到快捷栏
        if (index != 0) {
            // 将目标栏的物品交换到快捷栏
            for (int i = 0; i < 9; i++) {
                int targetSlot = getIndex(i, index);
                minecraft.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId,
                    targetSlot,
                    i,
                    net.minecraft.world.inventory.ClickType.SWAP,
                    player
                );
            }
            // 更新栏位置映射
            swapHotbarLocations(0, index);
            isShowingSelectedHotbar = true;
            System.out.println("[HotbarExpand] HotSwap: swapped in hotbar " + (index + 1));
        } else {
            isShowingSelectedHotbar = false;
        }

        // HotSwap算法：保持选中槽位不变（因为物品已经交换到对应位置）
        // 保存到目标栏的选中槽位记录中
        ensureHotbarExists(index);
        hotbarSelectedSlots.set(index, currentSelectedSlot);
        // 保持当前选中槽位
        player.getInventory().selected = currentSelectedSlot;
        System.out.println("[HotbarExpand] Kept slot " + currentSelectedSlot + " for hotbar " + (index + 1));

        System.out.println("[HotbarExpand] Switched to hotbar " + (index + 1) + " using HotSwap algorithm");

        // 同步栏追踪状态
        syncHotbarTracking();
    }

    /**
     * HotSwap算法：计算目标槽位索引
     * @param currentSlot 当前快捷栏槽位 (0-8)
     * @param scrollDelta 滚动偏移量/快捷栏索引
     * @return 对应的背包槽位索引
     */
    public static int getIndex(int currentSlot, int scrollDelta) {
        int result = currentSlot;
        if (scrollDelta < 0) {
            // 向上滚动
            result += SLOTS_SCROLL_UP[Math.abs(scrollDelta)];
        } else {
            // 向下滚动
            result += SLOTS_SCROLL_DOWN[Math.abs(scrollDelta)];
        }
        return result;
    }

    /**
     * 恢复栏1的物品到快捷栏（槽位0-8）- HotSwap算法
     * 使用SWAP操作将栏1的物品交换回快捷栏
     */
    public static void restoreHotbar1ToQuickbar(Player player) {
        if (!isShowingSelectedHotbar) return; // 已经在显示栏1了
        if (currentHotbarIndex == 0) return; // 当前已经是栏1

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) return;

        // HotSwap算法：将当前栏的物品与栏1交换回来
        // 交换全部9个槽位
        for (int i = 0; i < 9; i++) {
            int targetSlot = getIndex(i, currentHotbarIndex);
            minecraft.gameMode.handleInventoryMouseClick(
                player.containerMenu.containerId,
                targetSlot,
                i,
                net.minecraft.world.inventory.ClickType.SWAP,
                player
            );
        }

        isShowingSelectedHotbar = false;
        System.out.println("[HotbarExpand] HotSwap restored hotbar 1 to quickbar");
    }

    /**
     * 检查当前是否正在显示选中栏的物品
     */
    public static boolean isShowingSelectedHotbar() {
        return isShowingSelectedHotbar;
    }

    /**
     * 获取当前应该使用的物品（用于服务器通信）- HotSwap算法
     * 返回选中栏中当前选中槽位的物品
     */
    public static ItemStack getCurrentUsableItem() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return ItemStack.EMPTY;

        int selectedSlot = player.getInventory().selected;

        // 生存模式下，返回选中栏对应槽位的物品 (HotSwap槽位映射)
        if (isSurvivalMode()) {
            int actualSlot = getIndex(selectedSlot, currentHotbarIndex);
            if (actualSlot < player.getInventory().items.size()) {
                return player.getInventory().items.get(actualSlot);
            }
        }

        // 非生存模式或失败时，返回快捷栏当前物品
        return player.getInventory().getItem(selectedSlot);
    }

    /**
     * 选择指定快捷栏的指定槽位
     */
    public static boolean selectHotbarSlot(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return false;
        if (hotbarIdx < 0 || hotbarIdx >= getHotbarCount() || slotIdx < 0 || slotIdx >= 9) return false;

        // 切换到目标快捷栏
        if (hotbarIdx != currentHotbarIndex) {
            setCurrentHotbarIndex(hotbarIdx);
        }

        // 设置选中的槽位
        player.getInventory().selected = slotIdx;
        ensureHotbarExists(hotbarIdx);
        hotbarSelectedSlots.set(hotbarIdx, slotIdx);
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        return true;
    }

    /**
     * 查找并选择指定物品
     */
    public static boolean findAndSelectItem(ItemStack targetItem) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || targetItem.isEmpty()) return false;

        int currentHotbar = getCurrentHotbarIndex();

        // 先在当前快捷栏查找（同时检查玩家背包和hotbars列表）
        // 先检查玩家背包（生存模式）
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isSameItem(item, targetItem)) {
                return selectHotbarSlot(currentHotbar, slot);
            }
        }
        // 再检查hotbars列表（创造模式，因为创造模式玩家背包可能和hotbars不同步）
        List<ItemStack> currentHotbarItems = getHotbar(currentHotbar);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = currentHotbarItems.get(slot);
            if (isSameItem(item, targetItem)) {
                return selectHotbarSlot(currentHotbar, slot);
            }
        }

        // 在其他快捷栏查找
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
        // 使用ResourceLocation比较（更宽松的比较，只比较物品类型）
        var item1Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item1.getItem());
        var item2Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item2.getItem());
        return item1Key.getNamespace().equals(item2Key.getNamespace()) && 
               item1Key.getPath().equals(item2Key.getPath());
    }

    /**
     * 切换到下一个快捷栏
     */
    public static void scrollToNext() {
        int maxHotbars = getMaxHotbarCount();
        int nextIndex = (currentHotbarIndex + 1) % maxHotbars;
        setCurrentHotbarIndex(nextIndex);
    }

    /**
     * 切换到上一个快捷栏
     */
    public static void scrollToPrevious() {
        int maxHotbars = getMaxHotbarCount();
        int prevIndex = (currentHotbarIndex - 1 + maxHotbars) % maxHotbars;
        setCurrentHotbarIndex(prevIndex);
    }

    /**
     * 非生存模式：将快捷栏数据加载到玩家背包
     */
    private static void loadHotbarToPlayer(int index) {
        if (isSurvivalMode()) return; // 生存模式下不加载

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        ensureHotbarExists(index);

        ClientPacketListener connection = minecraft.getConnection();
        boolean isCreative = minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() == GameType.CREATIVE;

        List<ItemStack> hotbar = hotbars.get(index);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = hotbar.get(i).copy();
            player.getInventory().setItem(i, stack);
            // 创造模式下同步到服务器
            // 使用正确的槽位编号：36-44 对应快捷栏
            if (isCreative && connection != null) {
                int creativeSlot = 36 + i; // 36-44 是创造模式协议中的快捷栏槽位
                connection.send(new ServerboundSetCreativeModeSlotPacket(creativeSlot, stack));
            }
        }
        // 加载副手物品（副手槽位是45）
        ItemStack offhandStack = offhandSlots.get(index).copy();
        player.getInventory().offhand.set(0, offhandStack);
        if (isCreative && connection != null) {
            connection.send(new ServerboundSetCreativeModeSlotPacket(45, offhandStack));
        }
    }

    /**
     * 非生存模式：保存玩家背包到当前快捷栏
     */
    public static void savePlayerInventoryToCurrentHotbar() {
        savePlayerInventoryToHotbar(currentHotbarIndex);
    }

    /**
     * 非生存模式：保存玩家背包到指定快捷栏
     */
    private static void savePlayerInventoryToHotbar(int index) {
        if (isSurvivalMode()) return; // 生存模式下不保存

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        ensureHotbarExists(index);

        List<ItemStack> hotbar = hotbars.get(index);
        for (int i = 0; i < 9; i++) {
            hotbar.set(i, player.getInventory().getItem(i).copy());
        }
        // 保存副手物品
        offhandSlots.set(index, player.getOffhandItem().copy());
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

    /**
     * 设置快捷栏物品
     * @param hotbarIdx 快捷栏索引
     * @param slotIdx 槽位索引（0-8）
     * @param stack 物品堆
     */
    public static void setHotbarItem(int hotbarIdx, int slotIdx, ItemStack stack) {
        ensureHotbarExists(hotbarIdx);
        if (slotIdx >= 0 && slotIdx < 9) {
            hotbars.get(hotbarIdx).set(slotIdx, stack.copy());
        }
    }

    /**
     * 同步物品到服务器（创造模式）
     * @param slotIdx 槽位索引（0-8为快捷栏）
     * @param stack 物品堆
     */
    public static void syncItemToServer(int slotIdx, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) return;
        if (minecraft.gameMode.getPlayerMode() != GameType.CREATIVE) return;
        
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) return;
        
        // 在创造模式协议中，快捷栏槽位编号是 36-44（对应玩家背包窗口）
        // 而不是 0-8（对应玩家背包对象）
        // 36 = 快捷栏第1格，44 = 快捷栏第9格
        int creativeSlot = 36 + slotIdx;
        connection.send(new ServerboundSetCreativeModeSlotPacket(creativeSlot, stack));
    }

    private static boolean configLoaded = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            configLoaded = false;
            lastGameType = null;
            return;
        }

        // 检测游戏模式切换
        GameType currentGameType = minecraft.gameMode.getPlayerMode();
        if (lastGameType != null && lastGameType != currentGameType) {
            System.out.println("[HotbarExpand] Game mode changed from " + lastGameType + " to " + currentGameType);
            onGameModeChanged(lastGameType, currentGameType);
        }
        lastGameType = currentGameType;

        // 加载配置（只在玩家第一次加入时）
        if (!configLoaded) {
            HotbarConfig.loadConfig();
            configLoaded = true;
        }

        // 处理/clear指令：检测玩家背包是否被清空
        // 只在创造模式下处理，且不在冷却时间内
        if (minecraft.level != null && syncCooldownTicks <= 0) {
            handleClearCommand(player);
        }

        // 冷却时间递减
        if (syncCooldownTicks > 0) {
            syncCooldownTicks--;
        }
    }

    /**
     * 处理/clear指令：当检测到玩家背包被清空时，只更新当前快捷栏的数据
     * 不切换快捷栏，不加载数据到玩家背包，只同步数据状态
     */
    private static void handleClearCommand(Player player) {
        // 检查玩家背包是否为空（/clear指令执行后）
        boolean isPlayerInventoryEmpty = true;
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                isPlayerInventoryEmpty = false;
                break;
            }
        }

        // 如果背包为空，同步当前快捷栏数据（只更新数据，不加载）
        if (isPlayerInventoryEmpty) {
            ensureHotbarExists(currentHotbarIndex);
            List<ItemStack> currentHotbar = hotbars.get(currentHotbarIndex);
            
            // 只更新当前快捷栏的数据，不加载到玩家背包
            boolean wasNotEmpty = false;
            for (int i = 0; i < 9; i++) {
                if (!currentHotbar.get(i).isEmpty()) {
                    wasNotEmpty = true;
                }
                currentHotbar.set(i, ItemStack.EMPTY);
            }
            // 同步副手物品
            if (!offhandSlots.get(currentHotbarIndex).isEmpty()) {
                wasNotEmpty = true;
            }
            offhandSlots.set(currentHotbarIndex, ItemStack.EMPTY);

            // 如果之前有物品，打印日志
            if (wasNotEmpty) {
                System.out.println("[HotbarExpand] /clear detected: cleared current hotbar " + (currentHotbarIndex + 1));
            }
        }
    }

    /**
     * 游戏模式切换时调用 - 全局重置与同步
     */
    private static void onGameModeChanged(GameType oldMode, GameType newMode) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        System.out.println("[HotbarExpand] ========== Game Mode Change ==========");
        System.out.println("[HotbarExpand] From: " + oldMode + " To: " + newMode);

        // ========== 全局重置 ==========
        // 1. 所有模式之间切换都保存快捷栏展开状态
        // 只有切换到旁观者模式时才重置展开状态
        if (newMode == GameType.SPECTATOR) {
            isExpanded = false;
            ExpandedHotbarOverlay.resetExpandedState();
        }

        // 2. 重置栏身份映射（生存模式下）
        if (newMode == GameType.SURVIVAL) {
            resetHotbarIdentityMap();
        }

        // 3. 重置选中槽位记录
        for (int i = 0; i < hotbarSelectedSlots.size(); i++) {
            hotbarSelectedSlots.set(i, 0);
        }

        // ========== 数据同步 ==========
        if (oldMode != GameType.SURVIVAL && newMode == GameType.SURVIVAL) {
            // 从非生存模式切换到生存模式
            System.out.println("[HotbarExpand] Syncing to SURVIVAL mode...");

            // 保存非生存模式的数据
            savePlayerInventoryToCurrentHotbar();

            // 重置到栏1（生存模式固定使用栏1）
            currentHotbarIndex = 0;

            // 同步生存模式数据到存储
            syncSurvivalDataToStorage();

        } else if (oldMode == GameType.SURVIVAL && newMode != GameType.SURVIVAL) {
            // 从生存模式切换到非生存模式
            System.out.println("[HotbarExpand] Syncing from SURVIVAL mode...");

            // 同步生存模式数据到存储
            syncSurvivalDataToStorage();

            // 加载当前快捷栏到玩家背包
            loadHotbarToPlayer(currentHotbarIndex);

        } else {
            // 其他模式之间的切换
            System.out.println("[HotbarExpand] Syncing between non-survival modes...");

            // 保存当前数据
            savePlayerInventoryToCurrentHotbar();

            // 加载当前快捷栏
            loadHotbarToPlayer(currentHotbarIndex);
        }

        // ========== GUI同步 ==========
        // 通知GUI刷新
        HotbarInventoryScreen.onGameModeChanged();

        // 重置GUI滚动偏移
        HotbarInventoryScreen.resetScrollOffset();

        // 重置主界面滚动偏移
        try {
            Class<?> overlayClass = Class.forName("com.example.hotbarexpand.client.ExpandedHotbarOverlay");
            java.lang.reflect.Method method = overlayClass.getMethod("resetMainScrollOffset");
            method.invoke(null);
        } catch (Exception e) {
            // 忽略错误
        }

        System.out.println("[HotbarExpand] Current hotbar index: " + (currentHotbarIndex + 1));
        System.out.println("[HotbarExpand] ========== Sync Complete ==========");
    }

    /**
     * 同步生存模式数据到存储
     * 将原版背包中的栏1-4数据同步到内部存储
     */
    private static void syncSurvivalDataToStorage() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        // 同步所有4个栏的数据（栏1-4对应背包的不同位置）
        for (int hotbarIdx = 0; hotbarIdx < SURVIVAL_MAX_HOTBARS; hotbarIdx++) {
            ensureHotbarExists(hotbarIdx);
            List<ItemStack> hotbar = hotbars.get(hotbarIdx);

            // 获取该栏对应的背包槽位范围
            int startSlot = getSlotOffsetForHotbar(hotbarIdx);

            for (int i = 0; i < 9; i++) {
                int slot = startSlot + i;
                if (slot < player.getInventory().items.size()) {
                    hotbar.set(i, player.getInventory().items.get(slot).copy());
                }
            }
        }

        // 同步副手（所有栏共享同一个副手）
        ItemStack offhandItem = player.getInventory().offhand.get(0).copy();
        for (int i = 0; i < SURVIVAL_MAX_HOTBARS; i++) {
            ensureHotbarExists(i);
            offhandSlots.set(i, offhandItem.copy());
        }

        System.out.println("[HotbarExpand] Survival data synced to storage");
    }
}
