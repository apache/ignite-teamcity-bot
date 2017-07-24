package org.apache.ignite.ci;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.apache.ignite.ci.logs.LogsAnalyzer;
import org.apache.ignite.ci.logs.handlers.LastTestLogCopyHandler;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;
import org.apache.ignite.ci.util.HttpUtil;

import static org.apache.ignite.ci.HelperConfig.ensureDirExist;

/**
 * Created by Дмитрий on 21.07.2017.
 *
 *
 * https://confluence.jetbrains.com/display/TCD10/REST+API
 */
public class IgniteTeamcityHelper {

    private final Executor executor;
    private final File logsDir;
    private final String host;
    private final String basicAuthToken;
    private final String configName; //main properties file name

    public IgniteTeamcityHelper() throws IOException {
        this(null);
    }

    public IgniteTeamcityHelper(String tcName) throws IOException {
        final File workDir = HelperConfig.resolveWorkDir();
        this.configName = HelperConfig.prepareConfigName(tcName);
        final Properties props = HelperConfig.loadAuthProperties(workDir, configName);
        this.host = props.getProperty(HelperConfig.HOST, "http://ci.ignite.apache.org/");
        basicAuthToken = HelperConfig.prepareBasicHttpAuthToken(props, configName);
        logsDir = ensureDirExist(new File(workDir, "logs"));
        this.executor = MoreExecutors.directExecutor();
    }

    public CompletableFuture<File> downloadBuildLogZip(int buildId) {
        final Supplier<File> buildLog = new DownloadBuildLog(buildId,
            host, basicAuthToken, logsDir, true);
        return CompletableFuture.supplyAsync(buildLog, executor);
    }

    public void triggerBuild(String buildTypeId, String branchName) {
        String parameter = "<build  branchName=\"" + branchName +
            "\">\n" +
            "    <buildType id=\"" +
            buildTypeId + "\"/>\n" +
            "    <comment><text>Build triggered from [" + this.getClass().getSimpleName() + "]</text></comment>\n" +
            //some fake property to avoid merging build in queue
            "    <properties>\n" +
            "        <property name=\"build.query.ts\" value=\"" + System.currentTimeMillis() + "\"/>\n" +
            "    </properties>\n" +
            "</build>";
        String url = host + "/app/rest/buildQueue";
        try {
            HttpUtil.sendPostAsString(basicAuthToken, url, parameter);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public CompletableFuture<List<File>> unzip(CompletableFuture<File> zipFileFut) {
        return zipFileFut.thenApplyAsync(ZipUtil::unZipToSameFolder, executor);
    }

    public CompletableFuture<File> unzipFirstFile(CompletableFuture<File> fut) {
        final CompletableFuture<List<File>> clearFileF = unzip(fut);
        return clearFileF.thenApplyAsync(files -> {
            Preconditions.checkState(!files.isEmpty(), "ZIP file can't be empty");
            return files.get(0);
        }, executor);
    }

    public List<CompletableFuture<File>> standardProcessLogs(int... buildIds) {
        List<CompletableFuture<File>> futures = new ArrayList<>();
        for (int buildId : buildIds) {
            final CompletableFuture<File> zipFut = downloadBuildLogZip(buildId);
            final CompletableFuture<File> clearLogFut = unzipFirstFile(zipFut);
            final ThreadDumpCopyHandler search = new ThreadDumpCopyHandler();
            final LogsAnalyzer analyzer = new LogsAnalyzer(search, new LastTestLogCopyHandler());
            final CompletableFuture<File> future2 = clearLogFut.thenApplyAsync(analyzer);
            futures.add(future2);
        }
        return futures;
    }
}
