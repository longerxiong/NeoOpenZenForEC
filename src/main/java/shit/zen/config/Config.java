package shit.zen.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import lombok.Getter;

public abstract class Config {
    @Getter
    private final String name;
    @Getter
    private final File file;

    public Config(String name) {
        this.name = name;
        this.file = new File(ConfigManager.CONFIG_DIR, name + ".json");
    }

    public abstract void read(BufferedReader var1) throws IOException;

    public abstract void save(BufferedWriter var1) throws IOException;

    }