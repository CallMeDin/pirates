package ace.actually.pirates.combat;

import ace.actually.pirates.Pirates;
import ace.actually.pirates.blocks.CannonPrimingBlock;
import ace.actually.pirates.repair.PiratesShipBlueprintState;
import ace.actually.pirates.repair.ShipBlueprint;
import ace.actually.pirates.util.SailsCompat;
import ace.actually.pirates.util.ShipBlockIterator;
import net.minecraft.block.BlockState;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative, deliberately small age-of-sail combat controller. */
public final class ShipCombatController {
    private static final Map<Key, State> STATES = new HashMap<>();

    private ShipCombatController() {}

    public static void tick(ServerWorld world, LoadedServerShip ship, SeatedControllingPlayer controls,
                            Direction shipForward, boolean sailsControl, BlockPos helmPos) {
        if (controls == null || shipForward.getAxis().isVertical()) return;
        Key key = new Key(world.getRegistryKey().getValue().toString(), ship.getId());
        State state = STATES.computeIfAbsent(key, ignored -> new State());
        long time = world.getTime();
        if (state.liveShip != ship) state.reset(ship, time);

        if (time >= state.nextTargetUpdate) {
            state.nextTargetUpdate = time + Pirates.navalCombatTargetUpdateTicks;
            refreshCannons(world, ship, state, shipForward);
            captureOriginalBroadsides(world, ship, state, shipForward);
            state.completedBroadsideScans++;
            Ship retained = findById(world, state.targetShipId);
            if (!isValidTarget(world, ship, retained)) {
                retained = closestHostile(world, ship);
                state.targetShipId = retained == null ? -1L : retained.getId();
                state.chosenSide = null;
            }
            int portCannons = state.operational.getOrDefault(Side.LEFT, 0);
            int starboardCannons = state.operational.getOrDefault(Side.RIGHT, 0);
            boolean portBelowHalf = belowHalf(portCannons,
                    state.originalBroadside.getOrDefault(Side.LEFT, 0));
            boolean starboardBelowHalf = belowHalf(starboardCannons,
                    state.originalBroadside.getOrDefault(Side.RIGHT, 0));
            if (retained != null && state.mode != Mode.FLEEING
                    && time >= state.fleeCheckAfterTick
                    && state.completedBroadsideScans >= 3) {
                if (portBelowHalf && starboardBelowHalf) {
                    beginFleeing(ship, retained, state);
                } else if (state.chosenSide == Side.LEFT && portBelowHalf) {
                    state.chosenSide = Side.RIGHT;
                    state.mode = Mode.ALIGN_BROADSIDE;
                } else if (state.chosenSide == Side.RIGHT && starboardBelowHalf) {
                    state.chosenSide = Side.LEFT;
                    state.mode = Mode.ALIGN_BROADSIDE;
                }
            }
        }

        if (time < state.nextSteeringUpdate) return;
        state.nextSteeringUpdate = time + (sailsControl ? 1 : Pirates.navalCombatSteeringUpdateTicks);

        if (state.mode == Mode.FLEEING) {
            steer(world, helmPos, sailsControl, ship, controls, state,
                    shipForward, state.fleeX, state.fleeZ, 1.0f);
            return;
        }

        Ship target = findById(world, state.targetShipId);
        Vector3dc own = ship.getTransform().getPositionInWorld();
        if (!isValidTarget(world, ship, target)) {
            state.targetShipId = -1L;
            state.chosenSide = null;
            patrol(world, helmPos, sailsControl, ship, controls, state, own, shipForward);
            return;
        }


        var ownBounds = ship.getWorldAABB();
        var enemyBounds = target.getWorldAABB();
        double chaseX = (enemyBounds.minX() + enemyBounds.maxX()) * 0.5
                - (ownBounds.minX() + ownBounds.maxX()) * 0.5;
        double chaseZ = (enemyBounds.minZ() + enemyBounds.maxZ()) * 0.5
                - (ownBounds.minZ() + ownBounds.maxZ()) * 0.5;

        int totalCannons = state.operational.values().stream().mapToInt(Integer::intValue).sum();
        if (totalCannons == 0) {
            beginFleeing(ship, target, state);
            steer(world, helmPos, sailsControl, ship, controls, state,
                    shipForward, state.fleeX, state.fleeZ, 1.0f);
            return;
        }

        double enterRange = Pirates.cannonCanFireRange;
        double leaveRange = enterRange + Pirates.navalCombatRangeHysteresis;
        boolean engaged = state.mode == Mode.ALIGN_BROADSIDE || state.mode == Mode.HOLD_BROADSIDE;
        BroadsideSolution solution = state.chosenSide == null ? null
                : broadsideSolution(world, ship, target, state, state.chosenSide);
        Vector3d targetFromBroadside = solution == null ? null : solution.targetDirection();
        double firingDistanceSquared = targetFromBroadside == null
                ? chaseX * chaseX + chaseZ * chaseZ
                : targetFromBroadside.x * targetFromBroadside.x
                        + targetFromBroadside.z * targetFromBroadside.z;
        double allowedRange = engaged ? leaveRange : enterRange;

        if (firingDistanceSquared > allowedRange * allowedRange) {
            state.mode = Mode.CHASE;
            state.chosenSide = null;
            steer(world, helmPos, sailsControl, ship, controls, state,
                    shipForward, chaseX, chaseZ, 1.0f);
            return;
        }

        if (state.chosenSide == null || state.operational.getOrDefault(state.chosenSide, 0) == 0) {
            state.chosenSide = chooseSide(state.operational);
            state.mode = Mode.ALIGN_BROADSIDE;
            state.lastRudder = 0.0f;
            solution = broadsideSolution(world, ship, target, state, state.chosenSide);
            targetFromBroadside = solution == null ? null : solution.targetDirection();
        }
        if (targetFromBroadside == null || Math.hypot(targetFromBroadside.x, targetFromBroadside.z) < 0.001) {
            stopShip(world, helmPos, sailsControl, ship, controls);
            return;
        }

        double error = directionError(solution.aimDirection(), targetFromBroadside);
        double absoluteError = Math.abs(Math.toDegrees(error));

        if (absoluteError <= Pirates.navalCombatBroadsideToleranceDegrees) {
            state.mode = Mode.HOLD_BROADSIDE;
            stopShip(world, helmPos, sailsControl, ship, controls);
        } else {
            state.mode = Mode.ALIGN_BROADSIDE;
            // Rotate in place; do not add forward motion while obtaining the firing solution.
            turnByError(world, helmPos, sailsControl, ship, controls, state, error, 0.38f);
        }
    }

