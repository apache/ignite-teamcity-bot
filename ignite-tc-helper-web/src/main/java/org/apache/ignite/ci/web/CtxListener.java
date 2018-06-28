package org.apache.ignite.ci.web;

import java.util.concurrent.ExecutorService;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITcHelper;
import org.apache.ignite.ci.TcHelper;
import org.apache.ignite.ci.db.TcHelperDb;

/**
 */
public class CtxListener implements ServletContextListener {
    private static final String TC_HELPER = "tcHelper";

    public static final String IGNITE = "ignite";

    public static final String UPDATER = "updater";


    private static final String POOL = "pool";

    public static Ignite getIgnite(ServletContext ctx) {
        return (Ignite)ctx.getAttribute(IGNITE);
    }

    public static ITcHelper getTcHelper(ServletContext ctx) {
        return (ITcHelper)ctx.getAttribute(TC_HELPER);
    }

    public static BackgroundUpdater getBackgroundUpdater(ServletContext ctx) {
        return (BackgroundUpdater)ctx.getAttribute(UPDATER);
    }

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        final Ignite ignite = TcHelperDb.start();
        final ServletContext ctx = sctxEvt.getServletContext();
        ctx.setAttribute(IGNITE, ignite);

        BackgroundUpdater backgroundUpdater = new BackgroundUpdater();

        ctx.setAttribute(UPDATER, backgroundUpdater);

        TcHelper tcHelper = new TcHelper(ignite);

        ctx.setAttribute(TC_HELPER, tcHelper);
        ctx.setAttribute(POOL, tcHelper.getService());
    }

    public static ExecutorService getPool(ServletContext context) {
        return (ExecutorService)context.getAttribute(POOL);
    }

    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();

        TcHelperDb.stop(getIgnite(ctx));

        getBackgroundUpdater(ctx).stop();

        TcHelper helper = (TcHelper)getTcHelper(ctx);
        helper.close();
    }
}

