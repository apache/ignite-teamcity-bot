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

    @Override public void contextInitialized(ServletContextEvent sctxEvt) {
        final Ignite ignite = TcHelperDb.start();
        final ServletContext ctx = sctxEvt.getServletContext();
        ctx.setAttribute(IGNITE, ignite);
    }

    @Override public void contextDestroyed(ServletContextEvent sctxEvt) {
        final ServletContext ctx = sctxEvt.getServletContext();
        final Object attribute = ctx.getAttribute(IGNITE);
        if (attribute != null) {
            Ignite ignite = (Ignite)attribute;
            TcHelperDb.stop(ignite);
        }
    }
}

