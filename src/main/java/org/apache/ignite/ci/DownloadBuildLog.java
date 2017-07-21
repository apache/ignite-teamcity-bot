package org.apache.ignite.ci;

import java.io.File;
import java.io.IOException;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class DownloadBuildLog {
    private int buildId;
    private final String token;
    private final File dir;

    public DownloadBuildLog(int buildId, String basicAuthToken, File logsDir) {
        this.buildId = buildId;
        token = basicAuthToken;
        dir = logsDir;
    }

    public File run() throws IOException {
        String buildIdStr = Integer.toString(buildId);
        String url = "http://ci.ignite.apache.org/downloadBuildLog.html?buildId=" + buildIdStr;
        File file = new File(dir, "buildId" + buildIdStr + ".log");
        if(file.exists() && file.canRead() && file.length()>0) {
            System.err.println("Nothing to do, file is cached " + file);
            return file;
        }
        HttpUtil.sendGetCopyToFile(token, url, file);
        return file;
 

    }
}
