package org.apache.ignite.ci.actions;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import org.apache.ignite.ci.util.HttpUtil;

import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 21.07.2017
 */
public class DownloadBuildLog implements Supplier<File> {
    private int buildId;
    private final String tok;
    private final File dir;
    private String hostNormalized;
    private boolean archive;


    public DownloadBuildLog(int buildId, String hostNormalized, String tok,
        File logsDir, boolean archive) {
        this.buildId = buildId;
        this.tok = tok;
        this.dir = logsDir;
        this.hostNormalized = hostNormalized;
        this.archive = archive;
    }

    public File get() {
        String buildIdStr = Integer.toString(buildId);
        final File buildDirectory = ensureDirExist(new File(dir, "buildId" + buildIdStr));
        final File file = new File(buildDirectory,
            "build.log" + (archive ? ".zip" : ""));
        if (file.exists() && file.canRead() && file.length() > 0) {
            System.out.println("Nothing to do, file is cached locally: [" + file + "]");
            return file;
        }
        String hostWithSlash = hostNormalized;
        String url = hostWithSlash + "downloadBuildLog.html" +
            "?buildId=" + buildIdStr
            + (archive ? "&archived=true" : "");

        try {
            HttpUtil.sendGetCopyToFile(tok, url, file);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

}
