package ace.actually.pirates.mixin.worldgen;

import ace.actually.pirates.worldgen.PirateShipTemplateRegistry;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    @Inject(method = "getTemplate", at = @At("RETURN"))
    private void pirates$markShipTemplate(Identifier id,
                                          CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        cir.getReturnValue().ifPresent(template -> PirateShipTemplateRegistry.mark(template, id));
    }

    /** Jigsaw pool elements use this non-optional lookup path. */
    @Inject(method = "getTemplateOrBlank", at = @At("RETURN"))
    private void pirates$markShipTemplateOrBlank(Identifier id,
                                                  CallbackInfoReturnable<StructureTemplate> cir) {
        PirateShipTemplateRegistry.mark(cir.getReturnValue(), id);
    }
}
