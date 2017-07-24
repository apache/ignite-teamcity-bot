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
    public static final String HOST = "host";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    public static final String ENDL = String.format("%n");

    public static Properties loadAuthProperties(File workDir, String configFileName) throws IOException {
        File file = new File(workDir, configFileName);
        if (!(file.exists())) {

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(HOST + "=" + "http://ci.ignite.apache.org/" + ENDL);
                writer.write(USERNAME + "=" + ENDL);
                writer.write(PASSWORD + "=" + ENDL);
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

    static String prepareConfigName(String tcName) {
        return Strings.isNullOrEmpty(tcName) ? CONFIG_FILE_NAME : (tcName + "." + CONFIG_FILE_NAME);
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

    public static String prepareBasicHttpAuthToken(Properties props, String configName) {
        final String user = getMandatoryProperty(props, USERNAME, configName);
        final String pwd = getMandatoryProperty(props, PASSWORD, configName);
        return new String(Base64.getEncoder().encode((user +
            ":" +
            pwd).getBytes()));
    }

    private static String getMandatoryProperty(Properties props, String key, String configName) {
        final String user = props.getProperty(key);
        Preconditions.checkState(!Strings.isNullOrEmpty(user), key + " property should be filled in " + configName);
        return user;
    }
}
