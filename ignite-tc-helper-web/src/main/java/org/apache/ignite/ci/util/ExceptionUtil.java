package org.apache.ignite.ci.util;

import com.google.common.base.Throwables;
import org.apache.ignite.ci.web.rest.login.ServiceUnauthorizedException;

import java.util.Optional;

public class ExceptionUtil {
    public static RuntimeException propagateException(Exception e) {
        final Optional<Throwable> any = Throwables.getCausalChain(e)
                .stream()
                .filter(th -> (th instanceof ServiceUnauthorizedException)).findAny();
        if (any.isPresent())
            return (ServiceUnauthorizedException) any.get();

        throw Throwables.propagate(e);
    }
}