    private static void refreshCannons(ServerWorld world, LoadedServerShip ship, State state,
                                       Direction forward) {
        if (state.cannons.isEmpty()) {
            ShipBlockIterator.forEachBlock(ship, pos -> {
                BlockState block = world.getBlockState(pos);
                if (block.isOf(Pirates.CANNON_PRIMING_BLOCK) && block.contains(Properties.FACING)) {
                    state.cannons.add(new Cannon(pos.toImmutable(),
                            classify(forward, block.get(Properties.FACING))));
                }
            });
        }

        EnumMap<Side, Integer> counts = new EnumMap<>(Side.class);
        for (Side side : Side.values()) counts.put(side, 0);
        state.cannons.removeIf(cannon -> !world.getBlockState(cannon.pos()).isOf(Pirates.CANNON_PRIMING_BLOCK));
        for (Cannon cannon : state.cannons) {
            BlockState block = world.getBlockState(cannon.pos());
            Direction facing = block.get(Properties.FACING);
            BlockPos dispenser = cannon.pos().offset(facing);
            if (!world.getBlockState(dispenser).isOf(Pirates.DISPENSER_CANNON_BLOCK)) continue;
            if (!block.get(CannonPrimingBlock.DISARMED)) {
                counts.merge(cannon.side(), 1, Integer::sum);
            }
        }
        state.operational = counts;
    }

    private static Side classify(Direction front, Direction cannon) {
        if (cannon == front) return Side.FRONT;
        if (cannon == front.getOpposite()) return Side.BACK;
        if (cannon == front.rotateYCounterclockwise()) return Side.LEFT;
        return Side.RIGHT;
    }

