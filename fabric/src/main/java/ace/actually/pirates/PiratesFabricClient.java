package ace.actually.pirates;

import net.fabricmc.api.ClientModInitializer;

public final class PiratesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPirates.init();
    }
}