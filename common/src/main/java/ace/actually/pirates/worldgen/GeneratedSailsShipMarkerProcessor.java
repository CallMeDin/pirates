package ace.actually.pirates.worldgen;

import ace.actually.pirates.Pirates;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.StructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public final class GeneratedSailsShipMarkerProcessor extends StructureProcessor {
    public static final GeneratedSailsShipMarkerProcessor INSTANCE = new GeneratedSailsShipMarkerProcessor();
    public static final Codec<GeneratedSailsShipMarkerProcessor> CODEC = Codec.unit(INSTANCE);

    private GeneratedSailsShipMarkerProcessor() {}

    @Override
    public StructureTemplate.StructureBlockInfo process(WorldView world, BlockPos pos, BlockPos pivot,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
            StructurePlacementData placementData) {
        if (!current.state().isOf(Pirates.MOTION_INVOKING_BLOCK)) return current;
        NbtCompound nbt = current.nbt() == null ? new NbtCompound() : current.nbt().copy();
        nbt.putBoolean("piratesGeneratedSailsShip", true);
        return new StructureTemplate.StructureBlockInfo(current.pos(), current.state(), nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Pirates.GENERATED_SAILS_SHIP_MARKER_PROCESSOR;
    }
}