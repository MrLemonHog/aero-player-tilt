package com.mlh.aero_player_tilt.client.config.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class DimButton extends Button {

    public static final int VEIL = 0x99000000;

    public static final int VEIL_LIT = 0x66000000;

    private final BooleanSupplier dimmed;

    public DimButton(int width, int height, Component message, OnPress onPress,
                     BooleanSupplier dimmed) {
        super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
        this.dimmed = dimmed;
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(gfx, mouseX, mouseY, partialTick);
        if (!dimmed.getAsBoolean()) return;

        gfx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                isHoveredOrFocused() ? VEIL_LIT : VEIL);
    }

    public static void veil(GuiGraphics gfx, int x, int y, int width, int height) {
        gfx.fill(x, y, x + width, y + height, VEIL);
    }
}
