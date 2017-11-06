package org.apache.ignite.ci.web.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.apache.ignite.ci.HelperConfig;
import org.apache.ignite.ci.logs.handlers.ThreadDumpCopyHandler;

/**
 * Created by Дмитрий on 06.11.2017.
 */
@Path(GetBuildLog.GET_BUILD_LOG)
@Produces(MediaType.TEXT_PLAIN)
public class GetBuildLog {

    public static final String GET_BUILD_LOG = "getBuildLog";
    public static final String THREAD_DUMP = "threadDump";
    public static final String SERVER_ID = "serverId";
    public static final String BUILD_NO = "buildNo";
    public static final String FILE_IDX = "fileIdx";

    @GET
    @Path(THREAD_DUMP)
    public Response getThreadDump(
        @QueryParam(SERVER_ID) String serverId,
        @QueryParam(BUILD_NO) Integer buildNo,
        @QueryParam(FILE_IDX) Integer fileIdx) {
        final File logsDirFileConfigured = HelperConfig.getLogsDirForServer(serverId);
        final File buildFolder = new File(logsDirFileConfigured, "buildId" + Integer.toString(buildNo));
        final String fileName = ThreadDumpCopyHandler.fileName(fileIdx);
        final File file = new File(buildFolder, fileName);
        return sendFile(file);
    }

    private Response sendFile(File file) {
        final StreamingOutput stream = os -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(os));

            BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
            }
            writer.flush();
        };
        return Response.ok(stream).build();
    }

}
