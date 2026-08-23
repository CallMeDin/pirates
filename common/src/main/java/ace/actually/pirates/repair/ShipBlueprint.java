package ace.actually.pirates.repair;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.util.ShipBlockIterator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.valkyrienskies.core.api.ships.Ship;

/** Immutable, controller-relative view of one bundled ship structure template. */
public final class ShipBlueprint {
    private static final BlockRotation[] ROTATIONS = BlockRotation.values();
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

    public static List<Identifier> eurekaBlueprintIds(ServerWorld world) {
        return blueprintIds(world, "pirates_eureka");
    }

    public static List<Identifier> sailsBlueprintIds(ServerWorld world) {
        return blueprintIds(world, "pirates_sails");
    }

    public static List<Identifier> skyBlueprintIds(ServerWorld world) {
        return blueprintIds(world, "pirates_sky");
    }

    public static List<Identifier> allBlueprintIds(ServerWorld world) {
        List<Identifier> ids = new ArrayList<>(eurekaBlueprintIds(world));
        ids.addAll(sailsBlueprintIds(world));
        ids.addAll(skyBlueprintIds(world));
        return List.copyOf(ids);
    }

    private static List<Identifier> blueprintIds(ServerWorld world, String namespace) {
        return world.getServer().getResourceManager()
                .findResources("structures/ship", resourceId ->
                        resourceId.getNamespace().equals(namespace)
                                && resourceId.getPath().endsWith(".nbt"))
                .keySet().stream()
                .map(resourceId -> new Identifier(resourceId.getNamespace(),
                        resourceId.getPath().substring("structures/".length(),
                                resourceId.getPath().length() - ".nbt".length())))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    public static boolean isEurekaBlueprint(Identifier id) {
        return id != null && id.getNamespace().equals("pirates_eureka")
                && id.getPath().startsWith("ship/");
    }

    public static boolean isSailsBlueprint(Identifier id) {
        return id != null && id.getNamespace().equals("pirates_sails")
                && id.getPath().startsWith("ship/");
    }

    public static boolean isRepairBlueprint(Identifier id) {
        return isEurekaBlueprint(id) || isSailsBlueprint(id)
                || (id != null && id.getNamespace().equals("pirates_sky")
                && id.getPath().startsWith("ship/"));
    }
    public int size() {
        return entries.size();
    }

    public static Optional<ShipBlueprint> load(ServerWorld world, Identifier id, BlockRotation rotation) {
        return world.getStructureTemplateManager().getTemplate(id)
                .flatMap(template -> buildFromTemplate(world, id, template, rotation, null));
    }

    /** Returns only non-air template positions that were actually placed in the world. */
    public static Set<BlockPos> placedNonAirPositions(ServerWorld world, StructureTemplate template,
                                                       BlockPos origin, StructurePlacementData settings) {
        Set<BlockPos> positions = new HashSet<>();
        BlockMirror mirror = settings.getMirror() == null ? BlockMirror.NONE : settings.getMirror();
        BlockRotation rotation = settings.getRotation() == null ? BlockRotation.NONE : settings.getRotation();
        BlockPos pivot = settings.getPosition() == null ? BlockPos.ORIGIN : settings.getPosition();
        for (RawEntry entry : decode(world, template)) {
            if (entry.state().isAir()) continue;
            BlockPos transformed = StructureTemplate.transformAround(entry.pos(), mirror, rotation, pivot);
            BlockPos placedPos = origin.add(transformed);
            if (!world.getBlockState(placedPos).isAir()) positions.add(placedPos);
        }
        return positions;
    }

    /**
     * Matches a template to an already assembled controller. Assembly places the raw
     * structure as a ship, so controller-relative offsets survive relocation into shipyard space.
     */
    public static Optional<ShipBlueprint> match(ServerWorld world, BlockPos controllerPos) {
        return matchAt(world, controllerPos, eurekaBlueprintIds(world), "Eureka", false);
    }

    /** Strict match used before assembling a raw, naturally generated Sails structure. */
    public static Optional<ShipBlueprint> matchSails(ServerWorld world, BlockPos controllerPos) {
        return matchAt(world, controllerPos, sailsBlueprintIds(world), "Sails", true);
    }

    /** Strict match used before assembling any raw, naturally generated Pirates ship. */
    public static Optional<ShipBlueprint> matchGenerated(ServerWorld world, BlockPos controllerPos) {
        return matchAt(world, controllerPos, allBlueprintIds(world), "generated", true);
    }

    private static Optional<ShipBlueprint> matchAt(ServerWorld world, BlockPos controllerPos,
                                                    List<Identifier> blueprintIds, String kind,
                                                    boolean requireAssemblyCoverage) {
        Match best = null;
        int loadedCandidates = 0;

        for (Identifier id : blueprintIds) {
            Optional<StructureTemplate> optional = world.getStructureTemplateManager().getTemplate(id);
            if (optional.isEmpty()) {
                Pirates.LOGGER.warn("{} ship blueprint {} is not loaded", kind, id);
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

        int expected = best == null ? 0 : comparableBlocks(best.blueprint());
        if (best == null || best.matches() == 0
                || (requireAssemblyCoverage && (expected == 0
                || (long) best.matches() * 100L < (long) expected * 90L))) {
            Pirates.LOGGER.warn("No safe {} ship blueprint match at {}; loaded {}/{} templates",
                    kind, controllerPos, loadedCandidates, blueprintIds.size());
            return Optional.empty();
        }

        double similarity = best.compared() == 0 ? 0.0 : best.matches() * 100.0 / best.compared();
        Pirates.LOGGER.info("Selected highest-similarity {} blueprint {} rotation={} similarity={}% ({}/{}, coverage {}/{}) at {}",
                kind, best.blueprint().id(), best.blueprint().rotation(),
                String.format(java.util.Locale.ROOT, "%.2f", similarity),
                best.matches(), best.compared(), best.matches(), expected, controllerPos);
        return Optional.of(best.blueprint());
    }

    private static int comparableBlocks(ShipBlueprint blueprint) {
        int count = 0;
        for (Entry entry : blueprint.entries.values()) {
            if (!entry.state().isAir() && !entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK)) count++;
        }
        return count;
    }
    /**
     * Finds both blueprint and controller-relative anchor from the ship's remaining
     * blocks. The controller itself is only a coordinate convention in the NBT and
     * does not need to exist on the assembled ship.
     */
    public static Optional<ShipMatch> match(ServerWorld world, Ship ship) {
        return matchShip(world, ship, eurekaBlueprintIds(world), "Eureka");
    }

    public static Optional<ShipMatch> matchSails(ServerWorld world, Ship ship) {
        return matchShip(world, ship, sailsBlueprintIds(world), "Sails");
    }

    public static Optional<ShipMatch> matchAny(ServerWorld world, Ship ship) {
        return matchShip(world, ship, allBlueprintIds(world), "Pirates");
    }

    private static Optional<ShipMatch> matchShip(ServerWorld world, Ship ship,
                                                  List<Identifier> blueprintIds, String kind) {
        Map<Block, List<BlockPos>> actualByBlock = new HashMap<>();
        ShipBlockIterator.forEachBlock(ship, pos -> {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                actualByBlock.computeIfAbsent(state.getBlock(), ignored -> new ArrayList<>())
                        .add(pos.toImmutable());
            }
        });
        if (actualByBlock.isEmpty()) return Optional.empty();

        AnchoredMatch best = null;
        for (Identifier id : blueprintIds) {
            Optional<StructureTemplate> optional = world.getStructureTemplateManager().getTemplate(id);
            if (optional.isEmpty()) continue;
            List<RawEntry> raw = decode(world, optional.get());
            List<RawEntry> controllers = raw.stream()
                    .filter(entry -> entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK)).toList();
            for (RawEntry controller : controllers) {
                for (BlockRotation rotation : ROTATIONS) {
                    ShipBlueprint candidate = build(id, rotation, raw, controller.pos());
                    Map<Block, List<Entry>> blueprintByBlock = new HashMap<>();
                    for (Entry entry : candidate.entries()) {
                        if (entry.state().isAir() || entry.state().isOf(Pirates.MOTION_INVOKING_BLOCK)) continue;
                        if (actualByBlock.containsKey(entry.state().getBlock())) {
                            blueprintByBlock.computeIfAbsent(entry.state().getBlock(), ignored -> new ArrayList<>())
                                    .add(entry);
                        }
                    }

                    List<Block> pivots = blueprintByBlock.keySet().stream()
                            .sorted(Comparator.comparingLong(block ->
                                    (long) blueprintByBlock.get(block).size() * actualByBlock.get(block).size()))
                            .limit(8).toList();
                    Map<Long, Integer> anchorVotes = new HashMap<>();
                    for (Block block : pivots) {
                        for (Entry expected : blueprintByBlock.get(block)) {
                            for (BlockPos actual : actualByBlock.get(block)) {
                                long anchor = actual.subtract(expected.relativePos()).asLong();
                                anchorVotes.merge(anchor, 1, Integer::sum);
                            }
                        }
                    }

                    for (Map.Entry<Long, Integer> vote : anchorVotes.entrySet().stream()
                            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                            .limit(16).toList()) {
                        BlockPos anchor = BlockPos.fromLong(vote.getKey());
                        Match score = scoreAllBlocks(world, anchor, candidate);
                        AnchoredMatch current = new AnchoredMatch(score, anchor);
                        if (best == null || isBetter(current.match(), best.match())) best = current;
                    }
                }
            }
        }

        if (best == null || best.match().matches() == 0) {
            Pirates.LOGGER.warn("No usable {} blueprint could be aligned to VS ship {}", kind, ship.getId());
            return Optional.empty();
        }
        Match score = best.match();
        double similarity = score.compared() == 0 ? 0.0 : score.matches() * 100.0 / score.compared();
        Pirates.LOGGER.info("Aligned {} blueprint {} rotation={} to VS ship {} at {} similarity={}% ({}/{})",
                kind, score.blueprint().id(), score.blueprint().rotation(), ship.getId(), best.anchor(),
                String.format(java.util.Locale.ROOT, "%.2f", similarity), score.matches(), score.compared());
        return Optional.of(new ShipMatch(score.blueprint(), best.anchor()));
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
    public record ShipMatch(ShipBlueprint blueprint, BlockPos anchor) {}
    private record RawEntry(BlockPos pos, BlockState state, boolean hasBlockEntity) {}
    private record Match(ShipBlueprint blueprint, int matches, int compared) {}
    private record AnchoredMatch(Match match, BlockPos anchor) {}
}
