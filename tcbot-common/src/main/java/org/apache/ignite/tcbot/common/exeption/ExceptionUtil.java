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

package org.apache.ignite.tcbot.common.exeption;

import com.google.common.base.Throwables;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class ExceptionUtil {
    /**
     * @param e Exception.
     */
    public static RuntimeException propagateException(Exception e) {
        if (e instanceof InterruptedException)
            Thread.currentThread().interrupt();

        if(e instanceof ExecutionException) {
            ExecutionException executionException = (ExecutionException) e;
            Throwable cause = executionException.getCause();

            Throwables.throwIfUnchecked(cause);
        }

        throwIfRest(e);

        Throwables.throwIfUnchecked(e);

        throw new RuntimeException(e);
    }

    public static void throwIfRest(Exception e) {
        final Optional<Throwable> any = Throwables.getCausalChain(e)
                .stream()
                .filter(th ->
                        th instanceof ServiceUnauthorizedException
                                || th instanceof ServicesStartingException)
                .findAny();

        final RuntimeException eRest = (RuntimeException) any.orElse(null);
        if (eRest != null)
            throw eRest;
    }

}
