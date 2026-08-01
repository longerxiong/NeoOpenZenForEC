package shit.zen.command.impl;

import java.io.IOException;
import shit.zen.ZenClient;
import shit.zen.command.Command;
import shit.zen.config.ConfigManager;
import shit.zen.utils.misc.ChatUtil;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", new String[]{"cfg"});
    }

    @Override
    public void onCommand(String[] stringArray) {
        if (stringArray.length >= 1) {
            switch (stringArray[0]) {
                case "reload":
                    ZenClient.getInstance().getConfigManager().load();
                    ChatUtil.print("Config reloaded!");
                    break;
                case "folder":
                    try {
                        Runtime.getRuntime().exec("explorer " + ConfigManager.CONFIG_DIR.getAbsolutePath());
                    } catch (IOException ignored) {
                    }
                    break;
                case "load":
                    if (stringArray.length >= 2) {
                        ZenClient.getInstance().getConfigManager().loadConfig(stringArray[1]);
                        ChatUtil.print("Config " + stringArray[1] + " loaded!");
                    } else {
                        ChatUtil.print("Usage: config load <name>");
                    }
                    break;
                case "save":
                    if (stringArray.length >= 2) {
                        ZenClient.getInstance().getConfigManager().saveConfig(stringArray[1]);
                        ChatUtil.print("Config " + stringArray[1] + " saved!");
                    } else {
                        ChatUtil.print("Usage: config save <name>");
                    }
                    break;
            }
        } else {
            ChatUtil.print("Usage: config reload/folder/load/save");
        }
    }

    @Override
    public String[] onTab(String[] stringArray) {
        return new String[0];
    }
}
