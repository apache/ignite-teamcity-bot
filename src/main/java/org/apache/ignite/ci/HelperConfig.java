package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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
    public static final String CONFIG_FILE_NAME = "auth.properties";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public static Properties loadAuthProperties(File workDir) throws IOException {
        String configFileName = CONFIG_FILE_NAME;


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
        String prop = System.getProperty("user.home");
        File workDir = new File(prop, conf);

        return ensureDirExist(workDir);
    }

    public static String prepareBasicHttpAuthToken(Properties props) {
        final String user = getMandatoryProperty(props, USERNAME);
        final String pwd = getMandatoryProperty(props, PASSWORD);
        return new String(Base64.getEncoder().encode((user +
            ":" +
            pwd).getBytes()));
    }

    private static String getMandatoryProperty(Properties props, String key) {
        final String user = props.getProperty(key);
        Preconditions.checkState(!Strings.isNullOrEmpty(user), key + " property should be filled in " + CONFIG_FILE_NAME);
        return user;
    }
}
