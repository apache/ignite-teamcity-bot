package org.apache.ignite.ci.web;

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

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        final Ignite ignite = TcHelperDb.start();
        final ServletContext ctx = sctxEvt.getServletContext();
        ctx.setAttribute(IGNITE, ignite);

        ctx.setAttribute(UPDATER, new BackgroundUpdater(ignite));
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

