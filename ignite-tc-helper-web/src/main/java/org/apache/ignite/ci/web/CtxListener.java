package org.apache.ignite.ci.web;

import java.util.concurrent.ExecutorService;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.db.TcHelperDb;

/**
 */
public class CtxListener implements ServletContextListener {

    public static final String IGNITE = "ignite";

    public static final String UPDATER = "updater";

    private static final String POOL = "pool";

    public static Ignite getIgnite(ServletContext ctx) {
        return (Ignite)ctx.getAttribute(IGNITE);
    }

    public  static BackgroundUpdater getBackgroundUpdater(ServletContext ctx) {
        return (BackgroundUpdater)ctx.getAttribute(UPDATER);
    }

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        final Ignite ignite = TcHelperDb.start();
        final ServletContext ctx = sctxEvt.getServletContext();
        ctx.setAttribute(IGNITE, ignite);

        BackgroundUpdater object = new BackgroundUpdater(ignite);

        ctx.setAttribute(UPDATER, object);

        ctx.setAttribute(POOL, object.getService());
    }


    public static ExecutorService getPool(ServletContext context) {
        return (ExecutorService)context.getAttribute(POOL);
    }


    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();

        BackgroundUpdater updater = (BackgroundUpdater)ctx.getAttribute(UPDATER);
        if (updater != null)
            updater.stop();

        final Object attribute = ctx.getAttribute(IGNITE);
        if (attribute != null) {
            Ignite ignite = (Ignite)attribute;
            TcHelperDb.stop(ignite);
        }
    }
}

