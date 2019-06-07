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

package org.apache.ignite.tcbot.common.util;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Async computation util.
 */
public class FutureUtil {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(FutureUtil.class);

    /**
     * @param fut Future.
     * @return result or null if calculation failed
     */
    @Nullable public static <V> V getResultSilent(Future<V> fut) {
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

    /**
     * @param fut Future.
     * @return result or null if calculation failed
     */
    @Nullable public static <V> V getResult(Future<V> fut) {
        V logCheckRes = null;

        try {
            logCheckRes = fut.get();
        }
        catch (InterruptedException e) {
            logger.info("Future get reported interrupt ", e);

            throw ExceptionUtil.propagateException(e);
        }
        catch (ExecutionException e) {
            e.printStackTrace();

            logger.error("Failed to get future result", e);

            throw ExceptionUtil.propagateException(e);
        }

        return logCheckRes;
    }

    /**
     * @param listBuilds Futures to get builds.
     * @return Stream with builds.
     */
    @Nonnull public static <V> Stream<V> getResults(Collection<Future<V>> listBuilds) {
        return listBuilds.stream().map(FutureUtil::getResult);
    }
}
