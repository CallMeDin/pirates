package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.MotionInvokingBlock;
import ace.actually.pirates.util.ConfigUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.valkyrienskies.eureka.block.ShipHelmBlock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared eligibility policy used by direct blueprint comparison and repair. */
public final class RepairExclusions {
    private static Set<Identifier> configuredIds;

    private RepairExclusions() {}

    public static boolean isExcluded(BlockState state, boolean templateHasBlockEntity) {
        if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) return true;
        // This block has a block entity, but it must still be restored when destroyed.
        if (state.isOf(Pirates.MOTION_INVOKING_BLOCK)) return false;
        // Eureka helms also have block entities, but are essential ship structure.
        if (state.getBlock() instanceof ShipHelmBlock) return false;
        if (state.hasBlockEntity() || templateHasBlockEntity) return true;
        return configuredIds().contains(Registries.BLOCK.getId(state.getBlock()));
    }


    /**
     * Open/closed door and trapdoor states are player interaction, not ship damage.
     * A genuinely missing or replaced door still differs by block type and is repaired.
     */
    public static boolean needsRepair(BlockState current, BlockState blueprint) {
        if (current.equals(blueprint)) return false;
        // ARMED is runtime behavior, not structural damage. Repaired invokers stay disarmed.
        if (current.isOf(Pirates.MOTION_INVOKING_BLOCK)
                && blueprint.isOf(Pirates.MOTION_INVOKING_BLOCK)) return false;
        if (current.getBlock() == blueprint.getBlock()
                && (blueprint.getBlock() instanceof DoorBlock
                || blueprint.getBlock() instanceof TrapdoorBlock)) {
            return false;
        }
        return true;
    }
    /** Returns the safe state placed by ship repair. Manual item placement is unchanged. */
    public static BlockState repairState(BlockState blueprint) {
        return blueprint.isOf(Pirates.MOTION_INVOKING_BLOCK)
                ? blueprint.with(MotionInvokingBlock.ARMED, false)
                : blueprint;
    }

    private static Set<Identifier> configuredIds() {
        if (configuredIds == null) {
            configuredIds = new HashSet<>();
            String value = ConfigUtils.config.getOrDefault("ship-repair-excluded-blocks", "");
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Identifier::tryParse)
                    .filter(java.util.Objects::nonNull)
                    .forEach(configuredIds::add);
        }
        return configuredIds;
    }
}