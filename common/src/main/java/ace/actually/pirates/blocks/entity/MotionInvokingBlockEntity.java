package ace.actually.pirates.blocks.entity;

import ace.actually.pirates.blocks.MotionInvokingBlock;
import ace.actually.pirates.combat.ShipCombatController;
import ace.actually.pirates.util.ConfigUtils;
import ace.actually.pirates.util.EurekaCompat;
import ace.actually.pirates.Pirates;
import ace.actually.pirates.util.SailsCompat;
import ace.actually.pirates.repair.RepairExclusions;
import ace.actually.pirates.repair.ShipBlueprint;
import ace.actually.pirates.repair.ShipRepairManager;
import ace.actually.pirates.repair.PiratesShipBlueprintState;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;

import java.util.List;

import static ace.actually.pirates.blocks.MotionInvokingBlock.COMPAT;
import static net.minecraft.state.property.Properties.HORIZONTAL_FACING;

@SuppressWarnings("UnstableApiUsage")
public class MotionInvokingBlockEntity extends BlockEntity {
    NbtList path = new NbtList();
    long nextInstruction = 0;
    // Read-only legacy fields used once to migrate existing worlds into PiratesShipBlueprintState.
    private String legacyRepairBlueprintId = "";
    private BlockRotation legacyRepairBlueprintRotation = BlockRotation.NONE;
    private transient ShipBlueprint repairBlueprint;
    private transient long nextBlueprintMatchAttempt = 0L;
    //boolean isChecked = false;

    //variables below this line aren't serialised because they don't need to be.
    int[] target = new int[3]; //x,y,z of a point in space that the ship is "trying" to get to.
    double ldx = -1; //last distance tracked along the x-axis, from the target
    double ldz = -1; //last distance tracked along the z-axis, from the target


    private static int updateTicks = -1;

    public MotionInvokingBlockEntity(BlockPos pos, BlockState state) {
        super(Pirates.MOTION_INVOKING_BLOCK_ENTITY, pos, state);
    }

//    public void setCompat(String compat) {
//        this.compat = compat;
//        markDirty();
//    }

    public static void tick(World world, BlockPos pos, BlockState state, MotionInvokingBlockEntity be) {
        if (world instanceof ServerWorld serverWorld) {
            be.tickRepairController(serverWorld);
        }

//        if (!be.isChecked) {
//            state.with(COMPAT, 0);
//            if (Pirates.loadedCompats.sails && SailsCompat.checkHelm(world, pos)) {
//                state.with(COMPAT, 1);
//            }
//            if (Pirates.loadedCompats.eureka && EurekaCompat.checkHelm(world, pos)) {
//                state.with(COMPAT, 2);
//            }
//            world.setBlockState(pos, state, 10);
//            be.isChecked = true;
//        }

        if(!state.get(MotionInvokingBlock.ARMED)) return;

        //ensure compat value matches loaded dependencies and a helm is present
        if (state.get(COMPAT).equals(1)) {
            if (!Pirates.loadedCompats.sails) {
                state = state.with(COMPAT, 0);
                world.setBlockState(pos, state, 10);
                return;
            } else if (!SailsCompat.checkHelm(world, pos)) {
                MotionInvokingBlock.disarm(world, pos);
                return;
            }
        } else if (state.get(COMPAT).equals(2)) {
            if (!Pirates.loadedCompats.eureka) {
                state = state.with(COMPAT, 0);
                world.setBlockState(pos, state, 10);
                return;
            } else if (!EurekaCompat.checkHelm(world, pos)) {
                MotionInvokingBlock.disarm(world, pos);
                return;
            }
        }

        if(updateTicks==-1) {
            updateTicks = Integer.parseInt(ConfigUtils.config.getOrDefault("controlled-ship-updates","100"));

        }
        if (!world.isClient && world.getGameRules().getBoolean(Pirates.PIRATES_IS_LIVE_WORLD) && world.getTime() >= be.nextInstruction) {

            if (VSGameUtilsKt.isBlockInShipyard(world, pos)) {
                ChunkPos chunkPos = world.getChunk(pos).getPos();
                LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos((ServerWorld) world, chunkPos);

                if (ship != null) {
                    ship.setStatic(false);
                    SeatedControllingPlayer seatedControllingPlayer = ship.getAttachment(SeatedControllingPlayer.class);
                    if (seatedControllingPlayer == null && (world.getBlockState(pos.up()).contains(HORIZONTAL_FACING))) {
                        if (state.get(COMPAT).equals(1)) {
                            seatedControllingPlayer = new SeatedControllingPlayer(world.getBlockState(pos.up()).get(HORIZONTAL_FACING).getOpposite()); //not sure this is necessary
                        } else if (state.get(COMPAT).equals(2)) {
                            seatedControllingPlayer = new SeatedControllingPlayer(world.getBlockState(pos.up()).get(HORIZONTAL_FACING).getOpposite());
                        }
                        ship.setAttachment(SeatedControllingPlayer.class, seatedControllingPlayer);
                    }

                    Direction combatForward = world.getBlockState(pos.up()).get(HORIZONTAL_FACING).getOpposite();
                    boolean sailsControl = state.get(COMPAT).equals(1);
                    ShipCombatController.tick((ServerWorld) world, ship, seatedControllingPlayer,
                            combatForward, sailsControl, pos.up());
                }
            }
        }
    }

