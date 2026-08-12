package ace.actually.pirates.mixin.repair;

import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/** Stamps the source template ID into the repair controller before VLib assembles the ship. */
@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    private static final Set<String> PIRATES_SHIP_NAMESPACES = Set.of("pirates_eureka", "pirates_sails", "pirates_sky");

    @Shadow @Final private RegistryEntryLookup<Block> blockLookup;

    @Inject(method = "getTemplate", at = @At("RETURN"))
    private void pirates$attachRepairBlueprint(Identifier id,
                                                CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        if (!PIRATES_SHIP_NAMESPACES.contains(id.getNamespace()) || !id.getPath().startsWith("ship/")) return;
        cir.getReturnValue().ifPresent(template -> stamp(template, id));
    }

    private void stamp(StructureTemplate template, Identifier id) {
        NbtCompound root = template.writeNbt(new NbtCompound());
        NbtList palette;
        if (root.contains("palette", NbtElement.LIST_TYPE)) {
            palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        } else {
            NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);
            if (palettes.isEmpty()) return;
            palette = (NbtList) palettes.get(0);
        }

        int controllerState = -1;
        for (int i = 0; i < palette.size(); i++) {
            if (palette.getCompound(i).getString("Name").equals("pirates:motion_invoking_block")) {
                controllerState = i;
                break;
            }
        }
        if (controllerState < 0) return;

        boolean changed = false;
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            if (block.getInt("state") != controllerState) continue;
            NbtCompound blockEntityNbt = block.contains("nbt", NbtElement.COMPOUND_TYPE)
                    ? block.getCompound("nbt") : new NbtCompound();
            if (!blockEntityNbt.getString("repairBlueprintId").equals(id.toString())) {
                blockEntityNbt.putString("repairBlueprintId", id.toString());
                blockEntityNbt.putString("repairBlueprintRotation", "NONE");
                block.put("nbt", blockEntityNbt);
                changed = true;
            }
        }
        if (changed) template.readNbt(blockLookup, root);
    }
}