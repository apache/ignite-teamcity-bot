/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.ci.web;

import com.google.common.base.Preconditions;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.tcbot.common.conf.TcBotSystemProperties;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Launcher for server note and web application: all in one.
 * For client only node use {@link LauncherIgniteClientMode} and {@link TcHelperDb} for server.
 */
public class Launcher {
    /** */
    public static void main(String[] args) throws Exception {
        runServer(true);
    }

    /**
     * @param dev Dev mode.
     */
    public static void runServer(boolean dev) throws Exception {
        if(dev)
            System.setProperty(TcBotSystemProperties.DEV_MODE, "true");

        Server srv = new Server();

        ServerConnector connector = new ServerConnector(srv);
        int port = 8080;
        connector.setPort(port);
        srv.addConnector(connector);

        //working directory is expected to be module dir

        WebAppContext ctx = new WebAppContext();

        if (dev) {
            String webApp = "./ignite-tc-helper-web/src/main/webapp";
            File webResDir = new File(webApp);
            Preconditions.checkState(webResDir.exists(),
                "Resource directory [" + webResDir.getAbsolutePath() + "] does not exist");
            ctx.setDescriptor(ctx + "/WEB-INF/web.xml");
            ctx.setResourceBase(webResDir.getAbsolutePath());
            ctx.setContextPath("/");
            ctx.setParentLoaderPriority(true);
        }
        else {
            ctx.setContextPath("/");
            String war = "../war/ignite-tc-helper-web.war";
            File file = new File(war);
            Preconditions.checkState(file.exists(), "War file can not be found [" + file.getCanonicalPath() + "]");
            ctx.setWar(war);
        }
        srv.setHandler(ctx);

        System.out.println("Starting server at [" + port + "]");

        Runnable r = () -> {
            boolean stop = waitStopSignal();

            if (stop)
                stopSilent(srv);

        };

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopSilent(srv);
        }));


        new Thread(r).start();
        srv.start();
    }

    /**
     * Stops server, ignores exceptions
     * @param srv Server.
     */
    public static void stopSilent(Server srv) {
        try {
            srv.stop();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean waitStopSignal() {
        Console cons = System.console();
        if (cons != null) {
            Reader unbuffered = cons.reader();
            try {
                System.out.println("Press any key to stop");
                System.out.flush();
                int x = unbuffered.read();
                System.out.println(String.format("%08x", x));
                return x > 1;
            }
            catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        else {
            try {
                System.out.println("Press any key and Enter to stop");
                System.out.flush();
                return System.in.read() > 0;
            }
            catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