    private static void captureOriginalBroadsides(ServerWorld world, LoadedServerShip ship,
                                                  State state, Direction forward) {
        if (state.originalBroadsideCaptured) return;

        EnumMap<Side, Integer> original = new EnumMap<>(Side.class);
        for (Side side : Side.values()) original.put(side, 0);
        PiratesShipBlueprintState.BlueprintRecord saved =
                PiratesShipBlueprintState.get(world).get(world, ship.getId());
        if (saved != null) {
            ShipBlueprint.load(world, saved.blueprintId(), saved.rotation()).ifPresent(blueprint -> {
                for (ShipBlueprint.Entry entry : blueprint.entries()) {
                    BlockState block = entry.state();
                    if (!block.isOf(Pirates.CANNON_PRIMING_BLOCK) || !block.contains(Properties.FACING)) continue;
                    Direction facing = block.get(Properties.FACING);
                    ShipBlueprint.Entry dispenser = blueprint.entryAt(entry.relativePos().offset(facing));
                    if (dispenser != null && dispenser.state().isOf(Pirates.DISPENSER_CANNON_BLOCK)) {
                        original.merge(classify(forward, facing), 1, Integer::sum);
                    }
                }
            });
        }

        if (original.get(Side.LEFT) == 0 && original.get(Side.RIGHT) == 0) {
            original.put(Side.LEFT, state.operational.getOrDefault(Side.LEFT, 0));
            original.put(Side.RIGHT, state.operational.getOrDefault(Side.RIGHT, 0));
        }
        state.originalBroadside = original;
        state.originalBroadsideCaptured = true;
    }

    private static boolean belowHalf(int current, int original) {
        return original > 0 && current * 2 < original;
    }
    private static Side chooseSide(EnumMap<Side, Integer> counts) {
        int left = counts.getOrDefault(Side.LEFT, 0);
        int right = counts.getOrDefault(Side.RIGHT, 0);
        if (left > 0 || right > 0) return right > left ? Side.RIGHT : Side.LEFT;
        return counts.getOrDefault(Side.FRONT, 0) >= counts.getOrDefault(Side.BACK, 0)
                ? Side.FRONT : Side.BACK;
    }
    /**
     * Computes both the real firing direction and midpoint of the selected cannon
     * row in world space. This is the authoritative broadside alignment; no helm
     * facing or inferred 90-degree hull tangent is involved.
     */
    private static BroadsideSolution broadsideSolution(ServerWorld world, LoadedServerShip own,
                                                        Ship target, State state, Side side) {
        double x = 0.0, z = 0.0;
        Vector3d aim = new Vector3d();
        int count = 0;
        for (Cannon cannon : state.cannons) {
            if (cannon.side() != side) continue;
            BlockState block = world.getBlockState(cannon.pos());
            if (!block.isOf(Pirates.CANNON_PRIMING_BLOCK) || !block.contains(Properties.FACING)) continue;

            Vec3d cannonWorld = VSGameUtilsKt.toWorldCoordinates(world, Vec3d.ofCenter(cannon.pos()));
            x += cannonWorld.x;
            z += cannonWorld.z;

            Direction facing = block.get(Properties.FACING);
            Vec3d aheadWorld = VSGameUtilsKt.toWorldCoordinates(
                    world, Vec3d.ofCenter(cannon.pos().offset(facing, 16)));
            aim.add(aheadWorld.x - cannonWorld.x, 0, aheadWorld.z - cannonWorld.z);
            count++;
        }
        if (count == 0 || Math.hypot(aim.x, aim.z) < 0.001) return null;

        double centerX = x / count;
        double centerZ = z / count;
        var enemyBounds = target.getWorldAABB();
        double enemyX = (enemyBounds.minX() + enemyBounds.maxX()) * 0.5;
        double enemyZ = (enemyBounds.minZ() + enemyBounds.maxZ()) * 0.5;
        aim.y = 0.0;
        aim.normalize();
        return new BroadsideSolution(
                new Vector3d(enemyX - centerX, 0, enemyZ - centerZ), aim);
    }

    private static double directionError(Vector3dc current, Vector3dc desired) {
        double currentLength = Math.hypot(current.x(), current.z());
        double desiredLength = Math.hypot(desired.x(), desired.z());
        if (currentLength < 0.001 || desiredLength < 0.001) return 0.0;
        double currentX = current.x() / currentLength;
        double currentZ = current.z() / currentLength;
        double desiredX = desired.x() / desiredLength;
        double desiredZ = desired.z() / desiredLength;
        return Math.atan2(currentX * desiredZ - currentZ * desiredX,
                Math.max(-1.0, Math.min(1.0, currentX * desiredX + currentZ * desiredZ)));
    }

    private static void turnByError(ServerWorld world, BlockPos helmPos, boolean sailsControl,
                                    LoadedServerShip ship, SeatedControllingPlayer controls,
                                    State state, double error, float maxRudder) {
        double errorDegrees = Math.toDegrees(error);
        float rudder = (float) Math.max(-maxRudder, Math.min(maxRudder,
                -errorDegrees / 90.0 * maxRudder));
        state.lastRudder = rudder;
        applyControls(world, helmPos, sailsControl, ship, controls, 0.0f, rudder);
    }

