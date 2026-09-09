package com.mlh.aero_player_tilt.client.config.ui;

import com.mlh.aero_player_tilt.SideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ModeIndicator {
    private static final int PADDING_RIGHT = 6;
    private static final int COLOR_CLIENT_SERVER = 0xFF57C15A;

    private ModeIndicator() {}

    public static void render(GuiGraphics gfx, int screenWidth, int topY, int mouseX, int mouseY) {
        if (SideManager.getSide() != SideManager.Side.CLIENT_SERVER) return;

        Component label = Component.translatable("aero_player_tilt.configuration.mode.clientServer");
        Component tooltip = Component.translatable("aero_player_tilt.configuration.mode.clientServer.tooltip");
        int color = COLOR_CLIENT_SERVER;

        Font font = Minecraft.getInstance().font;
        int textW = font.width(label);
        int x = screenWidth - PADDING_RIGHT - textW;

        gfx.drawString(font, label, x, topY, color);

        boolean hovered = mouseX >= x && mouseX <= x + textW
                && mouseY >= topY && mouseY <= topY + font.lineHeight;
        if (hovered) {
            gfx.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}
