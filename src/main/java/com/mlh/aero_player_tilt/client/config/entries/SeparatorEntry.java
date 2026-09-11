package com.mlh.aero_player_tilt.client.config.entries;

import com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SeparatorEntry extends ConfigOptionList.Entry {
    private static final int LINE_COLOR    = 0x88AAAAAA;
    private static final int TEXT_COLOR    = 0xFFDDDDDD;

    private static final int HEIGHT_LINE_ONLY = 10;
    private static final int HEIGHT_WITH_TEXT = 18;

    private final boolean hasText;
    private final int     rowHeight;

    public SeparatorEntry() {
        super("", "");
        this.hasText   = false;
        this.rowHeight = HEIGHT_LINE_ONLY;
    }

    public SeparatorEntry(String labelKey) {
        super(labelKey, "");
        this.hasText   = !labelKey.isEmpty();
        this.rowHeight = hasText ? HEIGHT_WITH_TEXT : HEIGHT_LINE_ONLY;
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        if (!visible()) return;

        int lineY = top + rowHeight - 1;

        if (hasText) {
            Minecraft mc = Minecraft.getInstance();
            Component text = Component.translatable(labelKey);

            int textX = left + width / 2;
            int textY = top + (rowHeight - mc.font.lineHeight) / 2 - 1;
            gfx.drawCenteredString(mc.font, text, textX, textY, TEXT_COLOR);
        }

        gfx.fill(left, lineY, left + width, lineY + 1, LINE_COLOR);
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(); }

    @Override public void saveSnapshot()              {  }
    @Override public void restoreSnapshot()           {  }
    @Override public void reset()                     {  }
    @Override public boolean hasHardLimitViolation()  { return false; }
}
