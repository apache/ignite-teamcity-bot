package org.apache.ignite.ci.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.ignite.ci.BuildChainProcessor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Дмитрий on 23.02.2018.
 */
public class FutureUtil {

    private static final Logger logger = LoggerFactory.getLogger(BuildChainProcessor.class);

    @Nullable static public <V> V getResultSilent(CompletableFuture<V> fut) {
        V logCheckResult = null;
        try {
            logCheckResult = fut.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            e.printStackTrace();

            logger.error("Failed to get future result", e);
        }
        return logCheckResult;
    }
}
