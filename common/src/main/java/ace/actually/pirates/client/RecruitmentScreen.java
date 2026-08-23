package ace.actually.pirates.client;

import ace.actually.pirates.Pirates;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class RecruitmentScreen extends Screen {
    public record Offer(String id, String name, Item item, int count) {}
    private final int entityId;
    private final List<Offer> offers;

    public RecruitmentScreen(int entityId, List<Offer> offers) {
        super(Text.literal("Crew Recruitment"));
        this.entityId = entityId;
        this.offers = offers;
    }

    @Override
    protected void init() {
        int top = height / 2 - offers.size() * 13;
        for (int i = 0; i < offers.size(); i++) {
            Offer offer = offers.get(i);
            String price = offer.count() + " " + offer.item().getName().getString();
            addDrawableChild(ButtonWidget.builder(Text.literal(offer.name() + " — " + price),
                    button -> select(offer.id())).dimensions(width / 2 - 110, top + i * 26, 220, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(width / 2 - 110, top + offers.size() * 26 + 8, 220, 20).build());
    }

    private void select(String profession) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeString(profession);
        ClientPlayNetworking.send(Pirates.SELECT_RECRUIT_PROFESSION_PACKET_ID, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int top = height / 2 - offers.size() * 13;
        context.fill(width / 2 - 128, top - 34, width / 2 + 128,
                top + offers.size() * 26 + 40, 0xDD101820);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 24, 0xFFE0B84F);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Choose a profession"),
                width / 2, top - 10, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}