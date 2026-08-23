package ace.actually.pirates.repair;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/** Pirates-owned world save data. Written to world/data/pirates_ship_blueprints.dat. */
public final class PiratesShipBlueprintState extends PersistentState {
    private static final String FILE_ID = "pirates_ship_blueprints";
    private final Map<String, BlueprintRecord> records = new HashMap<>();

    public static PiratesShipBlueprintState get(ServerWorld world) {
        MinecraftServer server = world.getServer();
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                PiratesShipBlueprintState::fromNbt,
                PiratesShipBlueprintState::new,
                FILE_ID
        );
    }

    public BlueprintRecord get(ServerWorld world, long shipId) {
        return records.get(key(world, shipId));
    }

    public void put(ServerWorld world, long shipId, Identifier blueprintId,
                    BlockRotation rotation, BlockPos controllerAnchor) {
        records.put(key(world, shipId), new BlueprintRecord(
                world.getRegistryKey().getValue().toString(), shipId,
                blueprintId, rotation, controllerAnchor.toImmutable()));
        markDirty();
    }

    private static String key(ServerWorld world, long shipId) {
        return world.getRegistryKey().getValue() + "|" + shipId;
    }

    private static PiratesShipBlueprintState fromNbt(NbtCompound root) {
        PiratesShipBlueprintState state = new PiratesShipBlueprintState();
        NbtList list = root.getList("ships", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            Identifier blueprintId = Identifier.tryParse(entry.getString("blueprint"));
            if (!ShipBlueprint.isRepairBlueprint(blueprintId)) continue;
            BlockRotation rotation;
            try {
                rotation = BlockRotation.valueOf(entry.getString("rotation"));
            } catch (IllegalArgumentException ignored) {
                rotation = BlockRotation.NONE;
            }
            String dimension = entry.getString("dimension");
            long shipId = entry.getLong("shipId");
            BlueprintRecord record = new BlueprintRecord(dimension, shipId, blueprintId, rotation,
                    BlockPos.fromLong(entry.getLong("anchor")));
            state.records.put(dimension + "|" + shipId, record);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root) {
        NbtList list = new NbtList();
        for (BlueprintRecord record : records.values()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("dimension", record.dimension());
            entry.putLong("shipId", record.shipId());
            entry.putString("blueprint", record.blueprintId().toString());
            entry.putString("rotation", record.rotation().name());
            entry.putLong("anchor", record.controllerAnchor().asLong());
            list.add(entry);
        }
        root.put("ships", list);
        return root;
    }

    public record BlueprintRecord(String dimension, long shipId, Identifier blueprintId,
                                  BlockRotation rotation, BlockPos controllerAnchor) {}
}