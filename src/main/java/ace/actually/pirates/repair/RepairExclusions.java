package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.util.ConfigUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared eligibility policy used by direct blueprint comparison and repair. */
public final class RepairExclusions {
    private static Set<Identifier> configuredIds;

    private RepairExclusions() {}

    public static boolean isExcluded(BlockState state, boolean templateHasBlockEntity) {
        if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) return true;
        if (state.hasBlockEntity() || templateHasBlockEntity) return true;
        if (state.isOf(Pirates.MOTION_INVOKING_BLOCK)) return true;
        return configuredIds().contains(Registries.BLOCK.getId(state.getBlock()));
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