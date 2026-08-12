package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.entity.MotionInvokingBlockEntity;
import g_mungus.vlib.v2.api.extension.ShipExtKt;
import kotlin.Unit;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
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

    public static MotionInvokingBlockEntity controller(ServerWorld world, long shipId) {
        Key key = key(world, shipId);
        BlockPos pos = CONTROLLERS.get(key);
        if (pos == null) return null;
        if (world.getBlockEntity(pos) instanceof MotionInvokingBlockEntity controller) return controller;
        CONTROLLERS.remove(key);
        return null;
    }

    public static RepairResult repair(ServerPlayerEntity player, Entity boatswain, Hand hand, net.minecraft.util.Identifier blueprintId) {
        if (!(player.getWorld() instanceof ServerWorld world)) return RepairResult.NO_SHIP;
        Ship ship = resolveEntityShip(world, boatswain);
        if (ship == null) return RepairResult.NO_SHIP;
        MotionInvokingBlockEntity controller = controller(world, ship.getId());
        if (controller == null) controller = findController(world, ship);
        if (controller == null) return RepairResult.NO_CONTROLLER;

        ItemStack payment = player.getStackInHand(hand);
        int price = Pirates.shipRepairGoldCost;
        if (!payment.isOf(Items.GOLD_INGOT) || payment.getCount() < price) {
            Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
            ItemStack otherPayment = player.getStackInHand(otherHand);
            if (!otherPayment.isOf(Items.GOLD_INGOT) || otherPayment.getCount() < price) {
                return RepairResult.NOT_ENOUGH_GOLD;
            }
            payment = otherPayment;
        }

        int repaired = controller.repairImmediately(blueprintId);
        if (repaired < 0) return new RepairResult(-1, false, "Selected blueprint resource is unavailable: " + blueprintId);
        if (repaired == 0) return RepairResult.NO_DAMAGE;
        if (!player.isCreative()) payment.decrement(price);
        return new RepairResult(repaired, true, "Repaired " + repaired + " ship blocks for " + price + " gold.");
    }

    private static Ship resolveEntityShip(ServerWorld world, Entity entity) {
        Ship direct = VSGameUtilsKt.getShipMountedTo(entity);
        if (direct == null) direct = VSGameUtilsKt.getShipManaging(entity);
        if (direct != null) return direct;

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        Ship best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Ship candidate : VSGameUtilsKt.getAllShips(world)) {
            var box = candidate.getWorldAABB();
            double margin = 4.0;
            if (x < box.minX() - margin || x > box.maxX() + margin
                    || y < box.minY() - margin || y > box.maxY() + margin
                    || z < box.minZ() - margin || z > box.maxZ() + margin) continue;
            var center = candidate.getTransform().getPositionInWorld();
            double distance = center.distanceSquared(x, y, z);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
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

    public record RepairResult(int repairedBlocks, boolean success, String message) {
        public static final RepairResult NO_SHIP = new RepairResult(-1, false, "Could not resolve the Valkyrien ship supporting this boatswain.");
        public static final RepairResult NO_CONTROLLER = new RepairResult(-1, false, "This ship has no loaded Motion Invoking Block repair controller.");
        public static final RepairResult NOT_ENOUGH_GOLD = new RepairResult(-1, false, "Hold the full repair cost in gold ingots when right-clicking.");
        public static final RepairResult NO_DAMAGE = new RepairResult(0, false, "No eligible blueprint blocks need repair.");
    }

    private record Key(String dimension, long shipId) {}
}