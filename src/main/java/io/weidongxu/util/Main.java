package io.weidongxu.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        Configure configure;
        try (InputStream in = Main.class.getResourceAsStream("/configure.yml")) {
            Yaml yaml = new Yaml();
            configure = yaml.loadAs(in, Configure.class);
        }

        Transform transform = new Transform(configure);
        transform.process();
    }
}
