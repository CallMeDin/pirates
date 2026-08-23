package ace.actually.pirates.client;

import ace.actually.pirates.Pirates;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class BoatswainBlueprintScreen extends Screen {
    private final int boatswainEntityId;
    private final Identifier matchedBlueprint;
    private final int repairableBlocks;
    private final int goldCost;
    private final boolean existingBlueprint;

    public BoatswainBlueprintScreen(int boatswainEntityId, Identifier matchedBlueprint,
                                    int repairableBlocks, int goldCost, boolean existingBlueprint) {
        super(Text.literal("Boatswain Ship Repair"));
        this.boatswainEntityId = boatswainEntityId;
        this.matchedBlueprint = matchedBlueprint;
        this.repairableBlocks = repairableBlocks;
        this.goldCost = goldCost;
        this.existingBlueprint = existingBlueprint;
    }

    @Override
    protected void init() {
        ButtonWidget repair = ButtonWidget.builder(
                        Text.literal(repairableBlocks > 0 ? "Pay " + goldCost + " Gold and Repair" : "No Repairs Needed"),
                        button -> requestRepair())
                .dimensions(width / 2 - 90, height / 2 + 34, 180, 20)
                .build();
        repair.active = repairableBlocks > 0;
        addDrawableChild(repair);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(width / 2 - 90, height / 2 + 60, 180, 20)
                .build());
    }

    private void requestRepair() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(boatswainEntityId);
        ClientPlayNetworking.send(Pirates.SELECT_BOATSWAIN_BLUEPRINT_PACKET_ID, buf);
        close();
    }

    private String blueprintName() {
        String path = matchedBlueprint.getPath().substring("ship/".length());
        StringBuilder result = new StringBuilder();
        for (String word : path.split("-")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int center = width / 2;
        int top = height / 2 - 54;
        context.fill(center - 120, top - 12, center + 120, top + 116, 0xDD101820);
        context.drawCenteredTextWithShadow(textRenderer, title, center, top, 0xFFE0B84F);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(existingBlueprint ? "Exist blueprint found" : "Traverse to find blueprint"),
                center, top + 18, existingBlueprint ? 0xFF77DD77 : 0xFFFFC857);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Matched blueprint: " + blueprintName()), center, top + 34, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Blocks that can be repaired: " + repairableBlocks), center, top + 50, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Repair cost: " + goldCost + " gold ingots"), center, top + 66, 0xFFFFD65A);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}