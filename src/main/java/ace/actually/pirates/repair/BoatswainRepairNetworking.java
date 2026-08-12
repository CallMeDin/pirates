package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.entities.friendly_pirate.FriendlyPirateEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class BoatswainRepairNetworking {
    private BoatswainRepairNetworking() {}

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(Pirates.SELECT_BOATSWAIN_BLUEPRINT_PACKET_ID,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readVarInt();
                    Hand hand = buf.readEnumConstant(Hand.class);
                    Identifier blueprintId = buf.readIdentifier();
                    server.execute(() -> handleSelection(player, entityId, hand, blueprintId));
                });
    }

    public static void openSelection(ServerPlayerEntity player, FriendlyPirateEntity boatswain, Hand hand) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(boatswain.getId());
        buf.writeEnumConstant(hand);
        ServerPlayNetworking.send(player, Pirates.OPEN_BOATSWAIN_REPAIR_PACKET_ID, buf);
    }

    private static void handleSelection(ServerPlayerEntity player, int entityId, Hand hand, Identifier blueprintId) {
        if (!ShipBlueprint.isEurekaBlueprint(blueprintId)) {
            player.sendMessage(Text.literal("Invalid Eureka repair blueprint."), true);
            return;
        }
        Entity target = player.getServerWorld().getEntityById(entityId);
        if (!(target instanceof FriendlyPirateEntity boatswain)
                || !boatswain.getPirateJob().equals("boatswain")
                || player.squaredDistanceTo(boatswain) > 64.0) {
            player.sendMessage(Text.literal("The boatswain is no longer in interaction range."), true);
            return;
        }
        ShipRepairManager.RepairResult result = ShipRepairManager.repair(player, boatswain, hand, blueprintId);
        player.sendMessage(Text.literal(result.message()), true);
    }
}