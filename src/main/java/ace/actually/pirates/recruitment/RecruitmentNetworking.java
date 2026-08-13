package ace.actually.pirates.recruitment;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.entities.friendly_pirate.FriendlyPirateEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.village.VillagerProfession;

import java.util.List;
import java.util.function.Supplier;

public final class RecruitmentNetworking {
    private RecruitmentNetworking() {}

    public record Offer(String id, String name, Supplier<ItemStack> price, Supplier<ItemStack> contract) {}
    private static final List<Offer> OFFERS = List.of(
            new Offer("cannoneer", "Cannoneer", () -> Pirates.recruitCost.get(), () -> new ItemStack(Pirates.CANNONEER_ITEM)),
            new Offer("doctor", "Doctor", () -> Pirates.doctorRecruitCost.get(), () -> new ItemStack(Pirates.DOCTOR_ITEM)),
            new Offer("boatswain", "Boatswain", () -> Pirates.boatswainRecruitCost.get(), () -> new ItemStack(Pirates.BOATSWAIN_ITEM))
    );

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(Pirates.SELECT_RECRUIT_PROFESSION_PACKET_ID,
                (server, player, handler, buf, sender) -> {
                    int entityId = buf.readVarInt();
                    String profession = buf.readString(32);
                    server.execute(() -> recruit(player, entityId, profession));
                });
    }

    public static boolean isEligible(Entity entity) {
        if (entity instanceof VillagerEntity villager) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            return !villager.isBaby() && (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT);
        }
        if (!(entity instanceof FriendlyPirateEntity pirate) || pirate.hasCustomName()) return false;
        String job = pirate.getPirateJob();
        return job == null || job.isBlank() || "none".equals(job);
    }

    public static void open(ServerPlayerEntity player, Entity target) {
        if (!isEligible(target)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(target.getId());
        buf.writeVarInt(OFFERS.size());
        for (Offer offer : OFFERS) {
            ItemStack price = offer.price().get();
            buf.writeString(offer.id());
            buf.writeString(offer.name());
            buf.writeIdentifier(net.minecraft.registry.Registries.ITEM.getId(price.getItem()));
            buf.writeVarInt(price.getCount());
        }
        ServerPlayNetworking.send(player, Pirates.OPEN_RECRUITMENT_PACKET_ID, buf);
    }

    private static void recruit(ServerPlayerEntity player, int entityId, String profession) {
        Entity target = player.getServerWorld().getEntityById(entityId);
        if (target == null || !isEligible(target) || player.squaredDistanceTo(target) > 64.0) {
            player.sendMessage(Text.literal("Recruit is no longer available."), true);
            return;
        }
        Offer offer = OFFERS.stream().filter(candidate -> candidate.id().equals(profession)).findFirst().orElse(null);
        if (offer == null) return;
        ItemStack price = offer.price().get();
        if (!player.isCreative() && player.getInventory().count(price.getItem()) < price.getCount()) {
            player.sendMessage(Text.literal("Insufficient payment"), true);
            return;
        }
        if (!player.isCreative()) removePayment(player, price);
        player.giveItemStack(offer.contract().get());
        target.discard();
        player.sendMessage(Text.literal("Recruited " + offer.name() + "."), true);
    }

    private static void removePayment(ServerPlayerEntity player, ItemStack price) {
        int remaining = price.getCount();
        for (int slot = 0; slot < player.getInventory().size() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(price.getItem())) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.decrement(removed);
            remaining -= removed;
        }
        player.getInventory().markDirty();
    }
}