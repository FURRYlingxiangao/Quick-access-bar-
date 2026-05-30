package com.example.hotbarexpand.client.gui;

import com.example.hotbarexpand.HotbarExpandMod;
import com.example.hotbarexpand.client.HotbarConfig;
import com.example.hotbarexpand.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class HotbarSettingsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 140;
    private static final int CONTENT_PADDING = 20;
    private static final int CATEGORY_HEIGHT = 24;

    private final Screen lastScreen;

    // 分类
    private enum Category {
        GENERAL("常规"),
        DISPLAY("显示"),
        BEHAVIOR("行为"),
        HOTKEYS("快捷键"),
        ABOUT("关于");

        final String name;

        Category(String name) {
            this.name = name;
        }
    }

    private Category currentCategory = Category.GENERAL;
    private List<CategoryButton> categoryButtons = new ArrayList<>();

    // 配置项控件
    private List<AbstractWidget> contentWidgets = new ArrayList<>();

    // 临时值
    private int maxVisibleTemp;
    private boolean animationEnabledTemp;
    private boolean soundEnabledTemp;
    private boolean indicatorEnabledTemp;
    private boolean autoSaveTemp;
    private boolean scrollLoopTemp;
    private float animationSpeedTemp;
    private int expandDelayTemp;
    private int indicatorOpacityTemp;
    private HotbarConfig.DisplayPosition displayPositionTemp;
    private HotbarConfig.ExpandMode expandModeTemp;
    private HotbarConfig.TransparencyMode transparencyModeTemp;
    private int autoHideDelayTemp;
    private int transparencyTemp;
    private float alphaTransitionSpeedTemp;

    // 动画演示相关
    private float demoExpandProgress = 0f;
    private boolean demoExpanding = true;
    private long lastDemoTime = 0;
    private int demoCurrentHotbar = 0;

    // 演示区域尺寸（根据内容动态计算）
    private int demoActualWidth = 200;
    private int demoActualHeight = 140;

    public HotbarSettingsScreen(Screen lastScreen) {
        super(Component.literal("快捷栏拓展设置"));
        this.lastScreen = lastScreen;
        this.maxVisibleTemp = HotbarConfig.getMaxVisibleHotbars();
        this.animationEnabledTemp = HotbarConfig.isAnimationEnabled();
        this.soundEnabledTemp = HotbarConfig.isSoundEnabled();
        this.indicatorEnabledTemp = HotbarConfig.isIndicatorEnabled();
        this.autoSaveTemp = HotbarConfig.isAutoSaveEnabled();
        this.scrollLoopTemp = HotbarConfig.isScrollLoopEnabled();
        this.animationSpeedTemp = HotbarConfig.getAnimationSpeed();
        this.expandDelayTemp = HotbarConfig.getExpandDelay();
        this.indicatorOpacityTemp = HotbarConfig.getIndicatorOpacity();
        this.displayPositionTemp = HotbarConfig.getDisplayPosition();
        this.expandModeTemp = HotbarConfig.getExpandMode();
        this.transparencyModeTemp = HotbarConfig.getTransparencyMode();
        this.autoHideDelayTemp = HotbarConfig.getAutoHideDelay();
        this.transparencyTemp = HotbarConfig.getTransparency();
        this.alphaTransitionSpeedTemp = HotbarConfig.getAlphaTransitionSpeed();
    }

    @Override
    protected void init() {
        categoryButtons.clear();
        contentWidgets.clear();
        this.clearWidgets();

        int sidebarX = 10;
        int startY = 40;

        // 创建分类按钮
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            CategoryButton button = new CategoryButton(sidebarX, startY + i * (CATEGORY_HEIGHT + 4), SIDEBAR_WIDTH - 20, CATEGORY_HEIGHT, category);
            categoryButtons.add(button);
            this.addRenderableWidget(button);
        }

        // 创建底部按钮
        int buttonWidth = 100;
        int buttonHeight = 20;
        int bottomY = this.height - 30;

        // 保存并关闭按钮
        this.addRenderableWidget(Button.builder(Component.literal("保存并关闭"), button -> {
            saveSettings();
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width - buttonWidth - 10, bottomY, buttonWidth, buttonHeight).build());

        // 取消按钮
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> {
            this.minecraft.setScreen(this.lastScreen);
        }).bounds(this.width - buttonWidth * 2 - 25, bottomY, buttonWidth, buttonHeight).build());

        // 恢复默认按钮
        this.addRenderableWidget(Button.builder(Component.literal("恢复默认"), button -> {
            resetToDefaults();
        }).bounds(this.width - buttonWidth * 3 - 40, bottomY, buttonWidth, buttonHeight).build());

        // 初始化当前分类的内容
        initCategoryContent();
    }

    private void initCategoryContent() {
        // 清除旧的内容控件
        for (AbstractWidget widget : contentWidgets) {
            this.removeWidget(widget);
        }
        contentWidgets.clear();

        int contentX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int startY = 50;
        int spacing = 32;
        int currentY = startY;

        switch (currentCategory) {
            case GENERAL -> {
                // 布局模式
                addLabel(contentX, currentY, "快捷栏布局");
                currentY += 16;
                Button layoutButton = Button.builder(Component.literal(HotbarConfig.getLayout().displayName), button -> {
                    cycleLayoutMode();
                    button.setMessage(Component.literal(HotbarConfig.getLayout().displayName));
                }).bounds(contentX, currentY, 120, 20).build();
                contentWidgets.add(layoutButton);
                this.addRenderableWidget(layoutButton);
                currentY += spacing + 10;

                // 最大显示数量
                addLabel(contentX, currentY, "列表最大显示数量");
                currentY += 16;
                SliderWidget maxVisibleSlider = new SliderWidget(contentX, currentY, 200, 18,
                        Component.empty(), maxVisibleTemp, HotbarConfig.getMinVisibleHotbars(), HotbarConfig.getMaxVisibleHotbarsLimit()) {
                    @Override
                    protected void applyValue() {
                        maxVisibleTemp = (int) (this.value * (max - min) + min);
                    }
                };
                contentWidgets.add(maxVisibleSlider);
                this.addRenderableWidget(maxVisibleSlider);
                currentY += spacing;

                // 警告
                if (maxVisibleTemp > 9) {
                    addWarning(contentX, currentY, "⚠ Alt+滚轮仅覆盖前9个快捷栏");
                }
            }
            case DISPLAY -> {
                // 显示位置
                addLabel(contentX, currentY, "显示位置");
                currentY += 16;
                Button positionButton = Button.builder(Component.literal(displayPositionTemp.displayName), button -> {
                    cycleDisplayPosition();
                    button.setMessage(Component.literal(displayPositionTemp.displayName));
                }).bounds(contentX, currentY, 120, 20).build();
                contentWidgets.add(positionButton);
                this.addRenderableWidget(positionButton);
                currentY += spacing + 5;

                // 展开模式
                addLabel(contentX, currentY, "展开模式");
                currentY += 16;
                Button expandModeButton = Button.builder(Component.literal(expandModeTemp.displayName), button -> {
                    cycleExpandMode();
                    button.setMessage(Component.literal(expandModeTemp.displayName));
                    // 刷新界面以更新相关控件
                    initCategoryContent();
                }).bounds(contentX, currentY, 120, 20).build();
                contentWidgets.add(expandModeButton);
                this.addRenderableWidget(expandModeButton);
                currentY += spacing + 5;

                // 自动关闭延迟（仅在自动关闭模式下显示）
                if (expandModeTemp == HotbarConfig.ExpandMode.AUTO_HIDE) {
                    addLabel(contentX, currentY, "自动关闭延迟 (毫秒)");
                    currentY += 16;
                    SliderWidget autoHideSlider = new SliderWidget(contentX, currentY, 200, 18,
                            Component.empty(), autoHideDelayTemp, HotbarConfig.getMinAutoHideDelay(), HotbarConfig.getMaxAutoHideDelay()) {
                        @Override
                        protected void applyValue() {
                            autoHideDelayTemp = (int) (this.value * (max - min) + min);
                        }
                    };
                    contentWidgets.add(autoHideSlider);
                    this.addRenderableWidget(autoHideSlider);
                    currentY += spacing;
                }

                // 透明模式
                addLabel(contentX, currentY, "透明模式");
                currentY += 16;
                Button transparencyModeButton = Button.builder(Component.literal(transparencyModeTemp.displayName), button -> {
                    cycleTransparencyMode();
                    button.setMessage(Component.literal(transparencyModeTemp.displayName));
                    initCategoryContent();
                }).bounds(contentX, currentY, 120, 20).build();
                contentWidgets.add(transparencyModeButton);
                this.addRenderableWidget(transparencyModeButton);
                currentY += spacing + 5;

                // 透明度（仅在半透明模式下显示）
                if (transparencyModeTemp != HotbarConfig.TransparencyMode.ALWAYS_OPAQUE) {
                    addLabel(contentX, currentY, "透明度 (%)");
                    currentY += 16;
                    SliderWidget transparencySlider = new SliderWidget(contentX, currentY, 200, 18,
                            Component.empty(), transparencyTemp, HotbarConfig.getMinTransparency(), HotbarConfig.getMaxTransparency()) {
                        @Override
                        protected void applyValue() {
                            transparencyTemp = (int) (this.value * (max - min) + min);
                        }
                    };
                    contentWidgets.add(transparencySlider);
                    this.addRenderableWidget(transparencySlider);
                    currentY += spacing;
                }

                // 展开动画开关
                addToggleButton(contentX, currentY, "展开动画", animationEnabledTemp, newValue -> {
                    animationEnabledTemp = newValue;
                });
                currentY += spacing;

                // 动画速度滑块
                addLabel(contentX, currentY, "动画速度");
                currentY += 16;
                FloatSliderWidget speedSlider = new FloatSliderWidget(contentX, currentY, 200, 18,
                        Component.empty(), animationSpeedTemp, HotbarConfig.getMinAnimationSpeed(), HotbarConfig.getMaxAnimationSpeed()) {
                    @Override
                    protected void applyValue() {
                        animationSpeedTemp = (float) (this.value * (max - min) + min);
                    }
                };
                contentWidgets.add(speedSlider);
                this.addRenderableWidget(speedSlider);
                currentY += spacing;

                // 展开延迟滑块
                addLabel(contentX, currentY, "展开延迟 (毫秒)");
                currentY += 16;
                SliderWidget delaySlider = new SliderWidget(contentX, currentY, 200, 18,
                        Component.empty(), expandDelayTemp, HotbarConfig.getMinExpandDelay(), HotbarConfig.getMaxExpandDelay()) {
                    @Override
                    protected void applyValue() {
                        expandDelayTemp = (int) (this.value * (max - min) + min);
                    }
                };
                contentWidgets.add(delaySlider);
                this.addRenderableWidget(delaySlider);
                currentY += spacing;

                // 位置指示器开关
                addToggleButton(contentX, currentY, "位置指示器", indicatorEnabledTemp, newValue -> {
                    indicatorEnabledTemp = newValue;
                });
                currentY += spacing;

                // 指示器透明度滑块
                addLabel(contentX, currentY, "指示器透明度");
                currentY += 16;
                SliderWidget opacitySlider = new SliderWidget(contentX, currentY, 200, 18,
                        Component.empty(), indicatorOpacityTemp, HotbarConfig.getMinIndicatorOpacity(), HotbarConfig.getMaxIndicatorOpacity()) {
                    @Override
                    protected void applyValue() {
                        indicatorOpacityTemp = (int) (this.value * (max - min) + min);
                    }
                };
                contentWidgets.add(opacitySlider);
                this.addRenderableWidget(opacitySlider);
                currentY += spacing;

                // 切换音效开关
                addToggleButton(contentX, currentY, "切换音效", soundEnabledTemp, newValue -> {
                    soundEnabledTemp = newValue;
                });
                currentY += spacing;

                // 透明度过渡速度（仅在半透明模式下显示）
                if (transparencyModeTemp != HotbarConfig.TransparencyMode.ALWAYS_OPAQUE) {
                    addLabel(contentX, currentY, "透明度过渡速度");
                    currentY += 16;
                    FloatSliderWidget alphaSpeedSlider = new FloatSliderWidget(contentX, currentY, 200, 18,
                            Component.empty(), alphaTransitionSpeedTemp, HotbarConfig.getMinAlphaTransitionSpeed(), HotbarConfig.getMaxAlphaTransitionSpeed()) {
                        @Override
                        protected void applyValue() {
                            alphaTransitionSpeedTemp = (float) (this.value * (max - min) + min);
                        }
                    };
                    contentWidgets.add(alphaSpeedSlider);
                    this.addRenderableWidget(alphaSpeedSlider);
                }
            }
            case BEHAVIOR -> {
                // 滚轮循环
                addToggleButton(contentX, currentY, "滚轮循环", scrollLoopTemp, newValue -> {
                    scrollLoopTemp = newValue;
                });
                currentY += spacing;

                // 自动保存
                addToggleButton(contentX, currentY, "自动保存", autoSaveTemp, newValue -> {
                    autoSaveTemp = newValue;
                });
            }
            case HOTKEYS -> {
                // 快捷键说明
                addKeybindEntry(contentX, currentY, "Ctrl + B", "展开/收起快捷栏列表");
                currentY += spacing + 5;
                addKeybindEntry(contentX, currentY, "Alt + 滚轮", "选择快捷栏");
                currentY += spacing + 5;
                addKeybindEntry(contentX, currentY, "Alt + 数字键", "快速切换快捷栏");
                currentY += spacing + 5;
                addKeybindEntry(contentX, currentY, "Ctrl + N", "打开设置界面");
            }
            case ABOUT -> {
                // 关于信息
                addLabel(contentX, currentY, "快捷栏拓展 (HotbarExpand)");
                currentY += 25;
                addDescription(contentX, currentY, "版本: 1.0.0");
                currentY += 20;
                addDescription(contentX, currentY, "作者: Example");
                currentY += 30;
                addDescription(contentX, currentY, "扩展原版快捷栏功能，支持最多18个快捷栏。");
                currentY += 40;
                addDescription(contentX, currentY, "§7在创造模式下，快捷栏数据会自动同步到服务器。");
                currentY += 15;
                addDescription(contentX, currentY, "§7在生存模式下，快捷栏仅作为本地预设使用。");
            }
        }
    }

    private void addLabel(int x, int y, String text) {
        // 标签会在render中绘制
    }

    private void addDescription(int x, int y, String text) {
        // 描述会在render中绘制
    }

    private void addWarning(int x, int y, String text) {
        // 警告会在render中绘制
    }

    private void addKeybindEntry(int x, int y, String key, String description) {
        // 快捷键条目会在render中绘制
    }

    private void addToggleButton(int x, int y, String label, boolean currentValue, ToggleCallback callback) {
        ToggleButton button = new ToggleButton(x, y, 250, 20, label, currentValue, callback);
        contentWidgets.add(button);
        this.addRenderableWidget(button);
    }

    private void cycleLayoutMode() {
        HotbarConfig.LayoutMode[] modes = HotbarConfig.LayoutMode.values();
        int currentIndex = HotbarConfig.getLayout().ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        HotbarConfig.setLayout(modes[nextIndex]);
        initCategoryContent(); // 刷新内容
    }

    private void cycleDisplayPosition() {
        HotbarConfig.DisplayPosition[] positions = HotbarConfig.DisplayPosition.values();
        int currentIndex = displayPositionTemp.ordinal();
        int nextIndex = (currentIndex + 1) % positions.length;
        displayPositionTemp = positions[nextIndex];
    }

    private void cycleExpandMode() {
        HotbarConfig.ExpandMode[] modes = HotbarConfig.ExpandMode.values();
        int currentIndex = expandModeTemp.ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        expandModeTemp = modes[nextIndex];
    }

    private void cycleTransparencyMode() {
        HotbarConfig.TransparencyMode[] modes = HotbarConfig.TransparencyMode.values();
        int currentIndex = transparencyModeTemp.ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        transparencyModeTemp = modes[nextIndex];
    }

    private void saveSettings() {
        if (maxVisibleTemp != HotbarConfig.getMaxVisibleHotbars()) {
            HotbarConfig.setMaxVisibleHotbars(maxVisibleTemp);
        }
        HotbarConfig.setDisplayPosition(displayPositionTemp);
        HotbarConfig.setExpandMode(expandModeTemp);
        HotbarConfig.setTransparencyMode(transparencyModeTemp);
        HotbarConfig.setAutoHideDelay(autoHideDelayTemp);
        HotbarConfig.setTransparency(transparencyTemp);
        HotbarConfig.setAnimationEnabled(animationEnabledTemp);
        HotbarConfig.setSoundEnabled(soundEnabledTemp);
        HotbarConfig.setIndicatorEnabled(indicatorEnabledTemp);
        HotbarConfig.setAutoSaveEnabled(autoSaveTemp);
        HotbarConfig.setScrollLoopEnabled(scrollLoopTemp);
        HotbarConfig.setAnimationSpeed(animationSpeedTemp);
        HotbarConfig.setExpandDelay(expandDelayTemp);
        HotbarConfig.setIndicatorOpacity(indicatorOpacityTemp);
        HotbarConfig.setAlphaTransitionSpeed(alphaTransitionSpeedTemp);
    }

    private void resetToDefaults() {
        maxVisibleTemp = 9;
        animationEnabledTemp = true;
        soundEnabledTemp = true;
        indicatorEnabledTemp = true;
        autoSaveTemp = true;
        scrollLoopTemp = true;
        animationSpeedTemp = 1.0f;
        expandDelayTemp = 0;
        indicatorOpacityTemp = 100;
        displayPositionTemp = HotbarConfig.DisplayPosition.ABOVE_HOTBAR;
        expandModeTemp = HotbarConfig.ExpandMode.ALWAYS_SHOW;
        transparencyModeTemp = HotbarConfig.TransparencyMode.ALWAYS_OPAQUE;
        autoHideDelayTemp = 3000;
        transparencyTemp = 50;
        alphaTransitionSpeedTemp = 1.0f;
        HotbarConfig.setLayout(HotbarConfig.LayoutMode.ONE_X_ONE);
        initCategoryContent();
    }

    private void selectCategory(Category category) {
        this.currentCategory = category;
        initCategoryContent();
    }

    @Override
    public void tick() {
        super.tick();
        // 更新动画演示
        updateDemoAnimation();
    }

    private void updateDemoAnimation() {
        long currentTime = System.currentTimeMillis();
        if (lastDemoTime == 0) {
            lastDemoTime = currentTime;
            return;
        }

        float deltaTime = (currentTime - lastDemoTime) / 1000f;
        lastDemoTime = currentTime;

        // 根据动画速度调整
        float speed = animationEnabledTemp ? animationSpeedTemp : 5.0f; // 关闭动画时快速切换

        if (demoExpanding) {
            demoExpandProgress += deltaTime * speed * 0.5f;
            if (demoExpandProgress >= 1f) {
                demoExpandProgress = 1f;
                demoExpanding = false;
            }
        } else {
            demoExpandProgress -= deltaTime * speed * 0.5f;
            if (demoExpandProgress <= 0f) {
                demoExpandProgress = 0f;
                demoExpanding = true;
                // 切换演示的快捷栏
                demoCurrentHotbar = (demoCurrentHotbar + 1) % 9;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染半透明黑色背景（全屏）
        guiGraphics.fill(0, 0, this.width, this.height, 0xDD000000);

        // 渲染侧边栏背景
        guiGraphics.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF1a1a2e);
        guiGraphics.fill(SIDEBAR_WIDTH, 0, SIDEBAR_WIDTH + 1, this.height, 0xFF2d2d44);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染背景
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染标题
        guiGraphics.drawString(this.font, this.title, SIDEBAR_WIDTH + CONTENT_PADDING, 20, 0xFFFFFF);

        // 渲染分类按钮
        for (CategoryButton button : categoryButtons) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 渲染内容区域
        renderContent(guiGraphics);

        // 渲染演示区域（右侧）
        renderDemoArea(guiGraphics);

        // 渲染控件（按钮等）
        for (net.minecraft.client.gui.components.AbstractWidget widget : contentWidgets) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 渲染底部按钮
        for (var renderable : this.renderables) {
            if (renderable instanceof Button button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderContent(GuiGraphics guiGraphics) {
        int contentX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int startY = 50;
        int spacing = 32;
        int currentY = startY;

        switch (currentCategory) {
            case GENERAL -> {
                guiGraphics.drawString(this.font, "快捷栏布局", contentX, currentY, 0xAAAAAA);
                currentY += 16 + 24 + 10;

                guiGraphics.drawString(this.font, "列表最大显示数量", contentX, currentY, 0xAAAAAA);
                currentY += 16 + 24;

                if (maxVisibleTemp > 9) {
                    guiGraphics.drawString(this.font, "⚠ Alt+滚轮仅覆盖前9个快捷栏", contentX, currentY + 5, 0xFFAA00);
                }

                // 渲染布局说明
                currentY += 40;
                guiGraphics.drawString(this.font, "当前布局说明:", contentX, currentY, 0xAAAAAA);
                currentY += 15;
                String desc = switch (HotbarConfig.getLayout()) {
                    case ONE_X_ONE -> "1x1 - 原版样式，显示1个快捷栏";
                    case ONE_X_TWO -> "1x2 - 垂直排列，显示2个快捷栏";
                    case TWO_X_ONE -> "2x1 - 水平排列，显示2个快捷栏";
                    case TWO_X_TWO -> "2x2 - 网格排列，显示4个快捷栏";
                };
                guiGraphics.drawString(this.font, desc, contentX, currentY, 0xFFFFFF);

                // 渲染当前状态
                currentY += 40;
                renderStatusPanel(guiGraphics, contentX, currentY, "当前状态", new String[]{
                        "布局: " + HotbarConfig.getLayout().displayName,
                        "最大显示: " + maxVisibleTemp + " 个快捷栏",
                        "当前选中: 快捷栏 " + (HotbarManager.getCurrentHotbarIndex() + 1)
                });
            }
            case DISPLAY -> {
                guiGraphics.drawString(this.font, "显示设置", contentX, currentY - 20, 0xAAAAAA);

                // 渲染当前状态
                currentY += 300;
                String[] statusItems = new String[]{
                        "显示位置: " + displayPositionTemp.displayName,
                        "展开模式: " + expandModeTemp.displayName,
                        expandModeTemp == HotbarConfig.ExpandMode.AUTO_HIDE ? "自动关闭: " + autoHideDelayTemp + "ms" : null,
                        "透明模式: " + transparencyModeTemp.displayName,
                        transparencyModeTemp != HotbarConfig.TransparencyMode.ALWAYS_OPAQUE ? "透明度: " + transparencyTemp + "%" : null,
                        "展开动画: " + (animationEnabledTemp ? "开启" : "关闭"),
                        "动画速度: " + String.format("%.1f", animationSpeedTemp) + "x",
                        "展开延迟: " + expandDelayTemp + "ms",
                        "位置指示器: " + (indicatorEnabledTemp ? "开启" : "关闭"),
                        "指示器透明度: " + indicatorOpacityTemp + "%",
                        "切换音效: " + (soundEnabledTemp ? "开启" : "关闭")
                };
                // 过滤掉null项
                java.util.List<String> filteredItems = new java.util.ArrayList<>();
                for (String item : statusItems) {
                    if (item != null) filteredItems.add(item);
                }
                renderStatusPanel(guiGraphics, contentX, currentY, "当前显示设置",
                        filteredItems.toArray(new String[0]));
            }
            case BEHAVIOR -> {
                guiGraphics.drawString(this.font, "行为设置", contentX, currentY - 20, 0xAAAAAA);

                // 渲染当前状态
                currentY += 80;
                renderStatusPanel(guiGraphics, contentX, currentY, "当前行为设置", new String[]{
                        "滚轮循环: " + (scrollLoopTemp ? "开启" : "关闭"),
                        "自动保存: " + (autoSaveTemp ? "开启" : "关闭")
                });
            }
            case HOTKEYS -> {
                guiGraphics.drawString(this.font, "快捷键", contentX, currentY - 20, 0xAAAAAA);
                currentY += 10;

                renderKeybind(guiGraphics, contentX, currentY, "Ctrl + B", "展开/收起快捷栏列表");
                currentY += 38;
                renderKeybind(guiGraphics, contentX, currentY, "Alt + 滚轮", "选择快捷栏");
                currentY += 38;
                renderKeybind(guiGraphics, contentX, currentY, "Alt + 数字键", "快速切换快捷栏");
                currentY += 38;
                renderKeybind(guiGraphics, contentX, currentY, "Ctrl + N", "打开设置界面");
            }
            case ABOUT -> {
                guiGraphics.drawString(this.font, "快捷栏拓展 (HotbarExpand)", contentX, currentY, 0xFFFFFF);
                currentY += 25;
                guiGraphics.drawString(this.font, "版本: 1.0.0", contentX, currentY, 0xAAAAAA);
                currentY += 20;
                guiGraphics.drawString(this.font, "作者: Example", contentX, currentY, 0xAAAAAA);
                currentY += 30;
                guiGraphics.drawString(this.font, "扩展原版快捷栏功能，支持最多18个快捷栏。", contentX, currentY, 0xAAAAAA);
                currentY += 40;
                guiGraphics.drawString(this.font, "在创造模式下，快捷栏数据会自动同步到服务器。", contentX, currentY, 0x888888);
                currentY += 15;
                guiGraphics.drawString(this.font, "在生存模式下，快捷栏仅作为本地预设使用。", contentX, currentY, 0x888888);
            }
        }
    }

    private void renderStatusPanel(GuiGraphics guiGraphics, int x, int y, String title, String[] items) {
        int panelWidth = 220;
        int lineHeight = 14;
        int padding = 8;
        int panelHeight = items.length * lineHeight + padding * 2 + 20;

        // 渲染面板背景
        guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF2d2d44);
        guiGraphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, 0xFF1a1a2e);

        // 渲染标题
        guiGraphics.drawString(this.font, title, x + padding, y + padding, 0x6a6aff);

        // 渲染分隔线
        guiGraphics.fill(x + padding, y + padding + 12, x + panelWidth - padding, y + padding + 13, 0xFF3d3d5c);

        // 渲染状态项
        int currentY = y + padding + 20;
        for (String item : items) {
            guiGraphics.drawString(this.font, item, x + padding + 5, currentY, 0xCCCCCC);
            currentY += lineHeight;
        }
    }

    private void renderDemoArea(GuiGraphics guiGraphics) {
        // 根据当前分类计算演示区域大小
        calculateDemoSize();
        
        int demoX = this.width - demoActualWidth - 20;
        int demoY = 50;

        // 渲染演示区域背景
        guiGraphics.fill(demoX, demoY, demoX + demoActualWidth, demoY + demoActualHeight, 0xFF2d2d44);
        guiGraphics.fill(demoX + 1, demoY + 1, demoX + demoActualWidth - 1, demoY + demoActualHeight - 1, 0xFF1a1a2e);

        // 渲染标题
        guiGraphics.drawString(this.font, "动画演示", demoX + 10, demoY + 10, 0x6a6aff);

        // 根据当前分类渲染不同的演示
        switch (currentCategory) {
            case GENERAL -> renderLayoutDemo(guiGraphics, demoX, demoY);
            case DISPLAY -> renderAnimationDemo(guiGraphics, demoX, demoY);
            case BEHAVIOR -> renderBehaviorDemo(guiGraphics, demoX, demoY);
            default -> renderDefaultDemo(guiGraphics, demoX, demoY);
        }
    }

    private void calculateDemoSize() {
        // 根据分类和布局动态计算演示区域大小
        switch (currentCategory) {
            case GENERAL -> {
                // 根据布局调整大小
                HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
                switch (layout) {
                    case ONE_X_ONE -> {
                        demoActualWidth = 260;
                        demoActualHeight = 90;
                    }
                    case ONE_X_TWO -> {
                        demoActualWidth = 260;
                        demoActualHeight = 130;
                    }
                    case TWO_X_ONE -> {
                        demoActualWidth = 340;
                        demoActualHeight = 90;
                    }
                    case TWO_X_TWO -> {
                        demoActualWidth = 340;
                        demoActualHeight = 130;
                    }
                }
            }
            case DISPLAY -> {
                // 显示分类：需要空间展示主快捷栏+展开列表
                int hotbarWidth = 182;
                int offhandWidth = 29;
                int listWidth = hotbarWidth + 20;
                demoActualWidth = hotbarWidth + offhandWidth * 2 + 20 + listWidth + 40;
                demoActualHeight = 160;
            }
            case BEHAVIOR -> {
                demoActualWidth = 220;
                demoActualHeight = 160;
            }
            default -> {
                demoActualWidth = 200;
                demoActualHeight = 140;
            }
        }
    }

    private void renderLayoutDemo(GuiGraphics guiGraphics, int x, int y) {
        int centerX = x + demoActualWidth / 2;
        int centerY = y + demoActualHeight / 2 + 10;

        // 使用与实际渲染一致的尺寸
        int hotbarWidth = 182;  // 原版快捷栏宽度
        int hotbarHeight = 22;  // 原版快捷栏高度
        int offhandWidth = 29;  // 副手宽度
        int slotSize = 20;      // 槽位大小
        int gap = 4;            // 快捷栏之间的间隙

        HotbarConfig.LayoutMode layout = HotbarConfig.getLayout();
        int cols = layout.columns;
        int rows = layout.rows;

        // 计算总尺寸（包含副手）
        int totalWidth, totalHeight;
        if (cols == 2) {
            // 2列布局：副手 + 快捷栏 + gap + 快捷栏 + 副手
            totalWidth = offhandWidth + hotbarWidth + gap + hotbarWidth + offhandWidth;
        } else {
            // 1列布局：副手 + 快捷栏 + 副手
            totalWidth = offhandWidth + hotbarWidth + offhandWidth;
        }
        totalHeight = rows * hotbarHeight + (rows - 1) * 2; // 行间距2像素

        int startX = centerX - totalWidth / 2;
        int startY = centerY - totalHeight / 2;

        // 渲染快捷栏网格
        int maxHotbars = Math.min(4, cols * rows); // 演示最多显示4个快捷栏
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int hotbarIdx = row * cols + col;
                if (hotbarIdx >= maxHotbars) continue;
                
                int hotbarX, hotbarY;

                if (cols == 2) {
                    // 2列布局
                    hotbarX = startX + offhandWidth + col * (hotbarWidth + gap);
                } else {
                    // 1列布局
                    hotbarX = startX + offhandWidth;
                }
                hotbarY = startY + row * (hotbarHeight + 2);

                // 确定副手位置（左侧快捷栏副手在左，右侧快捷栏副手在右）
                boolean offhandOnLeft = (col == 0);

                // 渲染副手
                int offhandX = offhandOnLeft ?
                        hotbarX - offhandWidth - 2 :
                        hotbarX + hotbarWidth + 2;
                renderDemoOffhand(guiGraphics, offhandX, hotbarY, offhandWidth, hotbarHeight);

                // 渲染快捷栏背景
                renderDemoHotbarBackground(guiGraphics, hotbarX, hotbarY, hotbarWidth, hotbarHeight);

                // 渲染选中框（当前选中的快捷栏）
                boolean isCurrent = hotbarIdx == HotbarManager.getCurrentHotbarIndex();
                if (isCurrent) {
                    int selectedSlot = 0; // 演示用第一个槽位选中
                    renderDemoHotbarSelection(guiGraphics, hotbarX, hotbarY, slotSize, selectedSlot);
                }

                // 渲染槽位
                for (int slot = 0; slot < 9; slot++) {
                    int slotX = hotbarX + 3 + slot * slotSize;
                    int slotY = hotbarY + 3;
                    int color = (isCurrent && slot == 0) ? 0xFF6a6aff : 0xFF3d3d5c;
                    guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, color);
                }

                // 渲染快捷栏编号
                int numberColor = isCurrent ? 0xFFFF00 : 0xFFFFFF;
                int numberX = offhandOnLeft ? offhandX - 15 : offhandX + offhandWidth + 5;
                guiGraphics.drawString(this.font, String.valueOf(hotbarIdx + 1), numberX, hotbarY + 7, numberColor);
            }
        }

        // 渲染当前选中提示
        guiGraphics.drawString(this.font, "当前: 快捷栏 " + (HotbarManager.getCurrentHotbarIndex() + 1), x + 10, y + demoActualHeight - 20, 0xAAAAAA);
    }

    private void renderDemoOffhand(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // 渲染副手背景（简化版）
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2d2d44);
        // 副手槽位
        guiGraphics.fill(x + 3, y + 3, x + 19, y + 19, 0xFF4a4a6a);
    }

    private void renderDemoHotbarBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // 渲染快捷栏背景
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2d2d44);
    }

    private void renderDemoHotbarSelection(GuiGraphics guiGraphics, int x, int y, int slotSize, int selectedSlot) {
        // 渲染选中框
        int selectX = x + 3 + selectedSlot * slotSize - 1;
        int selectY = y + 3 - 1;
        guiGraphics.fill(selectX, selectY, selectX + 18, selectY + 18, 0xFFFFFFFF);
        guiGraphics.fill(selectX + 1, selectY + 1, selectX + 17, selectY + 17, 0xFF2d2d44);
    }

    private void renderAnimationDemo(GuiGraphics guiGraphics, int x, int y) {
        int margin = 15;
        int contentX = x + margin;
        int contentY = y + 35;
        int contentWidth = demoActualWidth - margin * 2;
        int contentHeight = demoActualHeight - 35 - margin;

        // 使用与实际一致的尺寸
        int hotbarWidth = 182;
        int hotbarHeight = 22;
        int offhandWidth = 29;
        int slotSize = 20;

        // 计算主快捷栏位置（左侧）
        int mainBarX = contentX + offhandWidth + 10;
        int mainBarY = contentY + 10;

        // 渲染副手（左侧）
        renderDemoOffhand(guiGraphics, mainBarX - offhandWidth - 2, mainBarY, offhandWidth, hotbarHeight);

        // 渲染快捷栏背景
        renderDemoHotbarBackground(guiGraphics, mainBarX, mainBarY, hotbarWidth, hotbarHeight);

        // 渲染选中框
        renderDemoHotbarSelection(guiGraphics, mainBarX, mainBarY, slotSize, 0);

        // 渲染槽位
        for (int i = 0; i < 9; i++) {
            int slotX = mainBarX + 3 + i * slotSize;
            int slotY = mainBarY + 3;
            int color = i == 0 ? 0xFF6a6aff : 0xFF3d3d5c;
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, color);
        }

        // 渲染副手（右侧）
        renderDemoOffhand(guiGraphics, mainBarX + hotbarWidth + 2, mainBarY, offhandWidth, hotbarHeight);

        // 渲染编号（黄色，当前快捷栏）
        guiGraphics.drawString(this.font, String.valueOf(demoCurrentHotbar + 1), 
                mainBarX - offhandWidth - 15, mainBarY + 7, 0xFFFF00);

        // 渲染展开列表（右侧，与实际一致：垂直排列在右下角）
        if (animationEnabledTemp) {
            float smoothProgress = getSmoothProgress(demoExpandProgress);
            int maxVisible = Math.min(5, maxVisibleTemp); // 演示最多显示5个
            
            // 列表位置：右侧，从底部向上展开
            int listRightX = contentX + contentWidth;
            int listBaseY = mainBarY + hotbarHeight + 10;
            
            // 计算列表总高度
            int listTotalHeight = maxVisible * hotbarHeight;
            
            // 渲染列表项（从上到下，但实际是从下往上展开）
            for (int i = 0; i < maxVisible; i++) {
                // 计算动画进度（延迟效果：下面的先出现）
                float slotProgress = Math.min(1.0f, Math.max(0.0f, smoothProgress * 1.5f - (maxVisible - 1 - i) * 0.08f));
                
                if (slotProgress <= 0) continue;
                
                // 目标位置（从上到下排列）
                int targetY = listBaseY + i * hotbarHeight;
                
                // 动画起始位置（从下方）
                int startY = listBaseY + listTotalHeight + hotbarHeight;
                
                // 插值计算当前位置
                int renderY = (int) (startY + (targetY - startY) * getSmoothProgress(slotProgress));
                
                // 检查是否超出演示区域
                if (renderY > contentY + contentHeight) continue;
                
                boolean isCurrent = i == demoCurrentHotbar;
                
                // 渲染副手（左侧）
                int listOffhandX = listRightX - hotbarWidth - offhandWidth - 2;
                if (isCurrent) {
                    // 当前快捷栏高亮（黄色）
                    guiGraphics.fill(listOffhandX, renderY, listOffhandX + offhandWidth, renderY + hotbarHeight, 0xFFAA8800);
                }
                renderDemoOffhand(guiGraphics, listOffhandX, renderY, offhandWidth, hotbarHeight);
                
                // 渲染快捷栏背景
                int listBarX = listRightX - hotbarWidth;
                if (isCurrent) {
                    guiGraphics.fill(listBarX, renderY, listBarX + hotbarWidth, renderY + hotbarHeight, 0xFFAA8800);
                } else {
                    renderDemoHotbarBackground(guiGraphics, listBarX, renderY, hotbarWidth, hotbarHeight);
                }
                
                // 渲染槽位
                for (int slot = 0; slot < 9; slot++) {
                    int slotX = listBarX + 3 + slot * slotSize;
                    int slotY = renderY + 3;
                    int slotColor = isCurrent && slot == 0 ? 0xFF6a6aff : 0xFF3d3d5c;
                    guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, slotColor);
                }
                
                // 渲染编号（白色，当前用黄色）
                int numberColor = isCurrent ? 0xFFFF00 : 0xFFFFFF;
                guiGraphics.drawString(this.font, String.valueOf(i + 1), 
                        listOffhandX - 15, renderY + 7, numberColor);
                
                // 如果是当前快捷栏，渲染选中框
                if (isCurrent) {
                    renderDemoHotbarSelection(guiGraphics, listBarX, renderY, slotSize, 0);
                }
            }

            // 渲染动画状态
            String status = demoExpanding ? "展开中..." : "收起中...";
            guiGraphics.drawString(this.font, status, contentX, contentY + contentHeight - 25, 0xAAAAAA);
            guiGraphics.drawString(this.font, String.format("进度: %.0f%%", demoExpandProgress * 100), contentX, contentY + contentHeight - 10, 0x888888);
        } else {
            guiGraphics.drawString(this.font, "动画已关闭", contentX, contentY + contentHeight - 10, 0x888888);
        }

        // 渲染指示器演示（在主快捷栏下方）
        if (indicatorEnabledTemp) {
            int indicatorY = mainBarY + hotbarHeight + 4;
            int barWidth = 3;
            int barGap = 1;
            int totalBars = 9;
            int totalWidth = totalBars * barWidth + (totalBars - 1) * barGap;
            int startX = mainBarX + hotbarWidth / 2 - totalWidth / 2;
            int opacity = (int) (255 * (indicatorOpacityTemp / 100f));

            // 渲染位置指示器
            for (int i = 0; i < totalBars; i++) {
                int barX = startX + i * (barWidth + barGap);
                int barColor = i == demoCurrentHotbar ?
                        (0xFFFFFF & 0xFFFFFF) | (opacity << 24) :
                        0xFF808080;
                guiGraphics.fill(barX, indicatorY, barX + barWidth, indicatorY + 3, barColor);
            }
        }
    }

    private void renderBehaviorDemo(GuiGraphics guiGraphics, int x, int y) {
        int centerX = x + demoActualWidth / 2;
        int demoY = y + 50;

        // 渲染滚轮循环演示
        guiGraphics.drawString(this.font, "滚轮循环:", x + 15, demoY, 0xAAAAAA);
        String loopStatus = scrollLoopTemp ? "开启 - 可循环滚动" : "关闭 - 到达边界停止";
        guiGraphics.drawString(this.font, loopStatus, x + 15, demoY + 15, scrollLoopTemp ? 0x4CAF50 : 0xFF6666);

        // 渲染自动保存演示
        guiGraphics.drawString(this.font, "自动保存:", x + 15, demoY + 40, 0xAAAAAA);
        String saveStatus = autoSaveTemp ? "开启 - 自动保存配置" : "关闭 - 手动保存配置";
        guiGraphics.drawString(this.font, saveStatus, x + 15, demoY + 55, autoSaveTemp ? 0x4CAF50 : 0xFF6666);

        // 渲染示意图
        int diagramY = demoY + 80;
        guiGraphics.drawString(this.font, "示意图:", x + 15, diagramY, 0x888888);

        // 绘制循环示意图
        int circleX = centerX;
        int circleY = diagramY + 30;
        int radius = 20;

        // 绘制圆
        for (int i = 0; i < 360; i += 10) {
            double rad = Math.toRadians(i);
            int px = (int) (circleX + Math.cos(rad) * radius);
            int py = (int) (circleY + Math.sin(rad) * radius);
            guiGraphics.fill(px, py, px + 2, py + 2, scrollLoopTemp ? 0xFF4CAF50 : 0xFF666666);
        }

        // 绘制箭头
        guiGraphics.drawString(this.font, scrollLoopTemp ? "↻" : "→", circleX - 5, circleY - 6, scrollLoopTemp ? 0x4CAF50 : 0xFF6666);
    }

    private void renderDefaultDemo(GuiGraphics guiGraphics, int x, int y) {
        int centerX = x + demoActualWidth / 2;
        int centerY = y + demoActualHeight / 2 + 10;

        // 渲染Logo/图标
        int size = 40;
        int logoX = centerX - size / 2;
        int logoY = centerY - size / 2 - 10;

        // 渲染方块图标
        guiGraphics.fill(logoX, logoY, logoX + size, logoY + size, 0xFF6a6aff);
        guiGraphics.fill(logoX + 2, logoY + 2, logoX + size - 2, logoY + size - 2, 0xFF4a4a6a);
        guiGraphics.drawString(this.font, "H", logoX + size / 2 - 4, logoY + size / 2 - 4, 0xFFFFFF);

        // 渲染提示文字
        guiGraphics.drawString(this.font, "HotbarExpand", centerX - 35, logoY + size + 10, 0xAAAAAA);
        guiGraphics.drawString(this.font, "快捷栏拓展", centerX - 30, logoY + size + 25, 0x888888);
    }

    private float getSmoothProgress(float progress) {
        if (progress >= 1.0f) return 1.0f;
        if (progress <= 0.0f) return 0.0f;
        return 1.0f - (float) Math.pow(1.0f - progress, 3);
    }

    private void renderKeybind(GuiGraphics guiGraphics, int x, int y, String key, String description) {
        // 渲染按键背景
        int keyWidth = this.font.width(key) + 10;
        guiGraphics.fill(x, y - 2, x + keyWidth, y + 12, 0xFF3d3d5c);
        guiGraphics.drawString(this.font, key, x + 5, y, 0xFFFFFF);

        // 渲染描述
        guiGraphics.drawString(this.font, description, x + keyWidth + 10, y, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    // ==================== 内部类 ====================

    private class CategoryButton extends AbstractWidget {
        private final Category category;

        public CategoryButton(int x, int y, int width, int height, Category category) {
            super(x, y, width, height, Component.literal(category.name));
            this.category = category;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isSelected = currentCategory == category;
            boolean isHovered = isMouseOver(mouseX, mouseY);

            // 渲染选中背景
            if (isSelected) {
                guiGraphics.fill(this.getX() - 5, this.getY(), this.getX() + this.width + 5, this.getY() + this.height, 0xFF4a4a6a);
                guiGraphics.fill(this.getX() - 5, this.getY(), this.getX() - 3, this.getY() + this.height, 0xFF6a6aff);
            } else if (isHovered) {
                guiGraphics.fill(this.getX() - 5, this.getY(), this.getX() + this.width + 5, this.getY() + this.height, 0xFF2d2d44);
            }

            // 渲染文字
            int textColor = isSelected ? 0xFFFFFF : (isHovered ? 0xCCCCCC : 0x888888);
            guiGraphics.drawString(font, category.name, this.getX() + 10, this.getY() + (this.height - 8) / 2, textColor);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            selectCategory(category);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    private class ToggleButton extends AbstractWidget {
        private final String label;
        private boolean value;
        private final ToggleCallback callback;

        public ToggleButton(int x, int y, int width, int height, String label, boolean initialValue, ToggleCallback callback) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.value = initialValue;
            this.callback = callback;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 渲染标签
            guiGraphics.drawString(font, label, this.getX(), this.getY() + 6, 0xAAAAAA);

            // 渲染开关背景
            int toggleX = this.getX() + this.width - 50;
            int toggleY = this.getY() + 2;
            int toggleWidth = 44;
            int toggleHeight = 18;

            // 背景
            int bgColor = value ? 0xFF4CAF50 : 0xFF666666;
            guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, bgColor);

            // 滑块
            int thumbSize = toggleHeight - 4;
            int thumbX = value ? toggleX + toggleWidth - thumbSize - 2 : toggleX + 2;
            int thumbY = toggleY + 2;
            guiGraphics.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0xFFFFFFFF);

            // 状态文字
            String stateText = value ? "开启" : "关闭";
            int textX = toggleX + (value ? 6 : toggleWidth - 6 - font.width(stateText));
            guiGraphics.drawString(font, stateText, textX, toggleY + 5, 0xFFFFFF);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            value = !value;
            callback.onToggle(value);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    private interface ToggleCallback {
        void onToggle(boolean newValue);
    }

    /**
     * 滑块组件 - 用于设置数值
     */
    public static abstract class SliderWidget extends AbstractWidget {
        protected double value;
        protected final int min;
        protected final int max;
        private boolean isDragging = false;

        public SliderWidget(int x, int y, int width, int height, Component message, int initialValue, int min, int max) {
            super(x, y, width, height, message);
            this.min = min;
            this.max = max;
            this.value = (double) (initialValue - min) / (max - min);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();

            // 绘制滑块轨道
            int trackY = this.getY() + this.height / 2 - 1;
            guiGraphics.fill(this.getX(), trackY, this.getX() + this.width, trackY + 2, 0xFF555555);

            // 计算滑块位置
            int thumbWidth = 10;
            int thumbX = this.getX() + (int) (this.value * (this.width - thumbWidth));

            // 绘制滑块
            guiGraphics.fill(thumbX, this.getY(), thumbX + thumbWidth, this.getY() + this.height, this.isHovered() ? 0xFF6a6aff : 0xFF4a4a6a);

            // 绘制数值文字
            int currentValue = (int) (this.value * (max - min) + min);
            Component valueText = Component.literal(String.valueOf(currentValue));
            guiGraphics.drawCenteredString(minecraft.font, valueText, this.getX() + this.width + 15, this.getY() + 5, 0xFFFFFF);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.isDragging = true;
            this.setValueFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (this.isDragging) {
                this.setValueFromMouse(mouseX);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.isDragging = false;
        }

        private void setValueFromMouse(double mouseX) {
            int thumbWidth = 10;
            this.value = Mth.clamp((mouseX - this.getX()) / (this.width - thumbWidth), 0.0, 1.0);
            this.applyValue();
        }

        public void setValue(int newValue) {
            this.value = (double) (newValue - min) / (max - min);
        }

        protected abstract void applyValue();

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    /**
     * 浮点滑块组件 - 用于设置浮点数值
     */
    public static abstract class FloatSliderWidget extends AbstractWidget {
        protected double value;
        protected final float min;
        protected final float max;
        private boolean isDragging = false;

        public FloatSliderWidget(int x, int y, int width, int height, Component message, float initialValue, float min, float max) {
            super(x, y, width, height, message);
            this.min = min;
            this.max = max;
            this.value = (double) (initialValue - min) / (max - min);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();

            // 绘制滑块轨道
            int trackY = this.getY() + this.height / 2 - 1;
            guiGraphics.fill(this.getX(), trackY, this.getX() + this.width, trackY + 2, 0xFF555555);

            // 计算滑块位置
            int thumbWidth = 10;
            int thumbX = this.getX() + (int) (this.value * (this.width - thumbWidth));

            // 绘制滑块
            guiGraphics.fill(thumbX, this.getY(), thumbX + thumbWidth, this.getY() + this.height, this.isHovered() ? 0xFF6a6aff : 0xFF4a4a6a);

            // 绘制数值文字（保留1位小数）
            float currentValue = (float) (this.value * (max - min) + min);
            Component valueText = Component.literal(String.format("%.1f", currentValue));
            guiGraphics.drawCenteredString(minecraft.font, valueText, this.getX() + this.width + 20, this.getY() + 5, 0xFFFFFF);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.isDragging = true;
            this.setValueFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (this.isDragging) {
                this.setValueFromMouse(mouseX);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.isDragging = false;
        }

        private void setValueFromMouse(double mouseX) {
            int thumbWidth = 10;
            this.value = Mth.clamp((mouseX - this.getX()) / (this.width - thumbWidth), 0.0, 1.0);
            this.applyValue();
        }

        public void setValue(float newValue) {
            this.value = (double) (newValue - min) / (max - min);
        }

        protected abstract void applyValue();

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
