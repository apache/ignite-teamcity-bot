package org.apache.ignite.ci.web.auth;

import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;

public class TcApplicationResCfg extends ResourceConfig {

    public TcApplicationResCfg() {
        //Register Auth Filter here
        register(AuthenticationFilter.class);

        register(LoggingFilter.class);
    }
}
