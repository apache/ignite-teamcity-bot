package org.apache.ignite.ci;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class DownloadBuildLog implements Supplier<File> {
    private int buildId;
    private final String token;
    private final File dir;
    private String host;
    private boolean archive;

    public DownloadBuildLog(int buildId, String host, String basicAuthToken, File logsDir) {
        this(buildId, host, basicAuthToken, logsDir, false);
    }

    public DownloadBuildLog(int buildId, String host, String basicAuthToken,
        File logsDir, boolean archive) {
        this.buildId = buildId;
        this.token = basicAuthToken;
        this.dir = logsDir;
        this.host = host;
        this.archive = archive;
    }

    public File get()  {
        String buildIdStr = Integer.toString(buildId);
        String url = host + "downloadBuildLog.html" +
            "?buildId=" + buildIdStr
            + (archive ? "&archived=true" : "");
        final File buildDirectory = ensureDirExist(new File(dir, "buildId" + buildIdStr));
        final File file = new File(buildDirectory,
            "build.log" + (archive ? ".zip" : ""));
        if (file.exists() && file.canRead() && file.length() > 0) {
            System.err.println("Nothing to do, file is cached " + file);
            return file;
        }
        try {
            HttpUtil.sendGetCopyToFile(token, url, file);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

}
