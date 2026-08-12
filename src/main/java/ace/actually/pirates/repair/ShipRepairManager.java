package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.entity.MotionInvokingBlockEntity;
import g_mungus.vlib.v2.api.extension.ShipExtKt;
import kotlin.Unit;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.HashMap;
import java.util.Map;

/** Runtime index from loaded VS ships to their persistent repair controller. */
public final class ShipRepairManager {
    private static final Map<Key, BlockPos> CONTROLLERS = new HashMap<>();

    private ShipRepairManager() {}

    public static void register(ServerWorld world, long shipId, MotionInvokingBlockEntity controller) {
        CONTROLLERS.put(key(world, shipId), controller.getPos().toImmutable());
    }

    public static QuoteResult quote(ServerPlayerEntity player, Entity boatswain) {
        ControllerResult target = resolveController(player.getServerWorld(), boatswain);
        if (target.error() != null) return new QuoteResult(null, target.error());
        MotionInvokingBlockEntity.RepairQuote quote = target.controller().createRepairQuote();
        return quote == null
                ? new QuoteResult(null, "Could not compare this ship with the Eureka blueprints.")
                : new QuoteResult(quote, null);
    }

    public static RepairResult payAndRepair(ServerPlayerEntity player, Entity boatswain) {
        ControllerResult target = resolveController(player.getServerWorld(), boatswain);
        if (target.error() != null) return new RepairResult(-1, false, target.error());

        // Recompute at click time so the client quote cannot become authoritative or stale.
        MotionInvokingBlockEntity.RepairQuote quote = target.controller().createRepairQuote();
        if (quote == null) return new RepairResult(-1, false, "Could not compare this ship with the Eureka blueprints.");
        if (quote.repairableBlocks() == 0) return RepairResult.NO_DAMAGE;

        int price = Pirates.shipRepairGoldCost;
        if (!player.isCreative() && player.getInventory().count(Items.GOLD_INGOT) < price) {
            return RepairResult.NOT_ENOUGH_GOLD;
        }

        int repaired = target.controller().repairImmediately(quote);
        if (repaired < 0) return new RepairResult(-1, false, "The matched blueprint could not be loaded.");
        if (repaired == 0) return RepairResult.NO_DAMAGE;
        if (!player.isCreative()) removeGold(player, price);
        return new RepairResult(repaired, true, "Repaired " + repaired + " ship blocks for " + price + " gold.");
    }

    private static void removeGold(ServerPlayerEntity player, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().size() && remaining > 0; slot++) {
            var stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.GOLD_INGOT)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.decrement(removed);
            remaining -= removed;
        }
        player.getInventory().markDirty();
    }

    private static ControllerResult resolveController(ServerWorld world, Entity boatswain) {
        Ship ship = resolveEntityShip(world, boatswain);
        if (ship == null) return new ControllerResult(null, "Could not resolve the Valkyrien ship supporting this boatswain.");
        MotionInvokingBlockEntity controller = controller(world, ship.getId());
        if (controller == null) controller = findController(world, ship);
        return controller == null
                ? new ControllerResult(null, "This ship has no loaded Motion Invoking Block repair controller.")
                : new ControllerResult(controller, null);
    }

    private static MotionInvokingBlockEntity controller(ServerWorld world, long shipId) {
        Key key = key(world, shipId);
        BlockPos pos = CONTROLLERS.get(key);
        if (pos == null) return null;
        if (world.getBlockEntity(pos) instanceof MotionInvokingBlockEntity controller) return controller;
        CONTROLLERS.remove(key);
        return null;
    }

    private static Ship resolveEntityShip(ServerWorld world, Entity entity) {
        Ship direct = VSGameUtilsKt.getShipMountedTo(entity);
        if (direct == null) direct = VSGameUtilsKt.getShipManaging(entity);
        if (direct != null) return direct;
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        Ship best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Ship candidate : VSGameUtilsKt.getAllShips(world)) {
            var box = candidate.getWorldAABB();
            double margin = 4.0;
            if (x < box.minX() - margin || x > box.maxX() + margin
                    || y < box.minY() - margin || y > box.maxY() + margin
                    || z < box.minZ() - margin || z > box.maxZ() + margin) continue;
            double distance = candidate.getTransform().getPositionInWorld().distanceSquared(x, y, z);
            if (distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }

    private static MotionInvokingBlockEntity findController(ServerWorld world, Ship ship) {
        MotionInvokingBlockEntity[] found = new MotionInvokingBlockEntity[1];
        ShipExtKt.forEachBlock(ship, blockPos -> {
            if (found[0] == null && world.getBlockEntity(blockPos) instanceof MotionInvokingBlockEntity controller) found[0] = controller;
            return Unit.INSTANCE;
        });
        if (found[0] != null) register(world, ship.getId(), found[0]);
        return found[0];
    }

    private static Key key(ServerWorld world, long shipId) {
        return new Key(world.getRegistryKey().getValue().toString(), shipId);
    }

    public record QuoteResult(MotionInvokingBlockEntity.RepairQuote quote, String error) {}
    public record RepairResult(int repairedBlocks, boolean success, String message) {
        public static final RepairResult NOT_ENOUGH_GOLD = new RepairResult(-1, false, "Insufficient gold");
        public static final RepairResult NO_DAMAGE = new RepairResult(0, false, "No eligible blueprint blocks need repair.");
    }
    private record ControllerResult(MotionInvokingBlockEntity controller, String error) {}
    private record Key(String dimension, long shipId) {}
}