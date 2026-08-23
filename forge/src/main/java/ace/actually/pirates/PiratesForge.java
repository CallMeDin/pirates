package ace.actually.pirates;

import dev.architectury.platform.forge.EventBuses;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.nio.file.Path;

@Mod(Pirates.MOD_ID)
public final class PiratesForge {
    public PiratesForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(Pirates.MOD_ID, modEventBus);
        modEventBus.addListener(PiratesForge::onClientSetup);
        modEventBus.addListener(PiratesForge::addPackFinders);
        Pirates.init();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientPirates::init);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != ResourceType.SERVER_DATA) return;
        boolean sails = ModList.get().isLoaded("vs_sails");
        boolean eureka = ModList.get().isLoaded("vs_eureka");
        if (sails) addBuiltinPack(event, "sails_ships", "Sails Ships", true);
        if (eureka) {
            addBuiltinPack(event, "eureka_ships", "Eureka Ships", !sails);
            addBuiltinPack(event, "flying_ships", "Flying Ships", false);
        }
    }

    private static void addBuiltinPack(AddPackFindersEvent event, String id, String title, boolean enabled) {
        Path root = ModList.get().getModFileById(Pirates.MOD_ID).getFile().findResource("resourcepacks", id);
        ResourcePackProfile profile = ResourcePackProfile.create(
                Pirates.MOD_ID + ":" + id, Text.literal(title), enabled,
                name -> new DirectoryResourcePack(name, root, true),
                ResourceType.SERVER_DATA, ResourcePackProfile.InsertionPosition.TOP,
                ResourcePackSource.BUILTIN);
        if (profile != null) event.addRepositorySource(consumer -> consumer.accept(profile));
    }
}