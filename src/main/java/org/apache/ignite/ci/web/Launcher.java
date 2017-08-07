package org.apache.ignite.ci.web;

import com.google.common.base.Preconditions;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Created by Дмитрий on 04.08.2017
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        Server server = new Server();

        ServerConnector connector = new ServerConnector(server);
        int port = 8080;
        connector.setPort(port);
        server.addConnector(connector);

        //working directory is expected to be module dir
        String webApp = "./src/main/webapp";
        File webResDir = new File(webApp);
        Preconditions.checkState(webResDir.exists(),
            "Resource directory [" + webResDir.getAbsolutePath() + "] does not exist");

        WebAppContext ctx = new WebAppContext();

        boolean dev = true;
        if(dev) {
            ctx.setDescriptor(ctx +"/WEB-INF/web.xml");
            ctx.setResourceBase(webResDir.getAbsolutePath());
            ctx.setContextPath("/");
            ctx.setParentLoaderPriority(true);
        } else {

            ctx.setContextPath("/");
            ctx.setWar("build/libs/ignite-teamcity-helper.war");
        }
        server.setHandler(ctx);

        System.out.println("Starting server at [" + port + "]");

        Runnable r = () -> {
            boolean stop = waitStopSignal();
            if (stop) {
                try {
                    server.stop();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        };
        new Thread(r).start();
        server.start();
    }

    private static boolean waitStopSignal()  {
        Console cons = System.console();
        if (cons != null) {
            Reader unbuffered = cons.reader();
            try {
                System.out.println("Press any key to stop");
                System.out.flush();
                int x = unbuffered.read();
                System.out.println(String.format("%08x", x));
                return x>1;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            try {
                System.out.println("Press any key and Enter to stop");
                System.out.flush();
                return  System.in.read() > 0;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
