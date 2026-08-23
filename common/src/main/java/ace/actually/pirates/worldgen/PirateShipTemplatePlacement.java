package ace.actually.pirates.worldgen;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.entity.MotionInvokingBlockEntity;
import ace.actually.pirates.repair.PiratesShipBlueprintState;
import ace.actually.pirates.repair.ShipBlueprint;
import ace.actually.pirates.repair.ShipRepairManager;
import ace.actually.pirates.util.ShipBlockIterator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Places a generated template normally, then lets VS safely assemble its exact blocks. */
public final class PirateShipTemplatePlacement {
    private static final Set<PlacementKey> QUEUED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> PLACING_IN_SHIPYARD =
            ThreadLocal.withInitial(() -> false);

    private PirateShipTemplatePlacement() {
    }

    public static boolean isPlacingInShipyard() {
        return PLACING_IN_SHIPYARD.get();
    }

    public static void placeOrQueue(StructureTemplate template, Identifier templateId,
                                    ServerWorld world, BlockPos worldPos,
                                    StructurePlacementData originalSettings) {
        PlacementKey key = new PlacementKey(VSGameUtilsKt.getDimensionId(world), worldPos.asLong());
        if (!QUEUED.add(key)) return;

        Runnable task = () -> {
            boolean completed = false;
            try {
                completed = place(template, templateId, world, worldPos, originalSettings);
            } catch (RuntimeException exception) {
                Pirates.LOGGER.error("Failed to place generated ship template {} at {}", templateId, worldPos,
                        exception);
            } finally {
                if (!completed) QUEUED.remove(key);
            }
        };

        if (world.getServer().isOnThread()) task.run();
        else world.getServer().execute(task);
    }

    private static boolean place(StructureTemplate template, Identifier templateId,
                                 ServerWorld world, BlockPos worldPos,
                                 StructurePlacementData originalSettings) {
        if (VSGameUtilsKt.isBlockInShipyard(world, worldPos)) return true;

        StructurePlacementData settings = originalSettings.copy().setBoundingBox(null);

        boolean placed;
        PLACING_IN_SHIPYARD.set(true);
        try {
            placed = template.place(world, worldPos, worldPos, settings,
                    world.getRandom(), 2);
        } finally {
            PLACING_IN_SHIPYARD.remove();
        }
        if (!placed) {
            Pirates.LOGGER.warn("Generated ship template {} placed no blocks at {}", templateId, worldPos);
            return false;
        }

        Set<BlockPos> blocks = ShipBlueprint.placedNonAirPositions(world, template, worldPos, settings);
        if (blocks.isEmpty()) {
            Pirates.LOGGER.warn("Generated ship template {} had no assembled blocks at {}", templateId, worldPos);
            return false;
        }

        ServerShip ship = ShipAssembler.assembleToShip(world, blocks, 1.0);
        if (ship == null) {
            Pirates.LOGGER.warn("VS rejected generated ship template {} at {}", templateId, worldPos);
            return false;
        }

        BlockPos[] controllerAnchor = {null};
        ShipBlockIterator.forEachBlock(ship, pos -> {
            if (controllerAnchor[0] == null && world.getBlockState(pos).isOf(Pirates.MOTION_INVOKING_BLOCK)) {
                controllerAnchor[0] = pos.toImmutable();
            }
        });
        if (controllerAnchor[0] != null) {
            PiratesShipBlueprintState.get(world).put(world, ship.getId(), templateId,
                    settings.getRotation() == null ? BlockRotation.NONE : settings.getRotation(),
                    controllerAnchor[0]);
            if (world.getBlockEntity(controllerAnchor[0]) instanceof MotionInvokingBlockEntity controller) {
                ShipRepairManager.register(world, ship.getId(), controller);
            }
        }

        Pirates.LOGGER.info("Placed and assembled generated ship {} as VS ship {} at {}",
                templateId, ship.getId(), worldPos);
        return true;
    }

    private record PlacementKey(String dimension, long blockPos) {
    }
}
