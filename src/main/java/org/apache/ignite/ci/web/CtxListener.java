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

    @Override public void contextInitialized(ServletContextEvent sctxEvent) {
        Ignite ignite = TcHelperDb.start();
        ServletContext context = sctxEvent.getServletContext();
        context.setAttribute(IGNITE, ignite);
        System.out.println("ServletContextListener destroyed");
    }

    @Override public void contextDestroyed(ServletContextEvent sctxEvent) {
        ServletContext context = sctxEvent.getServletContext();
        Object attribute = context.getAttribute(IGNITE);
        if (attribute instanceof Ignite) {
            Ignite ignite = (Ignite)attribute;
            TcHelperDb.stop(ignite);
        }
    }
}

