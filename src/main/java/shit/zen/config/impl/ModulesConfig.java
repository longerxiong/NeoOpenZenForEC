package shit.zen.config.impl;

import java.io.*;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.config.Config;
import shit.zen.hud.HudElement;
import shit.zen.modules.Module;
import shit.zen.modules.settings.Setting;
import shit.zen.modules.settings.impl.BooleanSetting;
import shit.zen.modules.settings.impl.ModeSetting;
import shit.zen.modules.settings.impl.MultiSelectSetting;
import shit.zen.modules.settings.impl.NumberSetting;

public class ModulesConfig
extends Config {
    private static final Logger logger = LogManager.getLogger(ModulesConfig.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ModulesConfig() {
        super("modules");
    }

    public ModulesConfig(String name) {
        super(name);
    }

    @Override
    public void read(BufferedReader bufferedReader) throws IOException {
        try {
            final JsonElement jsonElement = JsonParser.parseReader(bufferedReader);
            if (jsonElement == null || !jsonElement.isJsonObject()) {
                return;
            }
            final JsonObject jsonObject = jsonElement.getAsJsonObject();

            for (final Module module : ZenClient.getInstance().getModuleManager().getModules()) {
                if (!jsonObject.has(module.getName())) {
                    continue;
                }

                final JsonObject moduleObject = jsonObject.getAsJsonObject(module.getName());
                this.deserializeModule(module, moduleObject);
            }
        } catch (final Exception exception) {
            logger.debug("Failed to load config: {}. Error: {}", this.getName(), exception.getMessage());
        }
    }

    @Override
    public void save(BufferedWriter bufferedWriter) throws IOException {
        try {
            final JsonObject jsonObject = new JsonObject();

            for (Module module : ZenClient.getInstance().getModuleManager().getModules()) {
                final JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("enabled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());

                if (module instanceof HudElement drag) {
                    moduleObject.addProperty("x", drag.getX());
                    moduleObject.addProperty("y", drag.getY());
                }

                if (!module.getSettings().isEmpty()) {
                    moduleObject.add("values", this.serializeValues(module));
                }
                jsonObject.add(module.getName(), moduleObject);
            }

            bufferedWriter.write(this.gson.toJson(jsonObject));
        } catch (final Exception exception) {
            logger.debug("Failed to save config: {}. Error: {}", this.getName(), exception.getMessage());
        }
    }

    private JsonObject serializeValues(final Module module) {
        final JsonObject valuesObject = new JsonObject();
        for (final Setting<?> value : module.getSettings()) {
            switch (value) {
                case BooleanSetting bool -> valuesObject.addProperty(bool.getName(), bool.getValue());
                case NumberSetting num -> valuesObject.addProperty(num.getName(), num.getValue());
                case ModeSetting mode -> valuesObject.addProperty(mode.getName(), mode.getValue());
//                case Color color -> valuesObject.addProperty(color.getName(), color.getValue().getRGB());
                case MultiSelectSetting multi -> multi.save(valuesObject);
                default -> {}
            }
        }
        return valuesObject;
    }

    private void deserializeModule(final Module module, final JsonObject moduleObject) {
        // 先禁用，等 values 加载完再启用，避免 onEnable 读到旧值
        if (module.isEnabled()) {
            module.setEnabled(false);
        }

        if (moduleObject.has("key")) {
            module.setKey(moduleObject.get("key").getAsInt());
        }

        if (module instanceof HudElement drag) {
            if (moduleObject.has("x")) {
                drag.setX(moduleObject.get("x").getAsFloat());
            }
            if (moduleObject.has("y")) {
                drag.setY(moduleObject.get("y").getAsFloat());
            }
        }

        if (moduleObject.has("values") && !module.getSettings().isEmpty()) {
            final JsonObject valuesObject = moduleObject.getAsJsonObject("values");
            for (final Setting<?> value : module.getSettings()) {
                if (!valuesObject.has(value.getName())) {
                    continue;
                }
                final JsonElement element = valuesObject.get(value.getName());
                try {
                    switch (value) {
                        case BooleanSetting bool -> bool.setValue(element.getAsBoolean());
                        case NumberSetting num -> num.setValue(element.getAsFloat());
                        case ModeSetting mode -> mode.setValue(element.getAsString());
                        case MultiSelectSetting multi -> multi.load(element);
                        default -> {}
                    }
                } catch (final Exception exception) {
                    logger.debug("Failed to load value {}: {}", value.getName(), exception.getMessage());
                }
            }
        }

        // 最后再按配置启用
        if (moduleObject.has("enabled")) {
            final boolean shouldEnable = moduleObject.get("enabled").getAsBoolean();
            if (shouldEnable) {
                module.setEnabled(true);
            }
        }
    }
}