    private static void stopShip(ServerWorld world, BlockPos helmPos, boolean sailsControl,
                                 LoadedServerShip ship, SeatedControllingPlayer controls) {
        applyControls(world, helmPos, sailsControl, ship, controls, 0.0f, 0.0f);
    }

    /** Returns signed angular error in radians. */
    private static double steer(ServerWorld world, BlockPos helmPos, boolean sailsControl,
                                LoadedServerShip ship, SeatedControllingPlayer controls,
                                State state, Direction localForward, double desiredX, double desiredZ, float forwardImpulse) {
        double length = Math.hypot(desiredX, desiredZ);
        if (length < 0.001) return 0.0;
        desiredX /= length;
        desiredZ /= length;

        Vector3d current = ship.getTransform().getShipToWorldRotation()
                .transform(new Vector3d(localForward.getOffsetX(), 0, localForward.getOffsetZ()));
        double currentLength = Math.hypot(current.x, current.z);
        if (currentLength < 0.001) return 0.0;
        double currentX = current.x / currentLength;
        double currentZ = current.z / currentLength;
        double cross = currentX * desiredZ - currentZ * desiredX;
        double dot = Math.max(-1.0, Math.min(1.0, currentX * desiredX + currentZ * desiredZ));
        double error = Math.atan2(cross, dot);
        double errorDegrees = Math.toDegrees(error);
        double absoluteError = Math.abs(errorDegrees);
        float rudder;
        if (absoluteError <= Pirates.navalCombatSteeringDeadzoneDegrees) {
            rudder = 0.0f;
            state.lastRudder = 0.0f;
        } else {
            double proportional = Math.min(1.0, absoluteError / 55.0);
            float magnitude = (float) Math.max(0.18,
                    proportional * Pirates.navalCombatMaxRudderImpulse);
            rudder = error > 0 ? -magnitude : magnitude;
            if (state.lastRudder != 0.0f
                    && Math.signum(rudder) != Math.signum(state.lastRudder)
                    && absoluteError < 25.0) {
                rudder = 0.0f;
            } else {
                state.lastRudder = rudder;
            }
        }
        applyControls(world, helmPos, sailsControl, ship, controls, forwardImpulse, rudder);
        return error;
    }

