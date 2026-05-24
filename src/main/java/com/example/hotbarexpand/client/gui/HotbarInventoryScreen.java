package com.example.hotbarexpand.client.gui;

import com.example.hotbarexpand.client.HotbarManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 快捷栏背包界面 - 在原版背包右侧显示可交互的快捷栏面板
 */
public class HotbarInventoryScreen {
    // 使用原版背包纹理
    private static final ResourceLocation INVENTORY_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 2;
    private static final int NUMBER_WIDTH = 14; // 编号按钮宽度
    private static final int NUMBER_GAP = 6; // 编号与快捷栏之间的间距
    private static final int CONTENT_WIDTH = 9 * SLOT_SIZE + SLOT_SIZE + 4; // 9格 + 副手格 + 间距
    private static final int SCROLLBAR_WIDTH = 12; // 滚动条宽度
    private static final int BUTTON_SIZE = 12; // 按钮大小
    private static final int BUTTON_GAP = 2; // 按钮间距
    private static final int PANEL_WIDTH = NUMBER_WIDTH + NUMBER_GAP + CONTENT_WIDTH + SCROLLBAR_WIDTH + BUTTON_SIZE + BUTTON_GAP + 8; // 总宽度
    private static final int RIGHT_GAP = 25;
    private static final int SCREEN_PADDING = 5;
    private static final Component PANEL_TOO_SMALL_TEXT = Component.literal("[警告]屏幕过小 无法容纳");
    
    // 面板位置（相对于屏幕）
    private static int panelX = 0;
    private static int panelY = 0;
    private static int panelHeight = 0;
    private static boolean isPanelVisible = false;
    private static boolean panelTooSmall = false;
    
    // 当前高亮的快捷栏索引 - 实时从HotbarManager获取
    private static int currentHotbarIndexCache = -1;
    
    // 可变的快捷栏列表
    private static List<List<ItemStack>> hotbarList = new ArrayList<>();
    private static List<ItemStack> offhandList = new ArrayList<>();
    private static int maxHotbars = 9; // 默认9个快捷栏
    private static int scrollOffset = 0; // 滚动偏移
    private static final int VISIBLE_HOTBARS = 9; // 可见快捷栏数量
    
    // 滚动条拖动状态
    private static boolean isDraggingScrollbar = false;
    private static int scrollbarDragStartY = 0;
    private static int scrollbarDragStartOffset = 0;
    
    // 物品拖动状态
    private static boolean isDraggingItems = false;
    private static ItemStack draggedItem = ItemStack.EMPTY;
    private static int dragButton = -1;
    private static List<int[]> draggedSlots = new ArrayList<>(); // 记录已经分发过的槽位 [hotbarIdx, slotIdx]

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        
        // 每次打开背包都重新计算位置；太小则直接隐藏
        if (!recalculatePanelPosition(screen)) {
            isPanelVisible = false;
            panelTooSmall = true;
            return;
        }
        panelTooSmall = false;
        
        // 初始化快捷栏列表（如果为空）
        initHotbarList();
        
        // 同步当前索引和当前快捷栏数量
        maxHotbars = HotbarManager.getHotbarCount();
        currentHotbarIndexCache = HotbarManager.getCurrentHotbarIndex();
        
