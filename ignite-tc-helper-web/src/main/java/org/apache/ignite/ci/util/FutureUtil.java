package org.apache.ignite.ci.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Дмитрий on 23.02.2018.
 */
public class FutureUtil {
    @Nullable static public <V> V getResultSilent(CompletableFuture<V> fut) {
        V logCheckResult = null;
        try {
            logCheckResult = fut.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            e.printStackTrace(); //todo log
        }
        return logCheckResult;
    }
}
