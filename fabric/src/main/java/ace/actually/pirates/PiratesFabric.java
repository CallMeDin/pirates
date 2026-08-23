package ace.actually.pirates;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;

public class PiratesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        registerBuiltinPacks();
        Pirates.init();
    }

    private static void registerBuiltinPacks() {
        ModContainer container = FabricLoader.getInstance().getModContainer(Pirates.MOD_ID).orElse(null);
        if (container == null) return;
        boolean sails = FabricLoader.getInstance().isModLoaded("vs_sails");
        boolean eureka = FabricLoader.getInstance().isModLoaded("vs_eureka");
        if (sails) {
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("sails_ships"), container,
                    ResourcePackActivationType.DEFAULT_ENABLED);
        }
        if (eureka) {
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("eureka_ships"), container,
                    sails ? ResourcePackActivationType.NORMAL : ResourcePackActivationType.DEFAULT_ENABLED);
            ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("flying_ships"), container,
                    ResourcePackActivationType.NORMAL);
        }
    }
}