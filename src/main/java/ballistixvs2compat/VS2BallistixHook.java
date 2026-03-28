package ballistixvs2compat;

import ballistix.api.compat.BallistixCompatHooks;
import ballistix.api.missile.virtual.VirtualMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VS2BallistixHook implements BallistixCompatHooks.Hook {

    private static final int SHIP_LOCAL_TARGET_MAX_DISTANCE = 2048;
    private static final double CONTROL_PANEL_TOO_CLOSE_DISTANCE = 100.0;

    @Override
    public Vec3 resolveLaunchPosition(Level level, BlockPos launcherPos, Vec3 defaultLaunchPos) {
        return toWorld(level, defaultLaunchPos);
    }

    @Override
    public BlockPos resolveLaunchTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        if (level == null || launcherPos == null || requestedTarget == null || !VS2ReflectionBridge.isAvailable()) {
            return requestedTarget;
        }

        if (level.hasChunkAt(requestedTarget) || isTargetTooFarFromLauncher(launcherPos, requestedTarget)) {
            return requestedTarget;
        }

        Object launcherShip = VS2ReflectionBridge.getShipManagingPos(level, launcherPos);
        if (launcherShip == null) {
            return requestedTarget;
        }

        Vec3 mapped = toWorld(launcherShip, center(requestedTarget));

        if (mapped == null) {
            return requestedTarget;
        }

        return BlockPos.containing(mapped);
    }

    @Override
    public BlockPos resolveDesignatorTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        return resolveLaunchTarget(level, launcherPos, requestedTarget);
    }

    @Override
    public BlockPos resolveHandToolTarget(Level level, Vec3 observerPosition, BlockPos lookedTarget) {
        if (level == null || lookedTarget == null || !VS2ReflectionBridge.isAvailable()) {
            return lookedTarget;
        }

        Vec3 mapped = toWorld(level, center(lookedTarget));
        if (mapped == null) {
            return lookedTarget;
        }

        return BlockPos.containing(mapped);
    }

    @Override
    public boolean isControlPanelTargetTooClose(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        if (level == null || launcherPos == null || requestedTarget == null || !VS2ReflectionBridge.isAvailable()) {
            return false;
        }

        Vec3 worldLauncher = toWorld(level, center(launcherPos));
        Vec3 worldTarget = toWorld(level, center(requestedTarget));

        if (worldLauncher == null || worldTarget == null) {
            return false;
        }

        return worldLauncher.distanceTo(worldTarget) < CONTROL_PANEL_TOO_CLOSE_DISTANCE;
    }

    @Override
    public BlockPos resolveMissileTarget(ServerLevel level, VirtualMissile missile, BlockPos storedTarget) {
        // Keep target fixed after launch so missile-control coordinates are respected.
        return storedTarget;
    }

    @Override
    public BlockPos resolveCollisionSample(ServerLevel level, Vec3 ownerPosition, Vec3 samplePosition) {
        if (samplePosition == null) {
            return BlockPos.ZERO;
        }
        BlockPos defaultPos = BlockPos.containing(samplePosition);

        if (level == null || !VS2ReflectionBridge.isAvailable()) {
            return defaultPos;
        }

        if (isSolid(level, defaultPos)) {
            return defaultPos;
        }

        for (Vec3 candidate : VS2ReflectionBridge.positionToNearbyShips(level, samplePosition)) {
            BlockPos candidatePos = BlockPos.containing(candidate);
            if (candidatePos.equals(defaultPos)) {
                continue;
            }
            if (isSolid(level, candidatePos)) {
                return candidatePos;
            }
        }

        return defaultPos;
    }

    @Override
    public boolean isPositionTicking(ServerLevel level, BlockPos blockPos, Vec3 position) {
        if (level == null || blockPos == null || position == null) {
            return false;
        }

        if (isTicking(level, blockPos)) {
            return true;
        }

        if (!VS2ReflectionBridge.isAvailable()) {
            return false;
        }

        Vec3 worldPos = toWorld(level, position);
        if (worldPos != null && isTicking(level, BlockPos.containing(worldPos))) {
            return true;
        }

        for (Vec3 candidate : VS2ReflectionBridge.positionToNearbyShips(level, position)) {
            BlockPos pos = BlockPos.containing(candidate);
            if (isTicking(level, pos)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public double distance(Level level, Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return 0;
        }

        Vec3 fromWorld = toWorld(level, from);
        Vec3 toWorld = toWorld(level, to);

        return fromWorld == null || toWorld == null ? from.distanceTo(to) : fromWorld.distanceTo(toWorld);
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isTicking(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.isPositionEntityTicking(pos);
    }

    private static boolean isTargetTooFarFromLauncher(BlockPos launcherPos, BlockPos requestedTarget) {
        return requestedTarget.distManhattan(launcherPos) > SHIP_LOCAL_TARGET_MAX_DISTANCE;
    }

    private static Vec3 center(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Vec3 toWorld(Level level, Vec3 position) {
        Vec3 mapped = VS2ReflectionBridge.toWorldCoordinates(level, position);
        return mapped == null ? position : mapped;
    }

    private static Vec3 toWorld(Object ship, Vec3 position) {
        Vec3 mapped = VS2ReflectionBridge.toWorldCoordinates(ship, position);
        return mapped == null ? position : mapped;
    }

}
