package ballistixvs2compat.mixin;

import ballistix.common.tile.silo.TileLauncherPlatformT1;
import ballistixvs2compat.VS2BallistixHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Injects VS2 coordinate translation into the launcher platform so that
 * missiles launched from a ship start at the correct world-space position
 * instead of the ship-local block position.
 */
@Mixin(value = TileLauncherPlatformT1.class, remap = false)
public abstract class MixinTileLauncherPlatformT1 {

    @Shadow(remap = true)
    protected Level level;

    /**
     * Converts the missile start Vec3 (computed from the launcher's block
     * position) from ship-local space to world space. Without this fix, a
     * missile fired from a ship would originate at the ship-local chunk
     * coordinates rather than the correct world position.
     *
     * <p>The constructor descriptor covers all 11 parameters of:
     * {@code VirtualMissile(Vec3, Vec3, float, FlightPath, float, float,
     * BlockPos, int, IBlast, int, boolean)}</p>
     */
    @ModifyArg(
            method = "launchMissile",
            at = @At(value = "INVOKE",
                     target = "Lballistix/api/missile/virtual/VirtualMissile;<init>"
                              + "(Lnet/minecraft/world/phys/Vec3;"
                              + "Lnet/minecraft/world/phys/Vec3;"
                              + "F"
                              + "Lballistix/api/missile/virtual/VirtualMissile$FlightPath;"
                              + "FF"
                              + "Lnet/minecraft/core/BlockPos;"
                              + "I"
                              + "Lballistix/api/blast/IBlast;"
                              + "IZ)V"),
            index = 0
    )
    private Vec3 vs2_fixLaunchPosition(Vec3 startPos) {
        BlockPos launcherPos = ((net.minecraft.world.level.block.entity.BlockEntity) (Object) this).getBlockPos();
        return VS2BallistixHook.resolveLaunchPosition(level, launcherPos, startPos);
    }
}
