package ballistixvs2compat.mixin;

import ballistix.common.tile.silo.TileLauncherControlPanelT1;
import ballistixvs2compat.VS2BallistixHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import voltaic.prefab.tile.components.type.ComponentTickable;

/**
 * Injects VS2 coordinate translation into the launcher control panel so that
 * targets and range calculations work correctly when the launcher is mounted on
 * a VS2 ship (whose blocks live in ship-local chunk coordinates).
 */
@Mixin(value = TileLauncherControlPanelT1.class, remap = false)
public abstract class MixinTileLauncherControlPanelT1 {

    @Shadow(remap = true)
    protected Level level;

    /**
     * Converts an incoming target BlockPos from ship-local space to world space
     * before it is stored. Any code that subsequently reads the target (range
     * checks, missile guidance) will then operate in world coordinates.
     */
    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private BlockPos vs2_convertSetTarget(BlockPos blockPos) {
        BlockPos launcherPos = ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).getBlockPos();
        return VS2BallistixHook.resolveLaunchTarget(level, launcherPos, blockPos);
    }

    /**
     * Same ship-local → world conversion for the designator-driven path, which
     * preserves the stored Y axis before forwarding to setTarget.
     */
    @ModifyVariable(method = "setTargetFromDesignator", at = @At("HEAD"), argsOnly = true)
    private BlockPos vs2_convertDesignatorTarget(BlockPos target) {
        BlockPos launcherPos = ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).getBlockPos();
        return VS2BallistixHook.resolveDesignatorTarget(level, launcherPos, target);
    }

    /**
     * Replaces the plain Euclidean distance used for range checking with a
     * VS2-aware version that converts both the launcher and target positions to
     * world space first. Without this, a launcher on a ship would compare
     * ship-local coordinates against world coordinates, producing wrong distances.
     */
    @Redirect(method = "tickServer",
              at = @At(value = "INVOKE",
                       target = "Lballistix/common/tile/silo/TileLauncherControlPanelT1;calculateDistance(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)D"))
    private double vs2_calculateDistance(BlockPos fromPos, BlockPos toPos) {
        Vec3 from = new Vec3(fromPos.getX() + 0.5, fromPos.getY() + 0.5, fromPos.getZ() + 0.5);
        Vec3 to   = new Vec3(toPos.getX() + 0.5,   toPos.getY() + 0.5,   toPos.getZ() + 0.5);
        return VS2BallistixHook.distance(level, from, to);
    }
}
