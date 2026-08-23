package ace.actually.pirates.mixin.compat;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Sails 0.3.2 from dereferencing a null world during structure placement. */
@Pseudo
@Mixin(targets = "com.quintonc.vs_sails.blocks.entity.BaseHelmBlockEntity", remap = false)
public abstract class SailsHelmBlockEntityMixin {
    @Inject(method = {"markRemoved", "method_5431"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void pirates$skipUnattachedRemoval(CallbackInfo ci) {
        if (((BlockEntity) (Object) this).getWorld() == null) ci.cancel();
    }
}