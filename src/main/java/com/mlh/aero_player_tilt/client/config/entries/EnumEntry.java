package com.mlh.aero_player_tilt.client.config.entries;

import com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList;
import com.mlh.aero_player_tilt.client.config.ui.DimButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.mlh.aero_player_tilt.client.config.ui.ConfigOptionList.ENTRY_H;

public class EnumEntry<E extends Enum<E>> extends ConfigOptionList.Entry {
    private static final int SEG_H = 18;
    private static final int GAP = 2;

    private static final int MIN_SEG_W = 34;
    private static final int MAX_SEG_W = 104;

    private static final int SW_W = 40;
    private static final int SW_H = SEG_H;
    private static final int KNOB_W = 18;
    private static final int KNOB_H = SW_H - 2;
    private static final int SW_GAP = 4;

    private static final int BED = 0xFF000000;
    private static final int EDGE = 0xFF555555;

    private static final ResourceLocation KNOB_SPRITE =
            ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation KNOB_SPRITE_LIT =
            ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private interface CfgAdapter<E> {
        E    get();
        void set(E value);
        E    getDefault();
    }

    private final E[]           values;
    private final CfgAdapter<E> adapter;

    private final List<Segment> segments = new ArrayList<>();

    @Nullable private final Switch knob;

    private final List<AbstractWidget> widgets;

    private final List<Component> labels = new ArrayList<>();

    private final int segWidth;

    private E currentValue;
    private E snapshot;

    private final boolean locked;

    private int shown = -1;

    public EnumEntry(String labelKey, String tooltipKey,
                     ModConfigSpec.EnumValue<E> config,
                     E[] values,
                     Function<E, Component> labelOf) {
        this(labelKey, tooltipKey, config, values, labelOf, null);
    }

    public EnumEntry(String labelKey, String tooltipKey,
                     ModConfigSpec.EnumValue<E> config,
                     E[] values,
                     Function<E, Component> labelOf,
                     @Nullable Function<E, String> tooltipKeyOf) {
        this(labelKey, tooltipKey, config, values, labelOf, tooltipKeyOf, false);
    }

    public EnumEntry(String labelKey, String tooltipKey,
                     ModConfigSpec.EnumValue<E> config,
                     E[] values,
                     Function<E, Component> labelOf,
                     @Nullable Function<E, String> tooltipKeyOf,
                     boolean locked) {
        super(labelKey, tooltipKey);

        this.locked = locked;

        this.values  = values;
        this.adapter = new CfgAdapter<>() {
            public E    get()          { return config.get(); }
            public void set(E value)   { config.set(value); }
            public E getDefault()      { return config.getDefault(); }
        };

        this.currentValue = config.get();
        this.snapshot     = currentValue;

        Minecraft mc = Minecraft.getInstance();
        int widest = MIN_SEG_W;
        for (E value : values) {
            widest = Math.max(widest, mc.font.width(labelOf.apply(value)) + 12);
        }
        this.segWidth = Math.min(widest, MAX_SEG_W);

        boolean twoWay = values.length == 2;

        for (int i = 0; i < values.length; i++) {
            E value = values[i];
            Component label = labelOf.apply(value);

            labels.add(label);

            Segment segment = new Segment(i, label, btn -> choose(value));

            Component tip = tooltip(tooltipKey, tooltipKeyOf == null ? null : tooltipKeyOf.apply(value));
            if (tip != null) segment.setTooltip(Tooltip.create(tip));

            segments.add(segment);
        }

        if (twoWay) {
            this.knob = new Switch(btn -> choose(values[indexOf(currentValue) == 0 ? 1 : 0]));
            if (!tooltipKey.isEmpty()) {
                knob.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
        } else {
            this.knob = null;
        }

        this.widgets = new ArrayList<>(segments);
        if (knob != null) widgets.add(knob);

        for (AbstractWidget widget : widgets) widget.active = !locked;
    }

    @Nullable
    private static Component tooltip(String rowKey, @Nullable String optionKey) {
        boolean hasRow    = !rowKey.isEmpty();
        boolean hasOption = optionKey != null && I18n.exists(optionKey);

        if (!hasOption) return hasRow ? Component.translatable(rowKey) : null;
        if (!hasRow)    return Component.translatable(optionKey);

        MutableComponent tip = Component.translatable(rowKey);
        return tip.append(Component.literal("\n\n")).append(Component.translatable(optionKey));
    }

    private void choose(E value) {
        if (locked) return;

        currentValue = value;
        adapter.set(value);
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top, mouseX, mouseY);

        int chosen = indexOf(currentValue);
        if (knob != null && chosen != shown) {
            knob.setMessage(labels.get(chosen));
            shown = chosen;
        }

        int n = segments.size();
        int total = knob != null
                ? n * segWidth + 2 * SW_GAP + SW_W
                : n * segWidth + (n - 1) * GAP;

        int x = left + width - 4 - total;
        int y = top + (ENTRY_H - SEG_H) / 2;

        int cursor = x;
        for (int i = 0; i < n; i++) {
            if (knob != null && i == 1) {
                knob.setX(cursor + SW_GAP);
                knob.setY(y + (SEG_H - SW_H) / 2);
                cursor += SW_GAP + SW_W + SW_GAP;
            }

            Segment segment = segments.get(i);
            segment.setX(cursor);
            segment.setY(y);
            segment.setWidth(segWidth);
            segment.render(gfx, mouseX, mouseY, delta);

            cursor += segWidth + (knob != null ? 0 : GAP);
        }

        if (knob != null) knob.render(gfx, mouseX, mouseY, delta);
    }

    private int indexOf(E value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 0;
    }

    private final class Switch extends Button {
        private Switch(OnPress onPress) {
            super(0, 0, SW_W, SW_H, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();

            gfx.fill(x, y, x + SW_W, y + SW_H, EDGE);
            gfx.fill(x + 1, y + 1, x + SW_W - 1, y + SW_H - 1, BED);

            int knobX = indexOf(currentValue) == 0 ? x + 1 : x + SW_W - 1 - KNOB_W;
            gfx.blitSprite(isHoveredOrFocused() ? KNOB_SPRITE_LIT : KNOB_SPRITE,
                    knobX, y + 1, KNOB_W, KNOB_H);
        }
    }

    private final class Segment extends DimButton {
        private Segment(int index, Component label, OnPress onPress) {
            super(segWidth, SEG_H, label, onPress, () -> index != indexOf(currentValue));
        }
    }

    @Override public List<? extends GuiEventListener> children()    { return widgets; }
    @Override public List<? extends NarratableEntry>  narratables() { return widgets; }

    @Override public void saveSnapshot() { snapshot = currentValue; }

    @Override public void restoreSnapshot() {
        if (locked) return;
        currentValue = snapshot;
        adapter.set(snapshot);
    }

    @Override public void reset() {
        if (locked) return;
        E def = adapter.getDefault();
        currentValue = def;
        adapter.set(def);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof EnumEntry<?> d) this.snapshot = (E) d.snapshot;
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}
