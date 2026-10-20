package br.com.celestialdragon.client;

import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.network.DragonSettingsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Three labelled numeric values, confirmed by the server before any combat setting changes. */
public final class DragonSettingsScreen extends Screen {
    private final Screen parent;
    private EditBox health, bite, beam;
    private Button save;
    private String status = "Consultando os valores do servidor…";
    private boolean awaiting;
    private boolean initialSnapshot;
    private static DragonSettingsNetworking.Snapshot last;

    public DragonSettingsScreen(Screen parent) { super(Component.literal("Dragão Celestial")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 100, y = Math.max(44, height / 2 - 74);
        var values = last == null ? DragonConfig.DEFAULT : new DragonConfig.Values(last.health(), last.bite(), last.beam());
        health = field(x, y, "Vida máxima", values.maxHealth());
        bite = field(x, y + 39, "Dano da mordida", values.biteDamage());
        beam = field(x, y + 78, "Dano do raio por alvo", values.beamDamage());
        initialSnapshot = true;
        health.setEditable(false); bite.setEditable(false); beam.setEditable(false);
        save = addRenderableWidget(Button.builder(Component.literal("Salvar no servidor"), button -> apply())
                .bounds(x, y + 112, 200, 20).build());
        save.active = false;
        addRenderableWidget(Button.builder(Component.literal("Voltar"), button -> onClose())
                .bounds(x, y + 137, 200, 20).build());
        if (ClientPlayNetworking.canSend(DragonSettingsNetworking.Request.TYPE))
            ClientPlayNetworking.send(new DragonSettingsNetworking.Request(false, 0, 0, 0));
        else status = "O servidor precisa ter este mod instalado para configurar.";
    }

    private EditBox field(int x, int y, String label, double value) {
        EditBox field = new EditBox(font, x, y, 200, 20, Component.literal(label));
        field.setMaxLength(10);
        field.setValue(number(value));
        return addRenderableWidget(field);
    }
    private static String number(double value) { return value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value); }
    private void apply() {
        try {
            var values = new DragonConfig.Values(Double.parseDouble(health.getValue().replace(',', '.')),
                    Double.parseDouble(bite.getValue().replace(',', '.')), Double.parseDouble(beam.getValue().replace(',', '.')));
            if (!ClientPlayNetworking.canSend(DragonSettingsNetworking.Request.TYPE)) { status = "Sem conexão com o servidor."; return; }
            awaiting = true;
            save.active = false;
            status = "Salvando…";
            ClientPlayNetworking.send(new DragonSettingsNetworking.Request(true, values.maxHealth(), values.biteDamage(), values.beamDamage()));
        } catch (IllegalArgumentException invalid) { status = "Use números: vida 1–1024; danos 0–1000."; }
    }
    public static void receive(DragonSettingsNetworking.Snapshot snapshot) {
        last = snapshot;
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.gui.screen() instanceof DragonSettingsScreen screen) screen.update(snapshot);
    }
    private void update(DragonSettingsNetworking.Snapshot snapshot) {
        status = snapshot.message();
        save.active = snapshot.canEdit();
        health.setEditable(snapshot.canEdit()); bite.setEditable(snapshot.canEdit()); beam.setEditable(snapshot.canEdit());
        if (initialSnapshot || awaiting || !health.isFocused() && !bite.isFocused() && !beam.isFocused()) {
            health.setValue(number(snapshot.health())); bite.setValue(number(snapshot.bite())); beam.setValue(number(snapshot.beam()));
        }
        awaiting = false;
        initialSnapshot = false;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFD56A);
        graphics.text(font, "Vida máxima (1–1024)", health.getX(), health.getY() - 11, 0xFFFFFFFF);
        graphics.text(font, "Mordida (0–1000)", bite.getX(), bite.getY() - 11, 0xFFFFFFFF);
        graphics.text(font, "Raio por alvo (0–1000)", beam.getX(), beam.getY() - 11, 0xFFFFFFFF);
        graphics.textWithWordWrap(font, Component.literal(status), Math.max(8, width / 2 - 190), height - 32, Math.min(380, width - 16), 0xFFFFDC94);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
