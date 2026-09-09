package com.mlh.aero_player_tilt.client.config.entries;

import com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList;
import com.mlh.aero_player_tilt.client.config.ui.DimButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

import static com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList.ENTRY_H;

public class ToggleButtonEntry extends ConfigOptionList.Entry {
    private static final int BTN_W = 68;
    private static final int BTN_H = 18;

    private final ModConfigSpec.BooleanValue config;
    private final boolean locked;
    private boolean currentValue;
    private boolean snapshot;

    private final Button button;

    public ToggleButtonEntry(String labelKey, String tooltipKey,
                             ModConfigSpec.BooleanValue config) {
        this(labelKey, tooltipKey, config, false);
    }

    public ToggleButtonEntry(String labelKey, String tooltipKey,
                             ModConfigSpec.BooleanValue config, boolean locked) {
        super(labelKey, tooltipKey);
        this.config       = config;
        this.locked       = locked;
        this.currentValue = config.get();
        this.snapshot     = currentValue;

        this.button = new DimButton(BTN_W, BTN_H, label(currentValue), btn -> {
                    if (locked) return;
                    currentValue = !currentValue;
                    config.set(currentValue);
                    btn.setMessage(label(currentValue));
                }, () -> !currentValue);

        button.active = !locked;

        if (!tooltipKey.isEmpty())
            button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    private static Component label(boolean value) {
        return value
                ? Component.translatable("aero_player_tilt.configuration.toggle.on")
                : Component.translatable("aero_player_tilt.configuration.toggle.off");
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top, mouseX, mouseY);

        int rightEdge = left + width - 4;
        int btnX = rightEdge - BTN_W;
        int btnY = top + (ENTRY_H - BTN_H) / 2;

        button.setX(btnX);
        button.setY(btnY);
        button.setWidth(BTN_W);

        button.render(gfx, mouseX, mouseY, delta);
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(button); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(button); }

    @Override public void saveSnapshot() { snapshot = currentValue; }

    @Override public void restoreSnapshot() {
        if (locked) return;
        currentValue = snapshot;
        config.set(snapshot);
        button.setMessage(label(snapshot));
    }

    @Override public void reset() {
        if (locked) return;
        boolean def = (boolean) config.getDefault();
        currentValue = def;
        config.set(def);
        button.setMessage(label(def));
    }

    @Override
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof ToggleButtonEntry d) this.snapshot = d.snapshot;
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}
