package shit.zen.patch;

import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.DeltaTracker;
import shit.zen.ZenClient;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(DeltaTracker.Timer.class)
public class TimerPatch {
    private static final String TIMER_CLASS = "net/minecraft/client/DeltaTracker$Timer";

    @Overwrite(method = "advanceGameTime", desc = "(J)I")
    public static int overwriteAdvanceGameTime(DeltaTracker.Timer timer, long currentMs) {
        long lastMs = (Long)ReflectionUtil.getStaticField(timer, "lastMs", TIMER_CLASS);
        float msPerTick = (Float)ReflectionUtil.getStaticField(timer, "msPerTick", TIMER_CLASS);
        FloatUnaryOperator targetMsptProvider = (FloatUnaryOperator)ReflectionUtil.getStaticField(timer, "targetMsptProvider", TIMER_CLASS);
        float deltaTicks = (float)(currentMs - lastMs) / targetMsptProvider.apply(msPerTick) * ZenClient.serverTickRate;
        float residual = (Float)ReflectionUtil.getStaticField(timer, "deltaTickResidual", TIMER_CLASS) + deltaTicks;
        int wholeTicks = (int)residual;

        ReflectionUtil.setInstanceField(timer, deltaTicks, "deltaTicks", TIMER_CLASS);
        ReflectionUtil.setInstanceField(timer, currentMs, "lastMs", TIMER_CLASS);
        ReflectionUtil.setInstanceField(timer, residual - wholeTicks, "deltaTickResidual", TIMER_CLASS);
        return wholeTicks;
    }
}
