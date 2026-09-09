package com.mlh.aero_player_tilt.client.config;

import com.mlh.aero_player_tilt.client.config.categories.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;
import com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList;
import com.mlh.aero_player_tilt.client.config.ui.ModeIndicator;
import com.mlh.aero_player_tilt.client.KeyBindings;
import com.mlh.aero_player_tilt.client.config.entries.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ModConfigScreen extends Screen {
    private static final int FOOTER_HEIGHT = 36;
    private static final int TAB_HEIGHT    = 22;
    private static final int TAB_Y         = 22;
    private static final int TAB_W         = 84;
    private static final int TAB_GAP       = 2;

    private int headerHeight = 48;

    private final Screen parent;
    private ConfigScreenController controller;

    private ConfigOptionList optionList;
    private Button resetButton;
    private Button saveButton;

    private final List<ConfigCategory> categories = new ArrayList<>();

    private int activeCategory = 0;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("aero_player_tilt.configuration.title"));
        this.parent = parent;
        KeyBindings.syncFromMappings();

        rebuild();
        controller.snapshotAll();
    }

    private void rebuild() {
        categories.clear();
        categories.add(GeneralCategory.build());
        categories.add(MotionCategory.build());
        categories.add(SubLevelCategory.build());
        categories.add(ExperimentalCategory.build());
        categories.add(DebugCategory.build());

        controller = new ConfigScreenController(categories);
    }

    @Override
    protected void init() {
        headerHeight = TAB_Y + tabRows() * (TAB_HEIGHT + TAB_GAP) + 4;

        rebuildList();
        addTabButtons();
        addFooterButtons();
    }

    private int tabsPerRow() {
        int usable = Math.max(TAB_W, this.width - 16);
        return Math.max(1, Math.min(categories.size(), usable / TAB_W));
    }

    private int tabRows() {
        int perRow = tabsPerRow();
        return (categories.size() + perRow - 1) / perRow;
    }

    private void rebuildList() {
        if (optionList != null) removeWidget(optionList);

        int listTop    = headerHeight;
        int listBottom = this.height - FOOTER_HEIGHT;
        optionList = new ConfigOptionList(
                this.minecraft, this.width, listBottom - listTop, listTop, 26);

        for (ConfigOptionList.Entry e : categories.get(activeCategory).entries())
            optionList.addPublicEntry(e);

        addRenderableWidget(optionList);
    }

    private void addTabButtons() {
        int perRow = tabsPerRow();

        for (int i = 0; i < categories.size(); i++) {
            final int idx = i;

            int row      = i / perRow;
            int inRow    = i % perRow;
            int rowCount = Math.min(perRow, categories.size() - row * perRow);

            int rowWidth = rowCount * TAB_W;
            int startX   = (this.width - rowWidth) / 2;

            Button tab = Button.builder(Component.translatable(categories.get(i).nameKey()), btn -> {
                        activeCategory = idx;
                        clearWidgets();
                        init();
                    })
                    .bounds(startX + inRow * TAB_W, TAB_Y + row * (TAB_HEIGHT + TAB_GAP),
                            TAB_W - TAB_GAP, TAB_HEIGHT)
                    .build();

            if (i == activeCategory) tab.active = false;
            addRenderableWidget(tab);
        }
    }

    private void addFooterButtons() {
        int btnW   = 80;
        int btnH   = 20;
        int gap    = 4;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;
        int btnY   = this.height - FOOTER_HEIGHT + 8;

        addRenderableWidget(Button.builder(
                        Component.translatable("aero_player_tilt.configuration.btn.cancel"), btn -> {
                            restoreAll();
                            this.minecraft.setScreen(parent);
                        })
                .bounds(startX, btnY, btnW, btnH)
                .build());

        resetButton = Button.builder(
                        Component.translatable("aero_player_tilt.configuration.btn.reset"), btn -> {
                            resetCurrent();
                        })
                .bounds(startX + btnW + gap, btnY, btnW, btnH)
                .tooltip(Tooltip.create(Component.translatable("aero_player_tilt.configuration.btn.reset.tooltip")))
                .build();
        resetButton.active = false;
        addRenderableWidget(resetButton);

        saveButton = Button.builder(
                        Component.translatable("aero_player_tilt.configuration.btn.save"), btn -> {
                            Config.SPEC.save();
                            saveWorldRules();
                            this.minecraft.setScreen(parent);
                        }
                )
                .bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH)
                .build();
        addRenderableWidget(saveButton);
    }

    private void saveWorldRules() {
        if (!this.minecraft.hasSingleplayerServer()) return;
        if (!com.mlh.aero_player_tilt.ServerConfig.SPEC.isLoaded()) return;

        com.mlh.aero_player_tilt.ServerConfig.SPEC.save();
    }

    private void snapshotAll() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                e.saveSnapshot();
    }

    private void restoreAll() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                e.restoreSnapshot();
    }

    private void resetCurrent() {
        for (ConfigOptionList.Entry e : categories.get(activeCategory).entries())
            e.reset();
    }

    private boolean hasAnyHardViolation() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                if (e.hasHardLimitViolation()) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        renderBackground(gfx, mouseX, mouseY, delta);
        super.render(gfx, mouseX, mouseY, delta);

        gfx.fill(0, headerHeight - 1, this.width, headerHeight, 0x88AAAAAA);
        gfx.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, 0x88AAAAAA);

        gfx.drawCenteredString(this.font, this.title, this.width / 2, 7, 0xFFFFFF);

        ModeIndicator.render(gfx, this.width, 7, mouseX, mouseY);

        if (resetButton != null) resetButton.active = hasShiftDown();
        if (saveButton  != null) {
            boolean violation = hasAnyHardViolation();
            saveButton.active = !violation;
            if (violation) {
                gfx.drawCenteredString(this.font,
                        Component.translatable("aero_player_tilt.configuration.hardLimitWarning"),
                        this.width / 2, this.height - FOOTER_HEIGHT - 10, 0xFFFFFF);
            }
        }

        ConfigOptionList.renderNotice(gfx, this.font);
    }

    @Override
    public void onClose() {
        controller.restoreAll();
        KeyBindings.saveToConfig();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeKeyBindEntry() != null) {
            boolean consumed = activeKeyBindEntry().onKeyPressed(keyCode, scanCode);
            if (consumed) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeKeyBindEntry() != null) {
            boolean consumed = activeKeyBindEntry().onMouseClicked(button);
            if (consumed) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private KeyBindEntry activeKeyBindEntry() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                if (e instanceof KeyBindEntry kb && kb.isListening())
                    return kb;
        return null;
    }
}
