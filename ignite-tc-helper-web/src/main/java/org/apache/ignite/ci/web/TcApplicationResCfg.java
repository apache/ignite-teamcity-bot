package org.apache.ignite.ci.web;

import org.apache.ignite.ci.web.auth.AuthenticationFilter;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class TcApplicationResCfg extends ResourceConfig {

    public TcApplicationResCfg() {
        //Register Auth Filter here
        register(AuthenticationFilter.class);

        register(LoggingFeature.class);

        register(ServiceUnauthorizedException.class);
    }
}
