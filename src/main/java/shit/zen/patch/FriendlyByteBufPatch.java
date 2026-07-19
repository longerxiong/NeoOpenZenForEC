package shit.zen.patch;

import asm.patchify.annotation.Patch;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import shit.zen.ClientBase;
import shit.zen.modules.impl.render.NameProtect;

@Patch(FriendlyByteBuf.class)
public class FriendlyByteBufPatch extends ClientBase {
    public static MutableComponent readUtfWithNameProtection(String json) {
        String filtered = NameProtect.replacePlayerName(json);
        Component component = ComponentSerialization.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(filtered))
                .getOrThrow();
        return component.copy();
    }
}
