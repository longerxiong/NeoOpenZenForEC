package shit.zen.patch;

import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ModListScreen;
import shit.zen.ZenClient;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(ModListScreen.class)
public final class ModListScreenPatch {
    private static final String SCREEN_CLASS = "net/neoforged/neoforge/client/gui/ModListScreen";

    @Overwrite(method = "buildModList", desc = "(Ljava/util/function/Consumer;Ljava/util/function/Function;)V")
    @SuppressWarnings("unchecked")
    public static <T> void overwriteBuildModList(ModListScreen screen, Consumer<T> entryConsumer,
                                                  Function<ModContainer, T> entryFactory) {
        List<ModContainer> mods = (List<ModContainer>)ReflectionUtil.getStaticField(screen, "mods", SCREEN_CLASS);
        mods.stream()
                .filter(mod -> !ZenClient.MOD_ID.equals(mod.getModInfo().getModId()))
                .map(entryFactory)
                .forEach(entryConsumer);
    }
}
