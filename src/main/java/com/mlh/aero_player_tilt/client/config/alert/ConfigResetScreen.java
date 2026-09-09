package com.mlh.aero_player_tilt.client.config.alert;

import com.mlh.aero_player_tilt.client.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import static com.mlh.aero_player_tilt.client.config.Config.*;

public class ConfigResetScreen extends Screen {
    private final Screen parent;

    public ConfigResetScreen(Screen parent) {
        super(Component.translatable("screen.aero_player_tilt.config_reset.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.aero_player_tilt.config_reset.yes"),
                btn -> {
                    resetConfig();
                    ConfigMigrationManager.markAccepted();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(this.width / 2 - 155, this.height / 2 + 10, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.aero_player_tilt.config_reset.no"),
                btn -> {
                    ConfigMigrationManager.markAccepted();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(this.width / 2 + 5, this.height / 2 + 10, 150, 20).build());
    }

    public static void resetConfig() {
        resetAllToDefaults(Config.SPEC);
        Config.CONFIG_SCHEMA_VERSION.set(Config.CURRENT_CONFIG_SCHEMA);
        Config.SPEC.save();
    }

    private static void resetAllToDefaults(ModConfigSpec spec) {
        resetAllIn(spec.getValues());
    }

    @SuppressWarnings("unchecked")
    private static void resetAllIn(com.electronwill.nightconfig.core.UnmodifiableConfig values) {
        values.valueMap().values().forEach(obj -> {
            if (obj instanceof ModConfigSpec.ConfigValue<?> cv) {
                resetOne((ModConfigSpec.ConfigValue<Object>) cv);
            } else if (obj instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) {
                resetAllIn(nested);
            }
        });
    }

    private static <T> void resetOne(ModConfigSpec.ConfigValue<T> cv) {
        cv.set(cv.getDefault());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("screen.aero_player_tilt.config_reset.body1"),
                this.width / 2 - 140, this.height / 2 - 10, 0xCCCCCC);
        graphics.drawString(this.font,
                Component.translatable("screen.aero_player_tilt.config_reset.body2"),
                this.width / 2 - 140, this.height / 2 - 22, 0xCCCCCC);
    }
}