    private void tickRepairController(ServerWorld world) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(world, pos);
        if (ship == null) return;
        ShipRepairManager.register(world, ship.getId(), this);
        migrateLegacyBlueprint(world, ship.getId());
        loadSavedRepairBlueprint(world, ship.getId());
    }

    private void migrateLegacyBlueprint(ServerWorld world, long shipId) {
        if (legacyRepairBlueprintId.isEmpty()) return;
        Identifier id = Identifier.tryParse(legacyRepairBlueprintId);
        PiratesShipBlueprintState state = PiratesShipBlueprintState.get(world);
        if (state.get(world, shipId) == null && id != null && ShipBlueprint.isEurekaBlueprint(id)) {
            state.put(world, shipId, id, legacyRepairBlueprintRotation, pos);
            Pirates.LOGGER.info("Migrated legacy repair blueprint {} for VS ship {} into Pirates world data", id, shipId);
        }
        legacyRepairBlueprintId = "";
        legacyRepairBlueprintRotation = BlockRotation.NONE;
    }

    private void loadSavedRepairBlueprint(ServerWorld world, long shipId) {
        if (repairBlueprint != null) return;
        var record = PiratesShipBlueprintState.get(world).get(world, shipId);
        if (record != null && ShipBlueprint.isRepairBlueprint(record.blueprintId())) {
            repairBlueprint = ShipBlueprint.load(world, record.blueprintId(), record.rotation()).orElse(null);
        }
    }
    public RepairQuote createRepairQuote() {
        if (!(world instanceof ServerWorld serverWorld)) return null;

        Ship ship = VSGameUtilsKt.getShipManagingPos(serverWorld, pos);
        if (ship == null) return null;
        long shipId = ship.getId();
        loadSavedRepairBlueprint(serverWorld, shipId);
        boolean existingBlueprint = repairBlueprint != null;
        if (!existingBlueprint) {
            repairBlueprint = ShipBlueprint.matchGenerated(serverWorld, pos).orElse(null);
            if (repairBlueprint == null) return null;
            PiratesShipBlueprintState.get(serverWorld).put(serverWorld, shipId,
                    repairBlueprint.id(), repairBlueprint.rotation(), pos);
        }

        int repairable = countRepairable(serverWorld, repairBlueprint);
        int eligible = (int) repairBlueprint.entries().stream()
                .filter(entry -> !RepairExclusions.isExcluded(entry.state(), entry.hasBlockEntity())).count();
        boolean beyondSaving = eligible > 0 && (long) repairable * 100L >= (long) eligible * 80L;
        int goldCost = repairable == 0 ? 0
                : (int) Math.max(1L, ((long) repairable + Pirates.shipRepairBlocksPerGold - 1L)
                        / Pirates.shipRepairBlocksPerGold);
        return new RepairQuote(repairBlueprint.id(), repairBlueprint.rotation(), repairable,
                eligible, goldCost, beyondSaving, existingBlueprint);
    }

    private int countRepairable(ServerWorld world, ShipBlueprint blueprint) {
        int repairable = 0;
        for (ShipBlueprint.Entry entry : blueprint.entries()) {
            if (RepairExclusions.isExcluded(entry.state(), entry.hasBlockEntity())) continue;
            BlockPos target = pos.add(entry.relativePos());
            if (!world.isChunkLoaded(target)) continue;
            if (RepairExclusions.needsRepair(world.getBlockState(target), entry.state())) repairable++;
        }
        return repairable;
    }

    /** Returns -1 when unavailable, otherwise the number of replaced blueprint blocks. */
    public int repairImmediately(RepairQuote quote) {
        if (!(world instanceof ServerWorld serverWorld) || quote == null
                || !ShipBlueprint.isRepairBlueprint(quote.blueprintId())) return -1;
        repairBlueprint = ShipBlueprint.load(serverWorld, quote.blueprintId(), quote.rotation()).orElse(null);
        if (repairBlueprint == null) return -1;
        Ship ship = VSGameUtilsKt.getShipManagingPos(serverWorld, pos);
        if (ship == null) return -1;
        PiratesShipBlueprintState.get(serverWorld).put(serverWorld, ship.getId(),
                quote.blueprintId(), quote.rotation(), pos);
        int repaired = 0;
        for (ShipBlueprint.Entry entry : repairBlueprint.entries()) {
            if (RepairExclusions.isExcluded(entry.state(), entry.hasBlockEntity())) continue;
            BlockPos target = pos.add(entry.relativePos());
            if (!serverWorld.isChunkLoaded(target)) continue;
            BlockState current = serverWorld.getBlockState(target);
            if (RepairExclusions.needsRepair(current, entry.state())) {
                serverWorld.setBlockState(target, RepairExclusions.repairState(entry.state()), net.minecraft.block.Block.NOTIFY_ALL);
                repaired++;
            }
        }
        return repaired;
    }

    public record RepairQuote(Identifier blueprintId, BlockRotation rotation, int repairableBlocks,
                              int eligibleBlocks, int goldCost, boolean beyondSaving,
                              boolean existingBlueprint) {}
    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.put("path",path);
        nbt.putLong("nextInstruction", nextInstruction);
        nbt.putIntArray("target",target);
        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        nextInstruction = nbt.getLong("nextInstruction");
        if(nbt.contains("path")) {
            path = (NbtList) nbt.get("path");
        }

        if(nbt.contains("target")) {
            target = nbt.getIntArray("target");
        }
        legacyRepairBlueprintId = nbt.getString("repairBlueprintId");
        try {
            legacyRepairBlueprintRotation = BlockRotation.valueOf(nbt.getString("repairBlueprintRotation"));
        } catch (IllegalArgumentException ignored) {
            legacyRepairBlueprintRotation = BlockRotation.NONE;
        }
        repairBlueprint = null;
    }

    public void setTarget(int[] target) {
        this.target = target;
        markDirty();
    }

    public int[] getTarget() {
        return target;
    }

    public double getLdx() {
        return ldx;
    }

    public double getLdz() {
        return ldz;
    }

    public void setLdx(double ldx) {
        this.ldx = ldx;
    }

    public void setLdz(double ldz) {
        this.ldz = ldz;
    }

    public NbtList getPath() {
        return path;
    }

    public void setPath(NbtList path) {
        this.path = path;
        markDirty();
    }
    public void addPathNode(BlockPos pos) {
        this.path.add(new NbtIntArray(new int[]{pos.getX(),pos.getY(),pos.getZ()}));
        markDirty();
    }

    /**
     * This method uses bases VS things to effectively create circles.
     * the circles arent very good. TODO: Make the circles good
     * @param ship
     */
    private void moveShipForward(LoadedServerShip ship) {
        double mass = ship.getInertiaData().getMass();
        Vector3d qdc = ship.getTransform().getShipToWorldRotation().getEulerAnglesZXY(new Vector3d()).normalize().mul(mass*10);
        qdc = new Vector3d(-qdc.x,0,-qdc.z);
        GameToPhysicsAdapter gtpa = ValkyrienSkiesMod.getOrCreateGTPA(getWorld().getRegistryKey().getValue().toString());

        if(gtpa!=null) {
            Vector3dc v3dc = ship.getInertiaData().getCenterOfMassInShip();
            Vector3d loc = new Vector3d(v3dc.x()+1,v3dc.y(),v3dc.z()+1);
            //if(world instanceof ServerWorld serverWorld)
            //{
            //    serverWorld.spawnParticles(ParticleTypes.BUBBLE,loc.x,loc.y,loc.z,1,0,0,0,0);
            //}
            gtpa.applyInvariantForceToPos(ship.getId(), qdc, loc.sub(ship.getTransform().getPositionInShip()));
        }
    }
}
