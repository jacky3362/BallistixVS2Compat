package ballistixvs2compat;

import ballistix.api.compat.BallistixCompatHooks;
import ballistix.api.missile.virtual.VirtualMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VS2BallistixHook implements BallistixCompatHooks.Hook {

    @Override
    public Vec3 resolveLaunchPosition(Level level, BlockPos launcherPos, Vec3 defaultLaunchPos) {
        if (defaultLaunchPos == null || level == null || !VS2ReflectionBridge.isAvailable()) {
            return defaultLaunchPos;
        }

        Vec3 worldPos = VS2ReflectionBridge.toWorldCoordinates(level, defaultLaunchPos);
        return worldPos == null ? defaultLaunchPos : worldPos;
    }

    @Override
    public BlockPos resolveLaunchTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        return requestedTarget;
    }

    @Override
    public BlockPos resolveDesignatorTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        return requestedTarget;
    }

    @Override
    public BlockPos resolveMissileTarget(ServerLevel level, VirtualMissile missile, BlockPos storedTarget) {
        if (level == null || storedTarget == null || !VS2ReflectionBridge.isAvailable()) {
            return storedTarget;
        }

        Object ship = VS2ReflectionBridge.getShipManagingPos(level, storedTarget);
        if (ship == null) {
            return storedTarget;
        }

        Vec3 worldPos = VS2ReflectionBridge.toWorldCoordinates(ship,
                new Vec3(storedTarget.getX() + 0.5, storedTarget.getY() + 0.5, storedTarget.getZ() + 0.5));

        return worldPos == null ? storedTarget : BlockPos.containing(worldPos);
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

        if (level.hasChunkAt(blockPos) && level.isPositionEntityTicking(blockPos)) {
            return true;
        }

        if (!VS2ReflectionBridge.isAvailable()) {
            return false;
        }

        Vec3 worldPos = VS2ReflectionBridge.toWorldCoordinates(level, position);
        if (worldPos != null) {
            BlockPos worldBlock = BlockPos.containing(worldPos);
            if (level.hasChunkAt(worldBlock) && level.isPositionEntityTicking(worldBlock)) {
                return true;
            }
        }

        for (Vec3 candidate : VS2ReflectionBridge.positionToNearbyShips(level, position)) {
            BlockPos pos = BlockPos.containing(candidate);
            if (level.hasChunkAt(pos) && level.isPositionEntityTicking(pos)) {
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

        if (level == null || !VS2ReflectionBridge.isAvailable()) {
            return from.distanceTo(to);
        }

        Vec3 fromWorld = VS2ReflectionBridge.toWorldCoordinates(level, from);
        Vec3 toWorld = VS2ReflectionBridge.toWorldCoordinates(level, to);

        if (fromWorld == null || toWorld == null) {
            return from.distanceTo(to);
        }

        return fromWorld.distanceTo(toWorld);
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

}