        isPanelVisible = true;
        System.out.println("[HotbarExpand] Hotbar panel positioned at (" + panelX + ", " + panelY + "), height=" + panelHeight);
    }
    
    /**
     * 初始化快捷栏列表
     * 确保HotbarManager中有足够的快捷栏
     */
    private static void initHotbarList() {
        // 确保HotbarManager中有足够的快捷栏
        for (int i = 0; i < maxHotbars; i++) {
            HotbarManager.getHotbar(i); // 这会调用ensureHotbarExists确保快捷栏存在
        }
    }
    
    /**
     * 重新计算面板位置
     */
    private static boolean recalculatePanelPosition(AbstractContainerScreen<?> screen) {
        int guiLeft = screen.getGuiLeft();
        int guiWidth = getScreenImageWidth(screen);
        int rightPanelX = guiLeft + guiWidth + RIGHT_GAP;

        // 右侧放不下就直接隐藏，不再尝试挤位置
        if (rightPanelX + PANEL_WIDTH > screen.width - SCREEN_PADDING) {
            return false;
        }

        int guiTop = screen.getGuiTop() - 15;
        int guiHeight = 166; // 原版背包高度
        
        panelHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING) + 8; // 快捷栏 + 边距
        panelY = guiTop + (guiHeight - panelHeight) / 2; // 垂直居中
        
        // 计算X位置：背包右侧 + 间距
        panelX = rightPanelX;

        return true;
    }

    private static int getScreenImageWidth(AbstractContainerScreen<?> screen) {
        try {
            Field field = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (ReflectiveOperationException ignored) {
            return screen instanceof CreativeModeInventoryScreen ? 195 : 176;
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        isPanelVisible = false;
        isDraggingScrollbar = false;
    }

    
    @SubscribeEvent
    public static void onContainerBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        // 每次渲染前重新计算位置，确保位置正确
        if (!recalculatePanelPosition(screen)) {
            isPanelVisible = false;
            panelTooSmall = true;
            renderPanelTooSmallWarning(event.getGuiGraphics(), screen);
            return;
        }
        panelTooSmall = false;
        isPanelVisible = true;
        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        
        // 实时获取当前高亮索引，确保与HotbarManager同步
        currentHotbarIndexCache = HotbarManager.getCurrentHotbarIndex();
        
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // 绘制背景（使用背包背景色）
        renderPanelBackground(guiGraphics, panelX, panelY, PANEL_WIDTH - BUTTON_SIZE - BUTTON_GAP, panelHeight);
        
        // 绘制快捷栏列表区域
        int contentStartY = panelY + 4;
        int visibleCount = Math.min(VISIBLE_HOTBARS, maxHotbars - scrollOffset);
        
        // 每次渲染都重新获取当前索引和总快捷栏数量，确保没有残留
        maxHotbars = HotbarManager.getHotbarCount();
        int maxScroll = Math.max(0, maxHotbars - VISIBLE_HOTBARS);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        int realCurrentIndex = HotbarManager.getCurrentHotbarIndex();
        
        for (int i = 0; i < visibleCount; i++) {
            int hotbarIdx = scrollOffset + i;
            int rowY = contentStartY + i * (SLOT_SIZE + PADDING);
            // 每次都直接从HotbarManager获取当前索引进行比较
            boolean isCurrent = (hotbarIdx == realCurrentIndex);
            
            // 绘制编号按钮背景
            int numberX = panelX + 4;
            renderNumberButton(guiGraphics, numberX, rowY, hotbarIdx + 1, isCurrent);
            
            // 绘制9个物品槽位（添加间距）
            int contentStartX = panelX + NUMBER_WIDTH + NUMBER_GAP + 4;
            for (int slotIdx = 0; slotIdx < 9; slotIdx++) {
                int slotX = contentStartX + slotIdx * SLOT_SIZE;
                renderSlot(guiGraphics, slotX, rowY, hotbarIdx, slotIdx, isCurrent, minecraft);
            }
            
            // 绘制副手槽位
            int offhandX = contentStartX + 9 * SLOT_SIZE + 2;
            renderOffhandSlot(guiGraphics, offhandX, rowY, hotbarIdx, isCurrent, minecraft);
        }
        
        // 计算滚动条位置
        int scrollbarX = panelX + NUMBER_WIDTH + NUMBER_GAP + CONTENT_WIDTH + 4;
        int scrollbarHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING);
        
        // 绘制滚动条（使用背包样式）
        renderScrollbar(guiGraphics, scrollbarX, contentStartY, SCROLLBAR_WIDTH, 
                scrollbarHeight, scrollOffset, maxHotbars, VISIBLE_HOTBARS);
        
        // 绘制加减按钮（在滚动条右侧）- 上移5像素，右移3像素
        int buttonX = scrollbarX + SCROLLBAR_WIDTH + BUTTON_GAP + 3;
        renderPlusMinusButtons(guiGraphics, buttonX, contentStartY - 5, scrollbarHeight, minecraft);
        
        RenderSystem.disableBlend();
    }

    private static void renderPanelTooSmallWarning(GuiGraphics guiGraphics, AbstractContainerScreen<?> screen) {
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();
        int guiWidth = getScreenImageWidth(screen);
        int textWidth = Minecraft.getInstance().font.width(PANEL_TOO_SMALL_TEXT);
        int warningX = guiLeft + guiWidth + 8;
        int warningY = guiTop + 10;

        if (warningX + textWidth > screen.width - SCREEN_PADDING) {
            warningX = Math.max(SCREEN_PADDING, screen.width - SCREEN_PADDING - textWidth);
        }

        guiGraphics.fill(warningX - 4, warningY - 3, warningX + textWidth + 4, warningY + 10, 0xCC2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, PANEL_TOO_SMALL_TEXT, warningX, warningY, 0xFFFF6666);
    }
    
    /**
     * 绘制加减按钮和收起键（在滚动条右侧垂直排列）
     */
    private static void renderPlusMinusButtons(GuiGraphics guiGraphics, int x, int y, int height, Minecraft minecraft) {
        // 按钮间距改为2像素
        int buttonSpacing = 2;
        
        // 从上到下：+、-、收起、上、下
        int plusY = y;
        renderSmallButton(guiGraphics, x, plusY, "+", 0xFF00FF00);
        
        int minusY = plusY + BUTTON_SIZE + buttonSpacing;
        renderSmallButton(guiGraphics, x, minusY, "-", 0xFFFF0000);
        
        int collapseY = minusY + BUTTON_SIZE + buttonSpacing;
        renderSmallButton(guiGraphics, x, collapseY, "×", 0xFFFFFF00);
        
        int upY = collapseY + BUTTON_SIZE + buttonSpacing;
        renderSmallButton(guiGraphics, x, upY, "↑", 0xFF00AAFF);
        
        int downY = upY + BUTTON_SIZE + buttonSpacing;
        renderSmallButton(guiGraphics, x, downY, "↓", 0xFF00AAFF);
    }
    
    /**
     * 绘制小按钮
     */
    private static void renderSmallButton(GuiGraphics guiGraphics, int x, int y, String text, int color) {
        // 按钮背景
        guiGraphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF8B8B8B);
        
        // 3D边框
        guiGraphics.fill(x, y, x + BUTTON_SIZE, y + 1, 0xFFFFFFFF); // 上
        guiGraphics.fill(x, y, x + 1, y + BUTTON_SIZE, 0xFFFFFFFF); // 左
        guiGraphics.fill(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF373737); // 下
        guiGraphics.fill(x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF373737); // 右
        
        // 文字
        int textX = x + (BUTTON_SIZE - 6) / 2;
        int textY = y + (BUTTON_SIZE - 8) / 2;
        guiGraphics.drawString(Minecraft.getInstance().font, text, textX, textY, color);
    }
    
    /**
     * 绘制滚动条（使用背包样式）
     */
    private static void renderScrollbar(GuiGraphics guiGraphics, int x, int y, int width, int height, 
            int scrollOffset, int totalItems, int visibleItems) {
        // 滚动条背景 - 使用面板背景色（与总背景一致）
        guiGraphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        
        // 绘制3D边框（与背包一致）
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF373737); // 上深
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF373737); // 左深
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF); // 下亮
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF); // 右亮
        
        // 计算滑块位置和大小
        if (totalItems > visibleItems) {
            float ratio = (float) visibleItems / totalItems;
            int thumbHeight = Math.max(20, (int)(height * ratio));
            float scrollRatio = (float) scrollOffset / (totalItems - visibleItems);
            int thumbY = y + (int)((height - thumbHeight) * scrollRatio);
            
            // 绘制滑块 - 使用背包的按钮样式
            guiGraphics.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, 0xFF8B8B8B);
            
            // 滑块3D边框（凸起效果）
            guiGraphics.fill(x + 1, thumbY, x + width - 1, thumbY + 1, 0xFFFFFFFF); // 上亮
            guiGraphics.fill(x + 1, thumbY, x + 2, thumbY + thumbHeight, 0xFFFFFFFF); // 左亮
            guiGraphics.fill(x + 1, thumbY + thumbHeight - 1, x + width - 1, thumbY + thumbHeight, 0xFF373737); // 下深
            guiGraphics.fill(x + width - 2, thumbY, x + width - 1, thumbY + thumbHeight, 0xFF373737); // 右深
        }
    }
    
    /**
     * 绘制面板背景 - 使用背包颜色
     */
    private static void renderPanelBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // 使用纯色填充背景（背包背景色：#C6C6C6）
        guiGraphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        
        // 绘制边框（深色）
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF373737); // 上边框
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF373737); // 下边框
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF373737); // 左边框
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF373737); // 右边框
        
        // 绘制内边框（亮色）
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF); // 上内边框
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF); // 左内边框
        
        // 绘制阴影（深色）
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF8B8B8B); // 下阴影
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B); // 右阴影
    }
    
    /**
     * 绘制编号按钮
     */
    private static void renderNumberButton(GuiGraphics guiGraphics, int x, int y, int number, boolean isCurrent) {
        int btnWidth = NUMBER_WIDTH;
        int btnHeight = SLOT_SIZE;
        
        // 按钮背景色 - 始终使用灰色，不随选中状态变化
        int bgColor = 0xFF8B8B8B;
        int highlightColor = 0xFFFFFFFF;
        int shadowColor = 0xFF373737;
        
        // 绘制按钮背景
        guiGraphics.fill(x, y, x + btnWidth, y + btnHeight, bgColor);
        
        // 绘制按钮边框（3D效果）
        guiGraphics.fill(x, y, x + btnWidth, y + 1, highlightColor); // 上亮边
        guiGraphics.fill(x, y, x + 1, y + btnHeight, highlightColor); // 左亮边
        guiGraphics.fill(x, y + btnHeight - 1, x + btnWidth, y + btnHeight, shadowColor); // 下阴影
        guiGraphics.fill(x + btnWidth - 1, y, x + btnWidth, y + btnHeight, shadowColor); // 右阴影
        
        // 绘制数字 - 选中时变黄，未选中时白色
        int textColor = isCurrent ? 0xFFFFFF00 : 0xFFFFFFFF;
        String text = String.valueOf(number);
        
        // 计算文字宽度以实现居中
        int textWidth = Minecraft.getInstance().font.width(text);
        int textX = x + (btnWidth - textWidth) / 2;
        int textY = y + (btnHeight - 8) / 2;
        guiGraphics.drawString(Minecraft.getInstance().font, text, textX, textY, textColor);
    }
    
    private static void renderSlot(GuiGraphics guiGraphics, int x, int y, int hotbarIdx, int slotIdx, boolean isCurrentHotbar, Minecraft minecraft) {
        // 绘制槽位背景
        guiGraphics.blit(INVENTORY_LOCATION, x, y, 7, 83, SLOT_SIZE, SLOT_SIZE, 256, 256);
        
        // 如果是当前快捷栏，绘制黄色高亮
        if (isCurrentHotbar) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 0.0f, 0.3f);
            guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80FFFF00);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        // 渲染物品
        ItemStack itemStack = getHotbarItem(hotbarIdx, slotIdx);
        if (!itemStack.isEmpty()) {
            guiGraphics.renderItem(itemStack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(minecraft.font, itemStack, x + 1, y + 1);
        }
    }
    
    private static void renderOffhandSlot(GuiGraphics guiGraphics, int x, int y, int hotbarIdx, boolean isCurrentHotbar, Minecraft minecraft) {
        // 绘制槽位背景
        guiGraphics.blit(INVENTORY_LOCATION, x, y, 7, 83, SLOT_SIZE, SLOT_SIZE, 256, 256);
        
        // 如果是当前快捷栏，绘制黄色高亮
        if (isCurrentHotbar) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 0.0f, 0.3f);
            guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x80FFFF00);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        
        // 渲染副手物品
        ItemStack offhandItem = getOffhandItem(hotbarIdx);
        if (!offhandItem.isEmpty()) {
            guiGraphics.renderItem(offhandItem, x + 1, y + 1);
            guiGraphics.renderItemDecorations(minecraft.font, offhandItem, x + 1, y + 1);
        }
    }
    
    // ========== 数据访问方法 ==========
    
    private static ItemStack getHotbarItem(int hotbarIdx, int slotIdx) {
        if (hotbarIdx >= 0 && hotbarIdx < maxHotbars && slotIdx >= 0 && slotIdx < 9) {
            // 所有快捷栏都从HotbarManager获取
            return HotbarManager.getHotbar(hotbarIdx).get(slotIdx);
        }
        return ItemStack.EMPTY;
    }
    
    private static void setHotbarItem(int hotbarIdx, int slotIdx, ItemStack stack) {
        if (hotbarIdx >= 0 && hotbarIdx < maxHotbars && slotIdx >= 0 && slotIdx < 9) {
            // 所有快捷栏都设置到HotbarManager
            HotbarManager.getHotbar(hotbarIdx).set(slotIdx, stack);
        }
    }
    
    private static ItemStack getOffhandItem(int hotbarIdx) {
        if (hotbarIdx >= 0 && hotbarIdx < maxHotbars) {
            // 所有快捷栏都从HotbarManager获取
            return HotbarManager.getOffhandItem(hotbarIdx);
        }
        return ItemStack.EMPTY;
    }
    
    private static void setOffhandItem(int hotbarIdx, ItemStack stack) {
        if (hotbarIdx >= 0 && hotbarIdx < maxHotbars) {
            // 所有快捷栏都设置到HotbarManager
            HotbarManager.setOffhandItem(hotbarIdx, stack);
        }
    }
    
    // ========== 鼠标滚轮事件 ==========
    
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isPanelVisible) return;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return;
        if (!(minecraft.screen instanceof InventoryScreen) && !(minecraft.screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        // 获取鼠标位置
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        
        // 检查鼠标是否在GUI面板内
        int contentStartY = panelY + 4;
        int scrollbarX = panelX + NUMBER_WIDTH + NUMBER_GAP + CONTENT_WIDTH + 4;
        int scrollbarHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING);
        int panelRight = scrollbarX + SCROLLBAR_WIDTH + BUTTON_SIZE + BUTTON_GAP + 4;
        
        if (mouseX >= panelX && mouseX <= panelRight && 
            mouseY >= panelY && mouseY <= panelY + panelHeight) {
            // 鼠标在GUI内，处理滚动
            double scrollDelta = event.getScrollDeltaY();
            
            if (scrollDelta > 0 && scrollOffset > 0) {
                // 向上滚动
                scrollOffset--;
                event.setCanceled(true);
            } else if (scrollDelta < 0 && scrollOffset < maxHotbars - VISIBLE_HOTBARS) {
                // 向下滚动
                scrollOffset++;
                event.setCanceled(true);
            }
        }
    }
    
    // ========== 鼠标交互 ==========
    
    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isPanelVisible) return;
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        
        int contentStartY = panelY + 4;
        int scrollbarX = panelX + NUMBER_WIDTH + NUMBER_GAP + CONTENT_WIDTH + 4;
        int scrollbarHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING);
        // 按钮位置上移5像素，右移3像素（与渲染一致）
        int buttonX = scrollbarX + SCROLLBAR_WIDTH + BUTTON_GAP + 3;
        int buttonStartY = contentStartY - 5;
        
        // 检查鼠标是否在面板区域内
        int panelRight = buttonX + BUTTON_SIZE + 4;
        int panelBottom = contentStartY + scrollbarHeight;
        boolean inPanelArea = mouseX >= panelX && mouseX <= panelRight && 
                              mouseY >= panelY && mouseY <= panelBottom;
        
        if (!inPanelArea) {
            return;
        }
        
        // 检查是否点击了加号按钮
        int plusY = buttonStartY;
        if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_SIZE && 
            mouseY >= plusY && mouseY <= plusY + BUTTON_SIZE) {
            addHotbar();
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了减号按钮
        int minusY = buttonStartY + BUTTON_SIZE + BUTTON_GAP;
        if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_SIZE && 
            mouseY >= minusY && mouseY <= minusY + BUTTON_SIZE) {
            removeCurrentHotbar();
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了收起键
        int collapseY = buttonStartY + 2 * (BUTTON_SIZE + BUTTON_GAP);
        if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_SIZE && 
            mouseY >= collapseY && mouseY <= collapseY + BUTTON_SIZE) {
            togglePanelVisibility();
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了上箭头按钮
        int upY = buttonStartY + 3 * (BUTTON_SIZE + BUTTON_GAP);
        if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_SIZE && 
            mouseY >= upY && mouseY <= upY + BUTTON_SIZE) {
            moveCurrentHotbarUp();
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了下箭头按钮
        int downY = buttonStartY + 4 * (BUTTON_SIZE + BUTTON_GAP);
        if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_SIZE && 
            mouseY >= downY && mouseY <= downY + BUTTON_SIZE) {
            moveCurrentHotbarDown();
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了滚动条区域（包括滑块）
        if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
            mouseY >= contentStartY && mouseY <= contentStartY + scrollbarHeight) {
            System.out.println("[HotbarExpand] Clicked scrollbar area, maxHotbars=" + maxHotbars);
            if (maxHotbars > VISIBLE_HOTBARS) {
                // 计算滑块高度和位置
                float ratio = (float) VISIBLE_HOTBARS / maxHotbars;
                int thumbHeight = Math.max(20, (int)(scrollbarHeight * ratio));
                float scrollRatio = (float) scrollOffset / (maxHotbars - VISIBLE_HOTBARS);
                int thumbY = contentStartY + (int)((scrollbarHeight - thumbHeight) * scrollRatio);
                
                System.out.println("[HotbarExpand] Thumb at Y=" + thumbY + "-" + (thumbY + thumbHeight) + ", click at Y=" + mouseY);
                
                // 检查是否点击了滑块本身
                if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    // 开始拖动滑块
                    isDraggingScrollbar = true;
                    scrollbarDragStartY = (int) mouseY;
                    scrollbarDragStartOffset = scrollOffset;
                    System.out.println("[HotbarExpand] Started dragging scrollbar");
                } else {
                    // 点击滚动条其他位置，直接跳转
                    float clickRatio = (float)(mouseY - contentStartY) / scrollbarHeight;
                    int maxScroll = maxHotbars - VISIBLE_HOTBARS;
                    scrollOffset = Math.min(maxScroll, Math.max(0, (int)(clickRatio * maxScroll)));
                    System.out.println("[HotbarExpand] Jumped to scrollOffset=" + scrollOffset);
                }
            }
            event.setCanceled(true);
            return;
        }
        
        // 检查是否点击了编号按钮（切换快捷栏）
        int visibleCount = Math.min(VISIBLE_HOTBARS, maxHotbars - scrollOffset);
        System.out.println("[HotbarExpand] Checking number buttons, visibleCount=" + visibleCount + ", scrollOffset=" + scrollOffset);
        for (int i = 0; i < visibleCount; i++) {
            int hotbarIdx = scrollOffset + i;
            int numberX = panelX + 4;
            int numberY = contentStartY + i * (SLOT_SIZE + PADDING);
            
            System.out.println("[HotbarExpand] Checking hotbar " + (hotbarIdx + 1) + " at (" + numberX + "-" + (numberX + NUMBER_WIDTH) + ", " + numberY + "-" + (numberY + SLOT_SIZE) + "), mouse at (" + mouseX + ", " + mouseY + ")");
            
            if (mouseX >= numberX && mouseX <= numberX + NUMBER_WIDTH && 
                mouseY >= numberY && mouseY <= numberY + SLOT_SIZE) {
                // 点击了编号，切换快捷栏
                System.out.println("[HotbarExpand] CLICKED hotbar number: " + (hotbarIdx + 1));
                HotbarManager.setCurrentHotbarIndex(hotbarIdx);
                currentHotbarIndexCache = hotbarIdx; // 立即更新缓存
                
                // 播放按钮音效
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                
                System.out.println("[HotbarExpand] Switched to hotbar: " + (hotbarIdx + 1));
                event.setCanceled(true);
                return;
            }
        }
        
        // 检查是否点击了物品槽位
        int contentStartX = panelX + NUMBER_WIDTH + NUMBER_GAP + 4;
        for (int i = 0; i < visibleCount; i++) {
            int hotbarIdx = scrollOffset + i;
            int rowY = contentStartY + i * (SLOT_SIZE + PADDING);
            
            // 检查9个物品槽位
            for (int slotIdx = 0; slotIdx < 9; slotIdx++) {
                int slotX = contentStartX + slotIdx * SLOT_SIZE;
                
                if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE && 
                    mouseY >= rowY && mouseY <= rowY + SLOT_SIZE) {
                    // 点击了物品槽位
                    if (handleSlotClick(hotbarIdx, slotIdx, event.getButton())) {
                        event.setCanceled(true);
                    }
                    return;
                }
            }
            
            // 检查副手槽位
            int offhandX = contentStartX + 9 * SLOT_SIZE + 2;
            if (mouseX >= offhandX && mouseX <= offhandX + SLOT_SIZE && 
                mouseY >= rowY && mouseY <= rowY + SLOT_SIZE) {
                // 点击了副手槽位
                if (handleOffhandClick(hotbarIdx, event.getButton())) {
                    event.setCanceled(true);
                }
                return;
            }
        }
        
        // 如果鼠标在面板区域内但没有命中任何交互元素，取消事件（防止右键扔出物品）
        event.setCanceled(true);
        System.out.println("[HotbarExpand] Click in panel area, blocking event");
    }
    
    /**
     * 鼠标释放事件 - 结束拖动（使用Pre事件确保在事件传递到游戏之前处理）
     */
    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!isPanelVisible) return;
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        
        // 检查鼠标是否在面板区域内
        int contentStartY = panelY + 4;
        int scrollbarX = panelX + NUMBER_WIDTH + NUMBER_GAP + CONTENT_WIDTH + 4;
        int scrollbarHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING);
        int buttonX = scrollbarX + SCROLLBAR_WIDTH + BUTTON_GAP + 3;
        int panelRight = buttonX + BUTTON_SIZE + 4;
        int panelBottom = contentStartY + scrollbarHeight;
        boolean inPanelArea = mouseX >= panelX && mouseX <= panelRight && 
                              mouseY >= panelY && mouseY <= panelBottom;
        
        // 结束物品拖动
        if (isDraggingItems && event.getButton() == dragButton) {
            System.out.println("[HotbarExpand] Ended dragging, distributed to " + draggedSlots.size() + " slots");
            isDraggingItems = false;
            draggedItem = ItemStack.EMPTY;
            dragButton = -1;
            draggedSlots.clear();
            
            // 如果在面板区域内，取消事件防止丢出物品
            if (inPanelArea) {
                event.setCanceled(true);
            }
            return;
        }
        
        // 结束滚动条拖动
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            if (inPanelArea) {
                event.setCanceled(true);
            }
            return;
        }
        
        // 如果在面板区域内，取消事件防止右键丢出物品
        if (inPanelArea) {
            event.setCanceled(true);
            System.out.println("[HotbarExpand] Mouse release in panel area, blocking event");
        }
    }
    
    /**
     * 鼠标拖动事件 - 处理滚动条拖动和物品分发（复刻原版容器行为）
     */
    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!isPanelVisible) return;
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) {
            return;
        }
        
        // 处理滚动条拖动
        if (isDraggingScrollbar) {
            if (maxHotbars <= VISIBLE_HOTBARS) {
                isDraggingScrollbar = false;
                return;
            }
            
            double mouseY = event.getMouseY();
            int contentStartY = panelY + 4;
            int scrollbarHeight = VISIBLE_HOTBARS * (SLOT_SIZE + PADDING);
            
            // 计算滑块高度
            float ratio = (float) VISIBLE_HOTBARS / maxHotbars;
            int thumbHeight = Math.max(20, (int)(scrollbarHeight * ratio));
            int availableHeight = scrollbarHeight - thumbHeight;
            
            if (availableHeight <= 0) {
                isDraggingScrollbar = false;
                return;
            }
            
            // 计算鼠标拖动的距离对应的滚动偏移
            int maxScroll = maxHotbars - VISIBLE_HOTBARS;
            float dragRatio = (float)(mouseY - contentStartY - (thumbHeight / 2)) / availableHeight;
            scrollOffset = Math.min(maxScroll, Math.max(0, (int)(dragRatio * maxScroll)));
            
            System.out.println("[HotbarExpand] Dragging scrollbar: mouseY=" + mouseY + ", scrollOffset=" + scrollOffset);
            
            // 取消事件，防止其他处理程序干扰
            event.setCanceled(true);
            return;
        }
        
        // 处理物品拖动分发
        if (!isDraggingItems) return;
        
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        
        int contentStartY = panelY + 4;
        int contentStartX = panelX + NUMBER_WIDTH + NUMBER_GAP + 4;
        int visibleCount = Math.min(VISIBLE_HOTBARS, maxHotbars - scrollOffset);
        
        // 检查是否拖到了新的槽位
        for (int i = 0; i < visibleCount; i++) {
            int hotbarIdx = scrollOffset + i;
            int rowY = contentStartY + i * (SLOT_SIZE + PADDING);
            
            for (int slotIdx = 0; slotIdx < 9; slotIdx++) {
                int slotX = contentStartX + slotIdx * SLOT_SIZE;
                
                if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE && 
                    mouseY >= rowY && mouseY <= rowY + SLOT_SIZE) {
                    // 检查这个槽位是否已经被分发过
                    boolean alreadyDragged = false;
                    for (int[] slot : draggedSlots) {
                        if (slot[0] == hotbarIdx && slot[1] == slotIdx) {
                            alreadyDragged = true;
                            break;
                        }
                    }
                    
                    if (!alreadyDragged) {
                        // 分发物品到这个槽位（复刻原版容器行为）
                        handleDragDistribution(hotbarIdx, slotIdx);
                    }
                    return;
                }
            }
        }
    }
    
    /**
     * 处理拖动时分发物品 - 复刻原版容器行为
     * 左键拖动：平均分配物品到所有拖过的槽位
     * 右键拖动：每个槽位放1个物品
     */
    private static void handleDragDistribution(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        if (carriedItem.isEmpty()) {
            isDraggingItems = false;
            return;
        }
        
        ItemStack slotItem = getHotbarItem(hotbarIdx, slotIdx);
        
        // 记录这个槽位
        draggedSlots.add(new int[]{hotbarIdx, slotIdx});

        if (dragButton == 0) {
            // 左键拖动：立即处理每个槽位
            // 第1个槽位：放下所有物品
            // 第2个及以上槽位：平均分配到所有已拖动的槽位
            if (draggedSlots.size() == 1) {
                placeAllItems(hotbarIdx, slotIdx);
            } else {
                redistributeItemsEvenly();
            }
        } else if (dragButton == 1) {
            // 右键拖动：每个槽位放1个物品
            // 只能分发给空槽位或相同物品的槽位
            if (!slotItem.isEmpty() && !ItemStack.isSameItemSameComponents(slotItem, carriedItem)) {
                return;
            }
            // 如果槽位已满，不能分发
            if (!slotItem.isEmpty() && slotItem.getCount() >= slotItem.getMaxStackSize()) {
                return;
            }
            placeOneItem(hotbarIdx, slotIdx);
        }
    }

    /**
     * 在槽位放置所有物品（左键点击第一个槽位时的行为）
     */
    private static void placeAllItems(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        if (carriedItem.isEmpty()) {
            isDraggingItems = false;
            return;
        }

        ItemStack slotItem = getHotbarItem(hotbarIdx, slotIdx);

        // 如果槽位为空，直接放置所有物品
        if (slotItem.isEmpty()) {
            setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
            }
            minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
            isDraggingItems = false;
            System.out.println("[HotbarExpand] Left click placed all items to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        } else if (ItemStack.isSameItemSameComponents(slotItem, carriedItem)) {
            // 相同物品，尝试合并
            int maxStack = slotItem.getMaxStackSize();
            int canAdd = maxStack - slotItem.getCount();
            int toAdd = Math.min(canAdd, carriedItem.getCount());

            if (toAdd > 0) {
                ItemStack newSlot = slotItem.copy();
                newSlot.grow(toAdd);
                setHotbarItem(hotbarIdx, slotIdx, newSlot);
                if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                    minecraft.player.getInventory().setItem(slotIdx, newSlot.copy());
                }

                ItemStack newCarried = carriedItem.copy();
                newCarried.shrink(toAdd);
                if (newCarried.isEmpty()) {
                    newCarried = ItemStack.EMPTY;
                    isDraggingItems = false;
                }
                minecraft.player.containerMenu.setCarried(newCarried);
                System.out.println("[HotbarExpand] Left click merged " + toAdd + " items to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
            } else {
                // 槽位已满，交换
                setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
                if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                    minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
                }
                minecraft.player.containerMenu.setCarried(slotItem.copy());
                isDraggingItems = false;
                System.out.println("[HotbarExpand] Left click swapped items at hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
            }
        } else {
            // 不同物品，交换
            setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
            }
            minecraft.player.containerMenu.setCarried(slotItem.copy());
            isDraggingItems = false;
            System.out.println("[HotbarExpand] Left click swapped items at hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        }
    }
    
    /**
     * 平均重新分配物品到所有拖过的槽位（左键拖动行为）
     * 原版容器行为：将手持物品平均分配到所有拖过的槽位
     */
    private static void redistributeItemsEvenly() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        if (carriedItem.isEmpty()) {
            isDraggingItems = false;
            return;
        }
        
        // 原版容器逻辑：总物品数 = 原始物品总数（保持不变）
        // 每次添加新槽位时，重新计算每个槽位应该分配的数量
        int totalItems = draggedItem.getCount();
        int slotCount = draggedSlots.size();
        int baseAmount = totalItems / slotCount;
        int remainder = totalItems % slotCount;
        
        // 更新所有已分发槽位的物品数量
        for (int i = 0; i < draggedSlots.size(); i++) {
            int[] slot = draggedSlots.get(i);
            int targetHotbarIdx = slot[0];
            int targetSlotIdx = slot[1];
            
            // 前remainder个槽位多分配1个
            int amount = baseAmount + (i < remainder ? 1 : 0);
            
            ItemStack newStack = draggedItem.copy();
            newStack.setCount(amount);
            setHotbarItem(targetHotbarIdx, targetSlotIdx, newStack);
            
            // 如果修改的是当前快捷栏，同步到玩家背包
            if (targetHotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(targetSlotIdx, newStack.copy());
            }
        }
        
        // 计算实际分配的物品总数
        int distributedCount = 0;
        for (int i = 0; i < slotCount; i++) {
            distributedCount += baseAmount + (i < remainder ? 1 : 0);
        }
        
        // 更新手持物品数量（剩余的物品）
        int remainingItems = totalItems - distributedCount;
        ItemStack newCarried;
        if (remainingItems > 0) {
            newCarried = draggedItem.copy();
            newCarried.setCount(remainingItems);
        } else {
            newCarried = ItemStack.EMPTY;
            isDraggingItems = false;
        }
        minecraft.player.containerMenu.setCarried(newCarried);
        
        System.out.println("[HotbarExpand] Left drag redistributed to " + draggedSlots.size() + " slots, base=" + baseAmount + ", remainder=" + remainder + ", remaining=" + remainingItems);
    }
    
    /**
     * 在当前槽位放1个物品（右键拖动行为）
     */
    private static void placeOneItem(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        
        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        if (carriedItem.isEmpty()) {
            isDraggingItems = false;
            return;
        }
        
        ItemStack slotItem = getHotbarItem(hotbarIdx, slotIdx);
        
        // 只放一个物品到当前槽位
        ItemStack newSlotItem = slotItem.copy();
        if (newSlotItem.isEmpty()) {
            newSlotItem = carriedItem.copy();
            newSlotItem.setCount(1);
        } else {
            newSlotItem.grow(1);
        }
        setHotbarItem(hotbarIdx, slotIdx, newSlotItem);
        
        // 如果修改的是当前快捷栏，同步到玩家背包
        if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
            minecraft.player.getInventory().setItem(slotIdx, newSlotItem.copy());
        }
        
        // 减少手持物品数量
        ItemStack newCarried = carriedItem.copy();
        newCarried.shrink(1);
        if (newCarried.isEmpty()) {
            newCarried = ItemStack.EMPTY;
            isDraggingItems = false;
        }
        minecraft.player.containerMenu.setCarried(newCarried);
        
        System.out.println("[HotbarExpand] Right drag placed 1 item to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
    }
    
    /**
     * 切换面板显示/隐藏
     */
    private static void togglePanelVisibility() {
        isPanelVisible = !isPanelVisible;
        
        // 播放音效
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        
        System.out.println("[HotbarExpand] Panel visibility toggled: " + isPanelVisible);
    }
    
    /**
     * 添加新的快捷栏
     */
    private static void addHotbar() {
        int hotbarCount = HotbarManager.getHotbarCount();
        if (hotbarCount < 27) { // 最多27个快捷栏
            // 使用HotbarManager添加快捷栏
            HotbarManager.addHotbar();
            // 从HotbarManager获取最新的数量
            maxHotbars = HotbarManager.getHotbarCount();
            
            // 播放音效
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
            
            System.out.println("[HotbarExpand] Added hotbar, total: " + maxHotbars);
        }
    }
    
    /**
     * 删除当前选中的快捷栏，删除后重新排序
     */
    private static void removeCurrentHotbar() {
        int hotbarCount = HotbarManager.getHotbarCount();
        int currentIdx = HotbarManager.getCurrentHotbarIndex();
        System.out.println("[HotbarExpand] Remove requested. maxHotbars=" + maxHotbars + ", hotbarCount=" + hotbarCount + ", currentIdx=" + currentIdx + ", scrollOffset=" + scrollOffset);
        
        // 使用HotbarManager的实际数量来判断
        if (hotbarCount > 1) { // 至少保留1个快捷栏
            System.out.println("[HotbarExpand] Current index: " + currentIdx);
            
            // 确保索引在有效范围内
            if (currentIdx >= 0 && currentIdx < hotbarCount) {
                // 使用HotbarManager删除快捷栏
                HotbarManager.removeHotbar(currentIdx);
                // 从HotbarManager获取最新的数量
                maxHotbars = HotbarManager.getHotbarCount();
                currentHotbarIndexCache = HotbarManager.getCurrentHotbarIndex();
                
                // 调整滚动偏移 - 确保不会超出范围
                int maxScroll = Math.max(0, maxHotbars - VISIBLE_HOTBARS);
                if (scrollOffset > maxScroll) {
                    scrollOffset = maxScroll;
                }
                // 如果当前选中的快捷栏在滚动区域上方，调整滚动偏移
                if (currentHotbarIndexCache < scrollOffset) {
                    scrollOffset = currentHotbarIndexCache;
                }
                // 如果当前选中的快捷栏在滚动区域下方，调整滚动偏移
                if (currentHotbarIndexCache >= scrollOffset + VISIBLE_HOTBARS) {
                    scrollOffset = Math.max(0, currentHotbarIndexCache - VISIBLE_HOTBARS + 1);
                }
                
                // 播放音效
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f));
                
                System.out.println("[HotbarExpand] Removed hotbar " + (currentIdx + 1) + ", total: " + maxHotbars + ", new current: " + (HotbarManager.getCurrentHotbarIndex() + 1) + ", new scrollOffset: " + scrollOffset);
            } else {
                System.out.println("[HotbarExpand] Error: Invalid index " + currentIdx + ", hotbarCount=" + hotbarCount);
            }
        } else {
            System.out.println("[HotbarExpand] Cannot remove, minimum 1 hotbar required (current: " + hotbarCount + ")");
        }
    }
    
    /**
     * 将当前选中的快捷栏向上移动
     */
    private static void moveCurrentHotbarUp() {
        int currentIdx = HotbarManager.getCurrentHotbarIndex();
        if (currentIdx > 0) {
            // 交换当前快捷栏和上一个快捷栏的数据
            HotbarManager.swapHotbars(currentIdx, currentIdx - 1);
            // 更新当前选中的索引
            HotbarManager.setCurrentHotbarIndex(currentIdx - 1);
            currentHotbarIndexCache = currentIdx - 1;
            
            // 调整滚动偏移
            if (currentHotbarIndexCache < scrollOffset) {
                scrollOffset = currentHotbarIndexCache;
            }
            
            // 播放音效
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1f));
            
            System.out.println("[HotbarExpand] Moved hotbar " + (currentIdx + 1) + " up to position " + (currentHotbarIndexCache + 1));
        }
    }
    
    /**
     * 将当前选中的快捷栏向下移动
     */
    private static void moveCurrentHotbarDown() {
        int currentIdx = HotbarManager.getCurrentHotbarIndex();
        int hotbarCount = HotbarManager.getHotbarCount();
        if (currentIdx < hotbarCount - 1) {
            // 交换当前快捷栏和下一个快捷栏的数据
            HotbarManager.swapHotbars(currentIdx, currentIdx + 1);
            // 更新当前选中的索引
            HotbarManager.setCurrentHotbarIndex(currentIdx + 1);
            currentHotbarIndexCache = currentIdx + 1;
            
            // 调整滚动偏移
            if (currentHotbarIndexCache >= scrollOffset + VISIBLE_HOTBARS) {
                scrollOffset = currentHotbarIndexCache - VISIBLE_HOTBARS + 1;
            }
            
            // 播放音效
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9f));
            
            System.out.println("[HotbarExpand] Moved hotbar " + (currentIdx + 1) + " down to position " + (currentHotbarIndexCache + 1));
        }
    }
    
    private static boolean handleSlotClick(int hotbarIdx, int slotIdx, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        
        // 获取玩家光标上的物品和槽位物品
        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        // 对于当前快捷栏，直接从玩家库存获取物品（更准确）
        // 对于其他快捷栏，从HotbarManager获取
        ItemStack slotItem;
        int currentHotbar = HotbarManager.getCurrentHotbarIndex();
        if (hotbarIdx == currentHotbar) {
            slotItem = minecraft.player.getInventory().getItem(slotIdx);
        } else {
            slotItem = getHotbarItem(hotbarIdx, slotIdx);
        }
        
        // 中键/拾取键处理：在背包界面恢复创造模式下复制整组行为（其他情况保留原版）
        if (isPickItemButton(button)) {
            if (minecraft.player.hasInfiniteMaterials() && !slotItem.isEmpty()) {
                ItemStack copy = slotItem.copy();
                copy.setCount(copy.getMaxStackSize());
                minecraft.player.containerMenu.setCarried(copy);
                System.out.println("[HotbarExpand] Inventory middle-click copied item in creative mode");
                return true;
            }
            return false;
        }
        
        // 处理左键和右键点击
        if (button == 0 || button == 1) {
            // 如果手持物品，启动拖动模式
            if (!carriedItem.isEmpty()) {
                isDraggingItems = true;
                draggedItem = carriedItem.copy();
                dragButton = button;
                draggedSlots.clear();
                draggedSlots.add(new int[]{hotbarIdx, slotIdx});

                // 立即处理第一个槽位
                if (button == 0) {
                    placeAllItems(hotbarIdx, slotIdx);
                } else {
                    placeOneItem(hotbarIdx, slotIdx);
                }
            } else {
                // 空手点击，直接处理
                if (button == 0) {
                    handleLeftClick(hotbarIdx, slotIdx, carriedItem, slotItem);
                } else {
                    handleRightClick(hotbarIdx, slotIdx, carriedItem, slotItem);
                }
            }
        }
        return true;
    }

    /**
     * 处理左键点击
     */
    private static void handleLeftClick(int hotbarIdx, int slotIdx, ItemStack carriedItem, ItemStack slotItem) {
        Minecraft minecraft = Minecraft.getInstance();

        if (carriedItem.isEmpty() && !slotItem.isEmpty()) {
            // 空手点击有物品的槽位：拿起所有物品
            minecraft.player.containerMenu.setCarried(slotItem.copy());
            setHotbarItem(hotbarIdx, slotIdx, ItemStack.EMPTY);
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, ItemStack.EMPTY);
            }
            System.out.println("[HotbarExpand] Left click picked up all items from hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        } else if (!carriedItem.isEmpty() && slotItem.isEmpty()) {
            // 手持物品点击空槽位：放下所有物品
            setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
            }
            minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
            System.out.println("[HotbarExpand] Left click placed all items to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        } else if (!carriedItem.isEmpty() && !slotItem.isEmpty()) {
            // 手持物品点击有物品的槽位
            if (ItemStack.isSameItemSameComponents(carriedItem, slotItem)) {
                // 相同物品：尝试合并
                int maxStack = slotItem.getMaxStackSize();
                int canAdd = maxStack - slotItem.getCount();
                int toAdd = Math.min(canAdd, carriedItem.getCount());

                if (toAdd > 0) {
                    // 可以合并一部分
                    ItemStack newSlot = slotItem.copy();
                    newSlot.grow(toAdd);
                    setHotbarItem(hotbarIdx, slotIdx, newSlot);
                    if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                        minecraft.player.getInventory().setItem(slotIdx, newSlot.copy());
                    }

                    ItemStack newCarried = carriedItem.copy();
                    newCarried.shrink(toAdd);
                    if (newCarried.isEmpty()) {
                        newCarried = ItemStack.EMPTY;
                    }
                    minecraft.player.containerMenu.setCarried(newCarried);
                    System.out.println("[HotbarExpand] Left click merged " + toAdd + " items to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
                } else {
                    // 槽位已满，交换物品
                    setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
                    if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                        minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
                    }
                    minecraft.player.containerMenu.setCarried(slotItem.copy());
                    System.out.println("[HotbarExpand] Left click swapped items (slot full) at hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
                }
            } else {
                // 不同物品：交换
                setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
                if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                    minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
                }
                minecraft.player.containerMenu.setCarried(slotItem.copy());
                System.out.println("[HotbarExpand] Left click swapped items (different) at hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
            }
        }
    }

    /**
     * 处理右键点击
     */
    private static void handleRightClick(int hotbarIdx, int slotIdx, ItemStack carriedItem, ItemStack slotItem) {
        Minecraft minecraft = Minecraft.getInstance();

        if (carriedItem.isEmpty() && !slotItem.isEmpty()) {
            // 空手点击有物品的槽位：拿起一半
            int count = (slotItem.getCount() + 1) / 2;
            ItemStack resultCarried = slotItem.copy();
            resultCarried.setCount(count);
            ItemStack resultSlot = slotItem.copy();
            resultSlot.shrink(count);
            if (resultSlot.isEmpty()) {
                resultSlot = ItemStack.EMPTY;
            }

            minecraft.player.containerMenu.setCarried(resultCarried);
            setHotbarItem(hotbarIdx, slotIdx, resultSlot);
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, resultSlot.copy());
            }
            System.out.println("[HotbarExpand] Right click picked up half from hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        } else if (!carriedItem.isEmpty() && slotItem.isEmpty()) {
            // 手持物品点击空槽位：放下1个
            ItemStack resultSlot = carriedItem.copy();
            resultSlot.setCount(1);
            setHotbarItem(hotbarIdx, slotIdx, resultSlot);
            if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                minecraft.player.getInventory().setItem(slotIdx, resultSlot.copy());
            }

            ItemStack resultCarried = carriedItem.copy();
            resultCarried.shrink(1);
            if (resultCarried.isEmpty()) {
                resultCarried = ItemStack.EMPTY;
            }
            minecraft.player.containerMenu.setCarried(resultCarried);
            System.out.println("[HotbarExpand] Right click placed 1 item to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
        } else if (!carriedItem.isEmpty() && !slotItem.isEmpty()) {
            // 手持物品点击有物品的槽位
            if (ItemStack.isSameItemSameComponents(carriedItem, slotItem)) {
                // 相同物品：尝试添加1个
                if (slotItem.getCount() < slotItem.getMaxStackSize()) {
                    ItemStack newSlot = slotItem.copy();
                    newSlot.grow(1);
                    setHotbarItem(hotbarIdx, slotIdx, newSlot);
                    if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                        minecraft.player.getInventory().setItem(slotIdx, newSlot.copy());
                    }

                    ItemStack newCarried = carriedItem.copy();
                    newCarried.shrink(1);
                    if (newCarried.isEmpty()) {
                        newCarried = ItemStack.EMPTY;
                    }
                    minecraft.player.containerMenu.setCarried(newCarried);
                    System.out.println("[HotbarExpand] Right click added 1 item to hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
                }
                // 槽位已满，不做任何事
            } else {
                // 不同物品：交换
                setHotbarItem(hotbarIdx, slotIdx, carriedItem.copy());
                if (hotbarIdx == HotbarManager.getCurrentHotbarIndex()) {
                    minecraft.player.getInventory().setItem(slotIdx, carriedItem.copy());
                }
                minecraft.player.containerMenu.setCarried(slotItem.copy());
                System.out.println("[HotbarExpand] Right click swapped items at hotbar=" + (hotbarIdx + 1) + ", slot=" + (slotIdx + 1));
            }
        }
    }
    
    private static boolean handleOffhandClick(int hotbarIdx, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        
        ItemStack carriedItem = minecraft.player.containerMenu.getCarried();
        ItemStack offhandItem = getOffhandItem(hotbarIdx);
        
        // 中键处理：在背包界面恢复创造模式下复制副手物品的行为
        if (button == 2) {
            if (minecraft.player.hasInfiniteMaterials() && !offhandItem.isEmpty()) {
                ItemStack copy = offhandItem.copy();
                copy.setCount(copy.getMaxStackSize());
                minecraft.player.containerMenu.setCarried(copy);
                System.out.println("[HotbarExpand] Inventory middle-click copied offhand item in creative mode");
                return true;
            }
            return false;
        }
        
        ItemStack resultCarried = carriedItem.copy();
        ItemStack resultOffhand = offhandItem.copy();
        
        if (button == 0) { // 左键
            if (carriedItem.isEmpty() && !offhandItem.isEmpty()) {
                // 拿起副手物品
                resultCarried = offhandItem.copy();
                resultOffhand = ItemStack.EMPTY;
            } else if (!carriedItem.isEmpty() && offhandItem.isEmpty()) {
                // 放下物品到副手
                resultOffhand = carriedItem.copy();
                resultCarried = ItemStack.EMPTY;
            } else if (!carriedItem.isEmpty() && !offhandItem.isEmpty()) {
                // 交换物品
                resultCarried = offhandItem.copy();
                resultOffhand = carriedItem.copy();
            } else {
                // 都为空，不做任何事
                return true;
            }
        } else if (button == 1) { // 右键
            if (carriedItem.isEmpty() && !offhandItem.isEmpty()) {
                // 拿起一半
                int count = (offhandItem.getCount() + 1) / 2;
                resultCarried = offhandItem.copy();
                resultCarried.setCount(count);
                resultOffhand = offhandItem.copy();
                resultOffhand.shrink(count);
                if (resultOffhand.isEmpty()) {
                    resultOffhand = ItemStack.EMPTY;
                }
            } else if (!carriedItem.isEmpty() && offhandItem.isEmpty()) {
                // 放下1个
                resultOffhand = carriedItem.copy();
                resultOffhand.setCount(1);
                resultCarried = carriedItem.copy();
                resultCarried.shrink(1);
                if (resultCarried.isEmpty()) {
                    resultCarried = ItemStack.EMPTY;
                }
            } else if (!carriedItem.isEmpty() && !offhandItem.isEmpty()) {
                // 副手有物品，手上有物品
                if (ItemStack.isSameItemSameComponents(carriedItem, offhandItem)) {
                    // 相同物品，尝试添加1个
                    if (offhandItem.getCount() < offhandItem.getMaxStackSize()) {
                        resultOffhand = offhandItem.copy();
                        resultOffhand.grow(1);
                        resultCarried = carriedItem.copy();
                        resultCarried.shrink(1);
                        if (resultCarried.isEmpty()) {
                            resultCarried = ItemStack.EMPTY;
                        }
                    } else {
                        // 副手槽已满，不做任何事
                        return true;
                    }
                } else {
                    // 不同物品，交换
                    resultCarried = offhandItem.copy();
                    resultOffhand = carriedItem.copy();
                }
            } else {
                // 都为空，不做任何事
                return true;
            }
        } else {
            return true;
        }
        
        // 应用更改
        minecraft.player.containerMenu.setCarried(resultCarried);
        setOffhandItem(hotbarIdx, resultOffhand);

        System.out.println("[HotbarExpand] Offhand clicked: hotbar=" + (hotbarIdx + 1) + ", button=" + button);
        return true;
    }

    /**
     * 遍历所有快捷栏寻找相同物品并选中
     * @param targetItem 目标物品
     * @return 是否找到并选中
     */
    private static boolean selectHotbarSlot(int hotbarIdx, int slotIdx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        int currentHotbar = HotbarManager.getCurrentHotbarIndex();
        if (hotbarIdx != currentHotbar) {
            if (hotbarIdx < 0 || hotbarIdx >= HotbarManager.getHotbarCount()) {
                return false;
            }
            HotbarManager.setCurrentHotbarIndex(hotbarIdx);
            currentHotbarIndexCache = HotbarManager.getCurrentHotbarIndex();
        }

        minecraft.player.getInventory().selected = slotIdx;
        if (minecraft.player.containerMenu != null) {
            minecraft.player.containerMenu.broadcastChanges();
        }
        return true;
    }

    private static boolean findAndSelectItem(ItemStack targetItem) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;

        int hotbarCount = HotbarManager.getHotbarCount();
        int currentHotbar = HotbarManager.getCurrentHotbarIndex();

        System.out.println("[HotbarExpand] findAndSelectItem: target=" + targetItem.getItem() + ", hotbarCount=" + hotbarCount + ", current=" + currentHotbar);

        // 首先在当前快捷栏中查找（使用更宽松的物品比较）
        // 直接从玩家库存获取当前快捷栏的数据（更准确）
        System.out.println("[HotbarExpand] Searching current hotbar " + currentHotbar + " in player inventory...");
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = minecraft.player.getInventory().getItem(slot);
            System.out.println("[HotbarExpand]   Slot " + slot + ": " + (item.isEmpty() ? "empty" : item.getItem()));
            if (isSameItem(item, targetItem)) {
                selectHotbarSlot(currentHotbar, slot);
                System.out.println("[HotbarExpand] Middle-click: found item in current hotbar " + (currentHotbar + 1) + ", slot " + (slot + 1));
                return true;
            }
        }

        // 如果玩家库存中没有，检查HotbarManager中的当前快捷栏数据
        System.out.println("[HotbarExpand] Searching current hotbar " + currentHotbar + " in HotbarManager...");
        List<ItemStack> currentHotbarItems = HotbarManager.getHotbar(currentHotbar);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = currentHotbarItems.get(slot);
            System.out.println("[HotbarExpand]   Slot " + slot + ": " + (item.isEmpty() ? "empty" : item.getItem()));
            if (isSameItem(item, targetItem)) {
                selectHotbarSlot(currentHotbar, slot);
                System.out.println("[HotbarExpand] Middle-click: found item in current hotbar " + (currentHotbar + 1) + ", slot " + (slot + 1));
                System.out.println("[HotbarExpand] Set selected to: " + minecraft.player.getInventory().selected);
                return true;
            }
        }

        // 如果当前快捷栏没有，遍历其他快捷栏
        for (int hotbarIdx = 0; hotbarIdx < hotbarCount; hotbarIdx++) {
            if (hotbarIdx == currentHotbar) continue;

            List<ItemStack> hotbarItems = HotbarManager.getHotbar(hotbarIdx);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = hotbarItems.get(slot);
                if (isSameItem(item, targetItem)) {
                    selectHotbarSlot(hotbarIdx, slot);
                    System.out.println("[HotbarExpand] Middle-click: switched to hotbar " + (hotbarIdx + 1) + ", slot " + (slot + 1));
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查两个物品是否是同一种物品（使用更宽松的比较）
     * 注意：ItemStack.isSameItem()使用==比较，在某些情况下不可靠
     * 这里使用ResourceLocation比较
     */
    private static boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1.isEmpty() || item2.isEmpty()) return false;
        // 使用ResourceLocation比较
        var item1Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item1.getItem());
        var item2Key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item2.getItem());
        // 直接比较namespace和path
        return item1Key.getNamespace().equals(item2Key.getNamespace()) && 
               item1Key.getPath().equals(item2Key.getPath());
    }

    /**
     * 获取准星指向的方块物品（用于中键选取方块）
     */
    private static ItemStack getPickBlockItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return ItemStack.EMPTY;
        
        // 获取准星指向的方块
        var hitResult = minecraft.hitResult;
        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var blockState = minecraft.level.getBlockState(blockPos);
            var block = blockState.getBlock();
            
            // 获取方块的物品形式
            ItemStack pickBlock = block.getCloneItemStack(minecraft.level, blockPos, blockState);
            if (!pickBlock.isEmpty()) {
                return pickBlock;
            }
            
            // 如果getCloneItemStack返回空，尝试从方块获取物品
            var item = block.asItem();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }

        // 如果当前hitResult不是方块，继续使用玩家视线进行拾取射线计算
        if (minecraft.player != null) {
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
        }
        
        return ItemStack.EMPTY;
    }

    private static boolean isPickItemButton(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return button == 2;
        }
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
        return minecraft.options.keyPickItem.isActiveAndMatches(mouseKey) || button == 2;
    }

    /**
     * 获取当前滚动偏移（供ExpandedHotbarOverlay使用）
     */
    public static int getScrollOffset() {
        return scrollOffset;
    }
    
    /**
     * 设置滚动偏移（供ExpandedHotbarOverlay使用）
     */
    public static void setScrollOffset(int offset) {
        int maxHotbars = HotbarManager.getHotbarCount();
        int maxScroll = Math.max(0, maxHotbars - VISIBLE_HOTBARS);
        scrollOffset = Math.min(maxScroll, Math.max(0, offset));
    }
    
    /**
     * 获取可见快捷栏数量
     */
    public static int getVisibleHotbars() {
        return VISIBLE_HOTBARS;
    }
}