    private static void applyControls(ServerWorld world, BlockPos helmPos, boolean sailsControl,
                                      LoadedServerShip ship, SeatedControllingPlayer controls,
                                      float forwardImpulse, float rudder) {
        if (sailsControl) {
            SailsCompat.steerHelm(world, helmPos, ship, controls, rudder);
            return;
        }
        controls.setForwardImpulse(forwardImpulse);
        controls.setLeftImpulse(rudder);
        ship.setAttachment(SeatedControllingPlayer.class, controls);
    }
    private static void beginFleeing(LoadedServerShip ship, Ship enemy, State state) {
        var ownBounds = ship.getWorldAABB();
        var enemyBounds = enemy.getWorldAABB();
        double awayX = (ownBounds.minX() + ownBounds.maxX()) * 0.5
                - (enemyBounds.minX() + enemyBounds.maxX()) * 0.5;
        double awayZ = (ownBounds.minZ() + ownBounds.maxZ()) * 0.5
                - (enemyBounds.minZ() + enemyBounds.maxZ()) * 0.5;
        double length = Math.hypot(awayX, awayZ);
        if (length < 0.001) {
            double random = Math.random() * Math.PI * 2.0;
            awayX = Math.cos(random);
            awayZ = Math.sin(random);
        } else {
            awayX /= length;
            awayZ /= length;
        }

        // Randomize within +/-60 degrees of directly away, guaranteeing that
        // the selected course never points back into the enemy hemisphere.
        double offset = (Math.random() - 0.5) * Math.toRadians(120.0);
        double cosine = Math.cos(offset);
        double sine = Math.sin(offset);
        state.fleeX = awayX * cosine - awayZ * sine;
        state.fleeZ = awayX * sine + awayZ * cosine;
        state.mode = Mode.FLEEING;
        state.chosenSide = null;
        state.lastRudder = 0.0f;
    }
    private static void patrol(ServerWorld world, BlockPos helmPos, boolean sailsControl,
                               LoadedServerShip ship, SeatedControllingPlayer controls,
                               State state, Vector3dc own, Direction localForward) {
        double radius = Pirates.navalCombatPatrolRadius;
        if (state.patrolCenter == null || state.mode != Mode.PATROL) {
            Vector3d forward = ship.getTransform().getShipToWorldRotation().transform(
                    new Vector3d(localForward.getOffsetX(), 0.0, localForward.getOffsetZ()));
            double forwardLength = Math.hypot(forward.x, forward.z);
            if (forwardLength < 0.001) {
                forward.set(1.0, 0.0, 0.0);
            } else {
                forward.div(forwardLength);
            }

            state.patrolClockwise = Math.random() < 0.5;
            double direction = state.patrolClockwise ? 1.0 : -1.0;
            double radialX = -direction * forward.z;
            double radialZ = direction * forward.x;
            state.patrolCenter = new Vector3d(
                    own.x() - radialX * radius, own.y(), own.z() - radialZ * radius);
        }

        double radialX = own.x() - state.patrolCenter.x;
        double radialZ = own.z() - state.patrolCenter.z;
        double distance = Math.hypot(radialX, radialZ);
        if (distance < 0.001) {
            radialX = 1.0;
            radialZ = 0.0;
            distance = 1.0;
        }
        radialX /= distance;
        radialZ /= distance;

        double tangentX = state.patrolClockwise ? radialZ : -radialZ;
        double tangentZ = state.patrolClockwise ? -radialX : radialX;
        double radialCorrection = Math.max(-0.75, Math.min(0.75,
                (radius - distance) / radius * 1.5));
        double desiredX = tangentX + radialX * radialCorrection;
        double desiredZ = tangentZ + radialZ * radialCorrection;

        state.mode = Mode.PATROL;
        steer(world, helmPos, sailsControl, ship, controls, state,
                localForward, desiredX, desiredZ, 0.65f);
    }
    private static Ship closestHostile(ServerWorld world, LoadedServerShip own) {
        Ship best = null;
        double bestDistance = Double.MAX_VALUE;
        Vector3dc origin = own.getTransform().getPositionInWorld();
        for (Ship candidate : VSGameUtilsKt.getAllShips(world)) {
            if (!isValidTarget(world, own, candidate)) continue;
            double distance = origin.distanceSquared(candidate.getTransform().getPositionInWorld());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isValidTarget(ServerWorld world, Ship own, Ship candidate) {
        if (candidate == null || candidate.getId() == own.getId()) return false;
        if (own.getTransform().getPositionInWorld().distanceSquared(
                candidate.getTransform().getPositionInWorld()) >= Pirates.pursuitDistance) return false;
        Team ownTeam = world.getScoreboard().getPlayerTeam(own.getSlug());
        Team otherTeam = world.getScoreboard().getPlayerTeam(candidate.getSlug());
        return ownTeam == null || otherTeam == null || !ownTeam.isEqual(otherTeam);
    }

    private static Ship findById(ServerWorld world, long id) {
        if (id < 0) return null;
        for (Ship ship : VSGameUtilsKt.getAllShips(world)) if (ship.getId() == id) return ship;
        return null;
    }

    private enum Mode { PATROL, CHASE, ALIGN_BROADSIDE, HOLD_BROADSIDE, FLEEING }
    private enum Side { FRONT, LEFT, RIGHT, BACK }
    private record Cannon(BlockPos pos, Side side) {}
    private record BroadsideSolution(Vector3d targetDirection, Vector3d aimDirection) {}
    private record Key(String dimension, long shipId) {}
    private static final class State {
        LoadedServerShip liveShip;

        void reset(LoadedServerShip ship, long worldTime) {
            liveShip = ship;
            targetShipId = -1L;
            nextTargetUpdate = 0L;
            nextSteeringUpdate = 0L;
            fleeCheckAfterTick = worldTime + 100L;
            completedBroadsideScans = 0;
            mode = Mode.PATROL;
            chosenSide = null;
            patrolCenter = null;
            patrolClockwise = false;
            cannons.clear();
            operational = new EnumMap<>(Side.class);
            originalBroadside = new EnumMap<>(Side.class);
            originalBroadsideCaptured = false;
            lastRudder = 0.0f;
            fleeX = 0.0;
            fleeZ = 0.0;
        }
        long targetShipId = -1L;
        long nextTargetUpdate;
        long nextSteeringUpdate;
        long fleeCheckAfterTick;
        int completedBroadsideScans;
        Mode mode = Mode.PATROL;
        Side chosenSide;
        Vector3d patrolCenter;
        boolean patrolClockwise;
        final List<Cannon> cannons = new ArrayList<>();
        EnumMap<Side, Integer> operational = new EnumMap<>(Side.class);
        EnumMap<Side, Integer> originalBroadside = new EnumMap<>(Side.class);
        boolean originalBroadsideCaptured;
        float lastRudder;
        double fleeX;
        double fleeZ;
    }
}