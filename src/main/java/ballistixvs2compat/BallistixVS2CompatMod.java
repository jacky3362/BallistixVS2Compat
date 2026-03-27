package ballistixvs2compat;

import ballistix.api.compat.BallistixCompatHooks;
import net.minecraftforge.fml.common.Mod;

@Mod(BallistixVS2CompatMod.ID)
public class BallistixVS2CompatMod {

    public static final String ID = "ballistixvs2compat";

    public BallistixVS2CompatMod() {
        // Always register; the hook internally degrades to vanilla behavior when VS2 APIs are unavailable.
        BallistixCompatHooks.register(new VS2BallistixHook());
    }
}
