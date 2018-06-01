package org.apache.ignite.ci.web.rest.login;

import org.eclipse.jetty.server.Authentication;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

public class TcHelperExceptionMapper extends Exception  implements ExceptionMapper<Exception> {


    @Override
    public Response toResponse(Exception exception) {
        if(exception instanceof ServiceUnauthorizedException  ) {
            return Response.status(424).entity(exception.getMessage())
                    .type("text/plain").build();
        }
        else return Response.status(501).build();
    }

}
