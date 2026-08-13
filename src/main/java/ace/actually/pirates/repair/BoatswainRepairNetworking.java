package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.entities.friendly_pirate.FriendlyPirateEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class BoatswainRepairNetworking {
    private BoatswainRepairNetworking() {}

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(Pirates.SELECT_BOATSWAIN_BLUEPRINT_PACKET_ID,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readVarInt();
                    server.execute(() -> handleRepair(player, entityId));
                });
    }

    public static void openSelection(ServerPlayerEntity player, FriendlyPirateEntity boatswain, net.minecraft.util.Hand ignoredHand) {
        ShipRepairManager.QuoteResult result = ShipRepairManager.quote(player, boatswain);
        if (result.error() != null) {
            player.sendMessage(Text.literal(result.error()), true);
            return;
        }
        var quote = result.quote();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(boatswain.getId());
        buf.writeIdentifier(quote.blueprintId());
        buf.writeVarInt(quote.repairableBlocks());
        buf.writeVarInt(quote.goldCost());
        buf.writeBoolean(quote.existingBlueprint());
        ServerPlayNetworking.send(player, Pirates.OPEN_BOATSWAIN_REPAIR_PACKET_ID, buf);
    }

    private static void handleRepair(ServerPlayerEntity player, int entityId) {
        Entity target = player.getServerWorld().getEntityById(entityId);
        if (!(target instanceof FriendlyPirateEntity boatswain)
                || !boatswain.getPirateJob().equals("boatswain")
                || player.squaredDistanceTo(boatswain) > 64.0) {
            player.sendMessage(Text.literal("The boatswain is no longer in interaction range."), true);
            return;
        }
        ShipRepairManager.RepairResult result = ShipRepairManager.payAndRepair(player, boatswain);
        player.sendMessage(Text.literal(result.message()), true);
    }
}