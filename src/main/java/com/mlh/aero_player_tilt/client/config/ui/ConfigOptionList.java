package com.mlh.aero_player_tilt.client.config.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Entry> {
    public static final int ENTRY_H = 26;

    private static final int NOTICE_COLOR = 0xFFFFCC33;
    private static final int NOTICE_W = 11;
    private static final int NOTICE_H = 11;

    @Nullable private static Component pendingNotice;
    private static int pendingNoticeX;
    private static int pendingNoticeY;

    public static void renderNotice(GuiGraphics gfx, Font font) {
        if (pendingNotice == null) return;
        gfx.renderTooltip(font, font.split(pendingNotice, 220), pendingNoticeX, pendingNoticeY);
        pendingNotice = null;
    }

    public ConfigOptionList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void addPublicEntry(Entry entry) { super.addEntry(entry); }

    @Override
    protected int getRowTop(int index) {
        int top = this.getY() + 4 - (int) this.getScrollAmount();
        for (int i = 0; i < index; i++) {
            top += this.children().get(i).getItemHeight();
        }
        return top;
    }

    @Override
    public int getRowBottom(int index) {
        return getRowTop(index) + this.children().get(index).getItemHeight();
    }

   @Override
    public int getMaxScroll() {
        int contentHeight = children().stream()
                .mapToInt(Entry::getItemHeight)
                .sum() + 8;
        return Math.max(0, contentHeight - (this.height - 8));
    }

    @Override public int getRowWidth()             { return this.width - 20; }
    @Override protected int getScrollbarPosition() { return this.getRight() - 6; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        if (button == 0) {
            int scrollbarX = getScrollbarPosition();
            if (mouseX >= scrollbarX && mouseX < scrollbarX + 6) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        for (int i = 0; i < children().size(); i++) {
            int top    = getRowTop(i);
            int bottom = getRowBottom(i);
            if (mouseY >= top && mouseY < bottom) {
                Entry entry = children().get(i);
                if (entry.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(entry);
                    setDragging(true);
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        protected final String labelKey;
        protected final String tooltipKey;

        @Nullable private String noticeKey;
        @Nullable private BooleanSupplier noticeWhen;

        @Nullable private BooleanSupplier visibleWhen;

        protected Entry(String labelKey, String tooltipKey) {
            this.labelKey   = labelKey;
            this.tooltipKey = tooltipKey;
        }

        public Entry withVisibleWhen(BooleanSupplier when) {
            this.visibleWhen = when;
            return this;
        }

        public boolean visible() {
            return visibleWhen == null || visibleWhen.getAsBoolean();
        }

        public Entry withNotice(String noticeKey, BooleanSupplier when) {
            this.noticeKey  = noticeKey;
            this.noticeWhen = when;
            return this;
        }

        public abstract void saveSnapshot();

        public abstract void restoreSnapshot();

        public abstract void reset();

        public abstract boolean hasHardLimitViolation();

        public void inheritSnapshot(Entry donor) {}

        protected void drawLabel(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
            Minecraft mc = Minecraft.getInstance();
            Component label = Component.translatable(labelKey);
            gfx.drawString(mc.font, label, x + 4, y + (ENTRY_H - 8) / 2, 0xFFFFFF, false);

            if (noticeKey == null || noticeWhen == null || !noticeWhen.getAsBoolean()) return;

            int markX = x + 4 + mc.font.width(label) + 5;
            int markY = y + (ENTRY_H - NOTICE_H) / 2;
            drawNotice(gfx, markX, markY);

            if (mouseX >= markX && mouseX < markX + NOTICE_W
                    && mouseY >= markY && mouseY < markY + NOTICE_H) {
                pendingNotice  = Component.translatable(noticeKey);
                pendingNoticeX = mouseX;
                pendingNoticeY = mouseY;
            }
        }

        private static void drawNotice(GuiGraphics gfx, int x, int y) {
            int cx = x + NOTICE_W / 2;

            for (int row = 0; row < NOTICE_H; row++) {
                int half = row * (NOTICE_W / 2) / (NOTICE_H - 1);
                gfx.fill(cx - half, y + row, cx + half + 1, y + row + 1, NOTICE_COLOR);
            }

            gfx.fill(cx, y + 4, cx + 1, y + 8, 0xFF000000);
            gfx.fill(cx, y + 9, cx + 1, y + 10, 0xFF000000);
        }

        public int getItemHeight() { return visible() ? ENTRY_H : 0; }
    }
}
