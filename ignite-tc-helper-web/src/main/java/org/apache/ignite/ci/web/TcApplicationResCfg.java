package org.apache.ignite.ci.web;

import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;

public class TcApplicationResCfg extends ResourceConfig {

    public TcApplicationResCfg() {
        //Register Auth Filter here
        register(AuthenticationFilter.class);

        register(LoggingFilter.class);
    }
}
