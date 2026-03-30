package ballistixvs2compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ballistix.api.compat.BallistixCompatHooks;
import ballistix.api.missile.virtual.VirtualMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class VS2BallistixHook implements BallistixCompatHooks.Hook {

    private static final String VS2_MODID = "valkyrienskies";
    private static final int SHIP_LOCAL_TARGET_MAX_DISTANCE = 2048;
    private static final double CONTROL_PANEL_TOO_CLOSE_DISTANCE = 100.0;

    @Override
    public Vec3 resolveLaunchPosition(Level level, BlockPos launcherPos, Vec3 defaultLaunchPos) {
        if (defaultLaunchPos == null || level == null || !isVs2Available()) {
            return defaultLaunchPos;
        }

        Vec3 worldPos = toWorldCoordinates(level, defaultLaunchPos);
        return worldPos == null ? defaultLaunchPos : worldPos;
    }

    @Override
    public BlockPos resolveLaunchTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        if (level == null || launcherPos == null || requestedTarget == null || !isVs2Available()) {
            return requestedTarget;
        }

        // If the target chunk exists, treat the coordinates as world-space from missile control input.
        if (level.hasChunkAt(requestedTarget)) {
            return requestedTarget;
        }

        // Only remap when target looks local to the launcher shipyard region.
        if (requestedTarget.distManhattan(launcherPos) > SHIP_LOCAL_TARGET_MAX_DISTANCE) {
            return requestedTarget;
        }

        Object launcherShip = getShipManagingPos(level, launcherPos);
        if (launcherShip == null) {
            return requestedTarget;
        }

        Vec3 mapped = toWorldCoordinates(
                launcherShip,
                new Vec3(requestedTarget.getX() + 0.5, requestedTarget.getY() + 0.5, requestedTarget.getZ() + 0.5));

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
        if (level == null || lookedTarget == null || !isVs2Available()) {
            return lookedTarget;
        }

        Vec3 lookedCenter = new Vec3(lookedTarget.getX() + 0.5, lookedTarget.getY() + 0.5, lookedTarget.getZ() + 0.5);
        Vec3 mapped = toWorldCoordinates(level, lookedCenter);
        if (mapped == null) {
            return lookedTarget;
        }

        return BlockPos.containing(mapped);
    }

    @Override
    public boolean isControlPanelTargetTooClose(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
        if (level == null || launcherPos == null || requestedTarget == null || !isVs2Available()) {
            return false;
        }

        Vec3 launcher = new Vec3(launcherPos.getX() + 0.5, launcherPos.getY() + 0.5, launcherPos.getZ() + 0.5);
        Vec3 target = new Vec3(requestedTarget.getX() + 0.5, requestedTarget.getY() + 0.5, requestedTarget.getZ() + 0.5);

        Vec3 worldLauncher = toWorldCoordinates(level, launcher);
        Vec3 worldTarget = toWorldCoordinates(level, target);

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

        if (level == null || !isVs2Available()) {
            return defaultPos;
        }

        if (isSolid(level, defaultPos)) {
            return defaultPos;
        }

        for (Vec3 candidate : positionToNearbyShips(level, samplePosition)) {
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

        if (!isVs2Available()) {
            return false;
        }

        Vec3 worldPos = toWorldCoordinates(level, position);
        if (worldPos != null) {
            BlockPos worldBlock = BlockPos.containing(worldPos);
            if (level.hasChunkAt(worldBlock) && level.isPositionEntityTicking(worldBlock)) {
                return true;
            }
        }

        for (Vec3 candidate : positionToNearbyShips(level, position)) {
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

        if (level == null || !isVs2Available()) {
            return from.distanceTo(to);
        }

        Vec3 fromWorld = toWorldCoordinates(level, from);
        Vec3 toWorld = toWorldCoordinates(level, to);

        if (fromWorld == null || toWorld == null) {
            return from.distanceTo(to);
        }

        return fromWorld.distanceTo(toWorld);
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isVs2Available() {
        return ModList.get().isLoaded(VS2_MODID);
    }

    private static Object getShipManagingPos(Level level, BlockPos pos) {
        if (!isVs2Available() || level == null || pos == null) {
            return null;
        }
        try {
            return Vs2Api.getShipManagingPos(level, pos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vec3 toWorldCoordinates(Level level, Vec3 position) {
        if (!isVs2Available() || level == null || position == null) {
            return position;
        }
        try {
            return Vs2Api.toWorldCoordinates(level, position);
        } catch (Throwable ignored) {
            return position;
        }
    }

    private static Vec3 toWorldCoordinates(Object ship, Vec3 position) {
        if (!isVs2Available() || ship == null || position == null) {
            return position;
        }
        try {
            return Vs2Api.toWorldCoordinates(ship, position);
        } catch (Throwable ignored) {
            return position;
        }
    }

    private static Iterable<Vec3> positionToNearbyShips(Level level, Vec3 position) {
        if (!isVs2Available() || level == null || position == null) {
            return Collections.emptyList();
        }

        try {
            List<Vector3d> transformed = Vs2Api.transformToNearbyShipsAndWorld(level, position);
            if (transformed == null || transformed.isEmpty()) {
                return Collections.emptyList();
            }

            List<Vec3> out = new ArrayList<>(transformed.size());
            for (Vector3d vec : transformed) {
                out.add(new Vec3(vec.x(), vec.y(), vec.z()));
            }
            return out;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static final class Vs2Api {

        private Vs2Api() {

        }

        private static Object getShipManagingPos(Level level, BlockPos pos) {
            return VSGameUtilsKt.getShipManagingPos(level, pos);
        }

        private static Vec3 toWorldCoordinates(Level level, Vec3 position) {
            return VSGameUtilsKt.toWorldCoordinates(level, position);
        }

        private static Vec3 toWorldCoordinates(Object ship, Vec3 position) {
            return VSGameUtilsKt.toWorldCoordinates((org.valkyrienskies.core.api.ships.Ship) ship, position);
        }

        private static List<Vector3d> transformToNearbyShipsAndWorld(Level level, Vec3 position) {
            return VSGameUtilsKt.transformToNearbyShipsAndWorld(level, position.x, position.y, position.z, 1.0D);
        }
    }

}
