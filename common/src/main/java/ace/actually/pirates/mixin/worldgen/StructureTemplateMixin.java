package ace.actually.pirates.mixin.worldgen;

import ace.actually.pirates.worldgen.PirateShipTemplatePlacement;
import ace.actually.pirates.worldgen.PirateShipTemplateRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void pirates$placeTemplateAsShip(ServerWorldAccess world, BlockPos pos, BlockPos pivot,
                                             StructurePlacementData settings, Random random, int flags,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (PirateShipTemplatePlacement.isPlacingInShipyard()) return;

        StructureTemplate template = (StructureTemplate) (Object) this;
        var templateId = PirateShipTemplateRegistry.idOf(template);
        if (templateId.isEmpty()) return;

        ServerWorld serverWorld = world.toServerWorld();
        if (VSGameUtilsKt.isBlockInShipyard(serverWorld, pos)) return;

        PirateShipTemplatePlacement.placeOrQueue(template, templateId.get(), serverWorld,
                pos.toImmutable(), settings.copy());
        cir.setReturnValue(true);
    }
}
