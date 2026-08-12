package ace.actually.pirates.client;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.repair.ShipBlueprint;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class BoatswainBlueprintScreen extends Screen {
    private final int boatswainEntityId;
    private final Hand hand;

    public BoatswainBlueprintScreen(int boatswainEntityId, Hand hand) {
        super(Text.literal("Choose Ship Blueprint"));
        this.boatswainEntityId = boatswainEntityId;
        this.hand = hand;
    }

    @Override
    protected void init() {
        int buttonWidth = 150;
        int buttonHeight = 20;
        int gap = 6;
        int columns = 2;
        int totalWidth = buttonWidth * columns + gap;
        int startX = (width - totalWidth) / 2;
        int startY = Math.max(48, (height - 6 * 24) / 2);

        var ids = ShipBlueprint.eurekaBlueprintIds();
        for (int i = 0; i < ids.size(); i++) {
            Identifier id = ids.get(i);
            int column = i % columns;
            int row = i / columns;
            addDrawableChild(ButtonWidget.builder(Text.literal(displayName(id)), button -> select(id))
                    .dimensions(startX + column * (buttonWidth + gap), startY + row * 24, buttonWidth, buttonHeight)
                    .build());
        }
    }

    private void select(Identifier id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(boatswainEntityId);
        buf.writeEnumConstant(hand);
        buf.writeIdentifier(id);
        ClientPlayNetworking.send(Pirates.SELECT_BOATSWAIN_BLUEPRINT_PACKET_ID, buf);
        close();
    }

    private static String displayName(Identifier id) {
        String[] words = id.getPath().substring("ship/".length()).split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Select the original Eureka ship - repair cost: " + Pirates.shipRepairGoldCost + " gold"),
                width / 2, 31, 0xE0B84F);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}