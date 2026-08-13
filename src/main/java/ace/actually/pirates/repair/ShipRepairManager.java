package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.entity.MotionInvokingBlockEntity;
import g_mungus.vlib.v2.api.extension.ShipExtKt;
import kotlin.Unit;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.HashMap;
import java.util.Map;

/** Runtime index plus repair operations keyed by the persistent Valkyrien Skies ship id. */
public final class ShipRepairManager {
    private static final Map<Key, BlockPos> CONTROLLERS = new HashMap<>();

    private ShipRepairManager() {}

    public static void register(ServerWorld world, long shipId, MotionInvokingBlockEntity controller) {
        CONTROLLERS.put(key(world, shipId), controller.getPos().toImmutable());
    }

    public static QuoteResult quote(ServerPlayerEntity player, Entity boatswain) {
        ShipTarget target = resolveTarget(player.getServerWorld(), boatswain);
        if (target.error() != null) return new QuoteResult(null, target.error());
        MotionInvokingBlockEntity.RepairQuote quote = createRepairQuote(player.getServerWorld(), target);
        if (quote == null) return new QuoteResult(null, "Could not compare this ship with the Eureka blueprints.");
        if (quote.beyondSaving()) return new QuoteResult(null, "This ship is beyond saving");
        return new QuoteResult(quote, null);
    }

    public static RepairResult payAndRepair(ServerPlayerEntity player, Entity boatswain) {
        ShipTarget target = resolveTarget(player.getServerWorld(), boatswain);
        if (target.error() != null) return new RepairResult(-1, false, target.error());

        // Recompute at click time so the client quote cannot become authoritative or stale.
        MotionInvokingBlockEntity.RepairQuote quote = createRepairQuote(player.getServerWorld(), target);
        if (quote == null) return new RepairResult(-1, false, "Could not compare this ship with the Eureka blueprints.");
        if (quote.beyondSaving()) return RepairResult.BEYOND_SAVING;
        if (quote.repairableBlocks() == 0) return RepairResult.NO_DAMAGE;

        int price = quote.goldCost();
        if (!player.isCreative() && player.getInventory().count(Items.GOLD_INGOT) < price) {
            return RepairResult.NOT_ENOUGH_GOLD;
        }

        int repaired = repairImmediately(player.getServerWorld(), target, quote);
        if (repaired < 0) return new RepairResult(-1, false, "The matched blueprint could not be loaded.");
        if (repaired == 0) return RepairResult.NO_DAMAGE;
        if (!player.isCreative()) removeGold(player, price);
        return new RepairResult(repaired, true, "Repaired " + repaired + " ship blocks for " + price + " gold.");
    }

    private static MotionInvokingBlockEntity.RepairQuote createRepairQuote(ServerWorld world, ShipTarget target) {
        PiratesShipBlueprintState state = PiratesShipBlueprintState.get(world);
        PiratesShipBlueprintState.BlueprintRecord saved = state.get(world, target.ship().getId());
        boolean existing = saved != null;
        ShipBlueprint blueprint;
        BlockPos anchor;
        if (saved != null) {
            blueprint = ShipBlueprint.load(world, saved.blueprintId(), saved.rotation()).orElse(null);
            anchor = saved.controllerAnchor();
        } else {
            ShipBlueprint.ShipMatch match = ShipBlueprint.match(world, target.ship()).orElse(null);
            if (match == null) return null;
            blueprint = match.blueprint();
            anchor = match.anchor();
            state.put(world, target.ship().getId(), blueprint.id(), blueprint.rotation(), anchor);
        }
        if (blueprint == null) return null;

        int eligible = 0;
        int repairable = 0;
        for (ShipBlueprint.Entry entry : blueprint.entries()) {
            if (RepairExclusions.isExcluded(entry.state(), entry.hasBlockEntity())) continue;
            BlockPos pos = anchor.add(entry.relativePos());
            if (!world.isChunkLoaded(pos)) continue;
            eligible++;
            if (RepairExclusions.needsRepair(world.getBlockState(pos), entry.state())) repairable++;
        }
        boolean beyondSaving = eligible > 0 && (long) repairable * 100L >= (long) eligible * 80L;
        int goldCost = repairable == 0 || eligible == 0 ? 0
                : Math.max(1, (int) Math.ceil((double) Pirates.shipRepairGoldCost * repairable / eligible));
        return new MotionInvokingBlockEntity.RepairQuote(
                blueprint.id(), blueprint.rotation(), repairable, eligible, goldCost, beyondSaving, existing);
    }

    private static int repairImmediately(ServerWorld world, ShipTarget target,
                                         MotionInvokingBlockEntity.RepairQuote quote) {
        ShipBlueprint blueprint = ShipBlueprint.load(world, quote.blueprintId(), quote.rotation()).orElse(null);
        PiratesShipBlueprintState.BlueprintRecord saved =
                PiratesShipBlueprintState.get(world).get(world, target.ship().getId());
        if (blueprint == null || saved == null) return -1;
        BlockPos anchor = saved.controllerAnchor();

        int repaired = 0;
        for (ShipBlueprint.Entry entry : blueprint.entries()) {
            if (RepairExclusions.isExcluded(entry.state(), entry.hasBlockEntity())) continue;
            BlockPos pos = anchor.add(entry.relativePos());
            if (!world.isChunkLoaded(pos)) continue;
            BlockState current = world.getBlockState(pos);
            if (RepairExclusions.needsRepair(current, entry.state())) {
                world.setBlockState(pos, RepairExclusions.repairState(entry.state()), net.minecraft.block.Block.NOTIFY_ALL);
                repaired++;
            }
        }
        return repaired;
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

    private static ShipTarget resolveTarget(ServerWorld world, Entity boatswain) {
        Ship ship = resolveEntityShip(world, boatswain);
        if (ship == null) return new ShipTarget(null, null,
                "Could not resolve the Valkyrien ship supporting this boatswain.");

        PiratesShipBlueprintState.BlueprintRecord saved =
                PiratesShipBlueprintState.get(world).get(world, ship.getId());
        return new ShipTarget(ship, saved == null ? null : saved.controllerAnchor(), null);
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
            if (found[0] == null && world.getBlockEntity(blockPos) instanceof MotionInvokingBlockEntity controller) {
                found[0] = controller;
            }
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
        public static final RepairResult BEYOND_SAVING = new RepairResult(-1, false, "This ship is beyond saving");
    }
    private record ShipTarget(Ship ship, BlockPos anchor, String error) {}
    private record Key(String dimension, long shipId) {}
}