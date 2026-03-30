package ballistix.api.compat;

import ballistix.api.missile.virtual.VirtualMissile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Compile-time stub for standalone builds when the published Ballistix artifact does not include
 * the compat API yet. This source set is not packaged into the final mod jar.
 */
public final class BallistixCompatHooks {

    private BallistixCompatHooks() {

    }

    public static void register(Hook replacement) {
        // No-op in compile-only stub.
    }

    public interface Hook {

        default Vec3 resolveLaunchPosition(Level level, BlockPos launcherPos, Vec3 defaultLaunchPos) {
            return defaultLaunchPos;
        }

        default BlockPos resolveLaunchTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
            return requestedTarget;
        }

        default BlockPos resolveDesignatorTarget(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
            return requestedTarget;
        }

        default BlockPos resolveHandToolTarget(Level level, Vec3 observerPosition, BlockPos lookedTarget) {
            return lookedTarget;
        }

        default boolean isControlPanelTargetTooClose(Level level, BlockPos launcherPos, BlockPos requestedTarget) {
            return false;
        }

        default BlockPos resolveMissileTarget(ServerLevel level, VirtualMissile missile, BlockPos storedTarget) {
            return storedTarget;
        }

        default BlockPos resolveCollisionSample(ServerLevel level, Vec3 ownerPosition, Vec3 samplePosition) {
            return samplePosition == null ? BlockPos.ZERO : BlockPos.containing(samplePosition);
        }

        default boolean isPositionTicking(ServerLevel level, BlockPos blockPos, Vec3 position) {
            if (level == null || blockPos == null) {
                return false;
            }
            return level.hasChunkAt(blockPos) && level.isPositionEntityTicking(blockPos);
        }

        default double distance(Level level, Vec3 from, Vec3 to) {
            if (from == null || to == null) {
                return 0;
            }
            return from.distanceTo(to);
        }
    }
}
