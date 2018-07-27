/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(BuildChainProcessor.class);

    /**
     * @param fut Future.
     * @return result or null if calculation failed
     */
    @Nullable public static <V> V getResultSilent(CompletableFuture<V> fut) {
        V logCheckRes = null;

        try {
            logCheckRes = fut.get();
        }
        catch (InterruptedException e) {
            logger.info("Future get reported interrupt ", e);

            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            e.printStackTrace();

            logger.error("Failed to get future result", e);
        }

        return logCheckRes;
    }
}
