package org.apache.ignite.ci;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class HelperConfig {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static Properties loadAuthProperties(File workDir) throws IOException {
        String configFileName = "auth.properties";
        File file = new File(workDir, configFileName);
        if (!(file.exists())) {

            String endl = String.format("%n");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(USERNAME +
                    "=" );
                writer.write(endl);
                writer.write(PASSWORD +
                    "=");
                writer.write(endl);
            }
            throw new IllegalStateException("Please setup username and password in config file [" +
                file.getCanonicalPath() + "]");
        }
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    public static File ensureDirExist(File workDir) {
        if (!workDir.exists())
            checkState(workDir.mkdirs(), "Unable to make directory [" + workDir + "]");

        return workDir;
    }

    public static File resolveWorkDir() {
        String conf = ".ignite-teamcity-helper";
        String property = System.getProperty("user.home");
        File workDir = new File(property, conf);

        return ensureDirExist(workDir);
    }

    public static String prepareBasicHttpAuthToken(Properties properties) {
        String user = properties.getProperty(USERNAME);
        String password = properties.getProperty(PASSWORD);
        return new String(Base64.getEncoder().encode((user +
            ":" +
            password).getBytes()));
    }
}
