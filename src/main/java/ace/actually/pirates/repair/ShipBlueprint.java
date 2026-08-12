package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, controller-relative view of one bundled ship structure template. */
public final class ShipBlueprint {
    private static final BlockRotation[] ROTATIONS = BlockRotation.values();
    private static final List<Identifier> EUREKA_BLUEPRINT_IDS = List.of(
            id("anetum-contatum"), id("antelope"), id("barnacle-hopper"),
            id("deep-sea-moray"), id("eye-of-horus"), id("hispaniola"),
            id("midnight-barracuda"), id("queen-annes-revenge"), id("revenge"),
            id("whydah-ghost"), id("whydah")
    );

    private final Identifier id;
    private final BlockRotation rotation;
    private final Map<Long, Entry> entries;

    private ShipBlueprint(Identifier id, BlockRotation rotation, Map<Long, Entry> entries) {
        this.id = id;
        this.rotation = rotation;
        this.entries = Map.copyOf(entries);
    }

    public Identifier id() {
        return id;
    }

    public BlockRotation rotation() {
        return rotation;
    }

    public Entry entryAt(BlockPos relativePos) {
        return entries.get(relativePos.asLong());
    }

    public java.util.Collection<Entry> entries() {
        return entries.values();
    }

    public static List<Identifier> eurekaBlueprintIds() {
        return EUREKA_BLUEPRINT_IDS;
    }

    public static boolean isEurekaBlueprint(Identifier id) {
        return EUREKA_BLUEPRINT_IDS.contains(id);
    }

    public int size() {
        return entries.size();
    }

    public static Optional<ShipBlueprint> load(ServerWorld world, Identifier id, BlockRotation rotation) {
        return world.getStructureTemplateManager().getTemplate(id)
                .flatMap(template -> buildFromTemplate(world, id, template, rotation, null));
    }

    /**
     * Matches a template to an already assembled controller. VLib 0.1.1 places the raw
     * structure as a ship, so controller-relative offsets survive relocation into shipyard space.
     */
    public static Optional<ShipBlueprint> match(ServerWorld world, BlockPos controllerPos) {
        Match best = null;
        int loadedCandidates = 0;

        // Deliberately load every bundled Eureka NBT by its exact resource ID. Do not
        // depend on streamTemplates(), which may omit templates from built-in packs.
        for (Identifier id : EUREKA_BLUEPRINT_IDS) {
            Optional<StructureTemplate> optional = world.getStructureTemplateManager().getTemplate(id);
            if (optional.isEmpty()) {
                Pirates.LOGGER.warn("Eureka repair blueprint {} is not loaded", id);
                continue;
            }
            loadedCandidates++;
            List<RawEntry> rawEntries = decode(world, optional.get());
            List<RawEntry> controllers = rawEntries.stream()
                    .filter(entry -> entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK))
                    .toList();

            for (RawEntry controller : controllers) {
                for (BlockRotation rotation : ROTATIONS) {
                    ShipBlueprint candidate = build(id, rotation, rawEntries, controller.pos());
                    Match current = scoreAllBlocks(world, controllerPos, candidate);
                    if (isBetter(current, best)) best = current;
                }
            }
        }

        if (best == null || best.matches() == 0) {
            Pirates.LOGGER.warn("No usable Eureka repair blueprint match at {}; loaded {}/{} templates",
                    controllerPos, loadedCandidates, EUREKA_BLUEPRINT_IDS.size());
            return Optional.empty();
        }

        double similarity = best.compared() == 0 ? 0.0 : best.matches() * 100.0 / best.compared();
        Pirates.LOGGER.info("Selected highest-similarity Eureka blueprint {} rotation={} similarity={}% ({}/{}) at {}",
                best.blueprint().id(), best.blueprint().rotation(), String.format(java.util.Locale.ROOT, "%.2f", similarity),
                best.matches(), best.compared(), controllerPos);
        return Optional.of(best.blueprint());
    }

    private static boolean isBetter(Match candidate, Match best) {
        if (candidate.compared() == 0) return false;
        if (best == null || best.compared() == 0) return true;
        long candidateRatio = (long) candidate.matches() * best.compared();
        long bestRatio = (long) best.matches() * candidate.compared();
        if (candidateRatio != bestRatio) return candidateRatio > bestRatio;
        return candidate.matches() > best.matches();
    }
    private static Optional<ShipBlueprint> buildFromTemplate(ServerWorld world, Identifier id,
                                                              StructureTemplate template, BlockRotation rotation,
                                                              BlockPos preferredController) {
        List<RawEntry> raw = decode(world, template);
        BlockPos controller = preferredController;
        if (controller == null) {
            controller = raw.stream()
                    .filter(entry -> entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK))
                    .map(RawEntry::pos)
                    .findFirst()
                    .orElse(null);
        }
        return controller == null ? Optional.empty() : Optional.of(build(id, rotation, raw, controller));
    }

    private static ShipBlueprint build(Identifier id, BlockRotation rotation,
                                       List<RawEntry> rawEntries, BlockPos rawController) {
        BlockPos transformedController = transform(rawController, rotation);
        Map<Long, Entry> entries = new HashMap<>();
        for (RawEntry raw : rawEntries) {
            BlockPos relative = transform(raw.pos(), rotation).subtract(transformedController);
            BlockState transformedState = raw.state().rotate(rotation);
            entries.put(relative.asLong(), new Entry(relative, transformedState, raw.hasBlockEntity()));
        }
        return new ShipBlueprint(id, rotation, entries);
    }

    private static BlockPos transform(BlockPos pos, BlockRotation rotation) {
        return StructureTemplate.transformAround(pos, BlockMirror.NONE, rotation, BlockPos.ORIGIN);
    }

    private static Match scoreAllBlocks(ServerWorld world, BlockPos controllerPos, ShipBlueprint blueprint) {
        int compared = 0;
        int matches = 0;
        for (Entry entry : blueprint.entries.values()) {
            if (entry.state().isAir() || entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK)) continue;
            BlockPos target = controllerPos.add(entry.relativePos());
            if (!world.isChunkLoaded(target)) continue;
            BlockState current = world.getBlockState(target);
            compared++;
            if (current.getBlock() == entry.state().getBlock()) matches++;
        }
        return new Match(blueprint, matches, compared);
    }

    private static Identifier id(String shipName) {
        return new Identifier("pirates_eureka", "ship/" + shipName);
    }
    private static List<RawEntry> decode(ServerWorld world, StructureTemplate template) {
        NbtCompound root = template.writeNbt(new NbtCompound());
        NbtList palette;
        if (root.contains("palette", NbtElement.LIST_TYPE)) {
            palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        } else {
            NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);
            palette = palettes.isEmpty() ? new NbtList() : (NbtList) palettes.get(0);
        }

        List<BlockState> states = new ArrayList<>(palette.size());
        var blockLookup = world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK);
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtHelper.toBlockState(blockLookup, palette.getCompound(i)));
        }

        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        List<RawEntry> result = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound block = blocks.getCompound(i);
            int stateIndex = block.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.size()) continue;
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            if (pos.size() != 3) continue;
            result.add(new RawEntry(
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    states.get(stateIndex),
                    block.contains("nbt", NbtElement.COMPOUND_TYPE)
            ));
        }
        return result;
    }

    public record Entry(BlockPos relativePos, BlockState state, boolean hasBlockEntity) {}
    private record RawEntry(BlockPos pos, BlockState state, boolean hasBlockEntity) {}
    private record Match(ShipBlueprint blueprint, int matches, int compared) {}
}