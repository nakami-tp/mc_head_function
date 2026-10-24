package film.mixin;
import com.nakami.mcheadfunction.wear.WearHandler;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Replay packets are authoritative; do not overwrite their player metadata with a new simulation. */
@Mixin(WearHandler.class)
public class ReplayWearMixin {
    @Inject(method="tickWorld",at=@At("HEAD"),cancellable=true,remap=false)
    private static void headFilm$recordedState(ServerWorld world,CallbackInfo ci){
        if(world.getServer() instanceof ReplayServer) ci.cancel();
    }
}
