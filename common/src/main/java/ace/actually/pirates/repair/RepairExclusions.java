package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.MotionInvokingBlock;
import ace.actually.pirates.util.ConfigUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.valkyrienskies.eureka.block.ShipHelmBlock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared eligibility policy used by direct blueprint comparison and repair. */
public final class RepairExclusions {
    private static final Set<Block> VALUABLE_BLOCKS = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_BLOCK,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.EMERALD_BLOCK,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_BLOCK,
            Blocks.RAW_COPPER_BLOCK, Blocks.COPPER_BLOCK
    );
    private static Set<Identifier> configuredIds;

    private RepairExclusions() {}

    public static boolean isExcluded(BlockState state, boolean templateHasBlockEntity) {
        if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) return true;
        // This block has a block entity, but it must still be restored when destroyed.
        if (state.isOf(Pirates.MOTION_INVOKING_BLOCK)) return false;
        // Eureka helms also have block entities, but are essential ship structure.
        if (state.getBlock() instanceof ShipHelmBlock) return false;
        if (VALUABLE_BLOCKS.contains(state.getBlock())) return true;
        if (state.hasBlockEntity() || templateHasBlockEntity) return true;
        return configuredIds().contains(Registries.BLOCK.getId(state.getBlock()));
    }


    /** Repair is strictly additive: preserve every occupied position on the ship. */
    public static boolean needsRepair(BlockState current, BlockState blueprint) {
        return current.isAir() && !blueprint.isAir();
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