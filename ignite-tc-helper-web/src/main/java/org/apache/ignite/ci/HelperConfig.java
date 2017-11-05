package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Properties;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ignite.ci.conf.BranchesTracked;
import org.apache.ignite.ci.util.Base64Util;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class HelperConfig {
    public static final String CONFIG_FILE_NAME = "auth.properties";
    public static final String RESP_FILE_NAME = "resp.properties";
    public static final String HOST = "host";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    public static final String LOGS = "logs";
    public static final String ENDL = String.format("%n");

    public static Properties loadAuthProperties(File workDir, String configFileName) {
        try {
            return loadAuthPropertiesX(workDir, configFileName);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties loadAuthPropertiesX(File workDir, String configFileName) throws IOException {
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
        return loadProps(file);
    }

    private static Properties loadProps(File file) throws IOException {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    static String prepareConfigName(String tcName) {
        return prefixedWithServerName(tcName, CONFIG_FILE_NAME);
    }

    private static String prefixedWithServerName(String tcName, String name) {
        return Strings.isNullOrEmpty(tcName) ? name : (tcName + "." + name);
    }

    public static File ensureDirExist(File workDir) {
        if (!workDir.exists())
            checkState(workDir.mkdirs(), "Unable to make directory [" + workDir + "]");

        return workDir;
    }

    public static File resolveWorkDir() {
        File workDir = null;
        String property = System.getProperty(IgniteTeamcityHelper.TEAMCITY_HELPER_HOME);
        if (Strings.isNullOrEmpty(property)) {
            String conf = ".ignite-teamcity-helper";
            String prop = System.getProperty("user.home");
            //relative in work dir
            workDir = Strings.isNullOrEmpty(prop) ? new File(conf) : new File(prop, conf);
        }
        else
            workDir = new File(property);

        return ensureDirExist(workDir);
    }

    public static String prepareBasicHttpAuthToken(Properties props, String configName) {
        final String user = getMandatoryProperty(props, USERNAME, configName);
        final String pwd = getMandatoryProperty(props, PASSWORD, configName);
        String str = user + ":" + pwd;
        return Base64Util.encodeUtf8String(str);
    }

    private static String getMandatoryProperty(Properties props, String key, String configName) {
        final String user = props.getProperty(key);
        Preconditions.checkState(!Strings.isNullOrEmpty(user), key + " property should be filled in " + configName);
        return user;
    }

    public static Properties loadPrefixedProperties(String tcName, String name) throws IOException {
        String respConf = prefixedWithServerName(tcName, name);
        final File workDir = resolveWorkDir();
        File file = new File(workDir, respConf);
        return loadProps(file);
    }

    public static BranchesTracked getTrackedBranches() {
        final File workDir = resolveWorkDir();
        final File file = new File(workDir, "branches.json");
        final FileReader json;
        try {
            json = new FileReader(file);
        }
        catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
        return new Gson().fromJson(json, BranchesTracked.class);
    }

    public static Properties loadContactPersons(String tcName) throws IOException {
        try {
            return loadPrefixedProperties(tcName, RESP_FILE_NAME);
        }
        catch (IOException e) {
            e.printStackTrace();
            return new Properties();
        }
    }
}
