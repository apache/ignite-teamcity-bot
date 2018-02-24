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

    private static final String TC_UPDATE_POOL = "tcUpdatePool";

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

        TcUpdatePool tcUpdatePool = new TcUpdatePool();

        ctx.setAttribute(TC_UPDATE_POOL, tcUpdatePool);
        ctx.setAttribute(POOL, tcUpdatePool.getService());
    }

    public static ExecutorService getPool(ServletContext context) {
        return (ExecutorService)context.getAttribute(POOL);
    }

    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();

        TcHelperDb.stop(getIgnite(ctx));

        getBackgroundUpdater(ctx).stop();

        ((TcUpdatePool)ctx.getAttribute(TC_UPDATE_POOL)).stop();
    }
}

