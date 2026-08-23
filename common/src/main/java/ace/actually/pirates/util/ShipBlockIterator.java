package ace.actually.pirates.util;

import net.minecraft.util.math.BlockPos;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.function.Consumer;

/** Iterates ship-space blocks without depending on an addon library. */
public final class ShipBlockIterator {
    private ShipBlockIterator() {
    }

    public static void forEachBlock(Ship ship, Consumer<BlockPos> consumer) {
        AABBic bounds = ship.getShipAABB();
        if (bounds == null || ship.getActiveChunksSet().getSize() == 0) return;

        ship.getActiveChunksSet().forEach((chunkX, chunkZ) -> {
            int minX = Math.max(bounds.minX(), chunkX << 4);
            int maxX = Math.min(bounds.maxX(), (chunkX << 4) + 15);
            int minZ = Math.max(bounds.minZ(), chunkZ << 4);
            int maxZ = Math.min(bounds.maxZ(), (chunkZ << 4) + 15);
            if (minX > maxX || minZ > maxZ) return;

            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        consumer.accept(mutable.set(x, y, z));
                    }
                }
            }
        });
    }
}