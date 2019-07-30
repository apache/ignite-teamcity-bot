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
package org.apache.ignite.tcbot.common.interceptor;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class GuavaCachedInterceptor implements MethodInterceptor {
    private final ConcurrentMap<String, Cache<List, Optional>> caches = new ConcurrentHashMap<>();

    @Override public Object invoke(MethodInvocation invocation) throws Throwable {
        final Method invocationMtd = invocation.getMethod();
        GuavaCached annotation = invocationMtd.getAnnotation(GuavaCached.class);

        Cache<List, Optional> cache = caches.computeIfAbsent(cacheId(invocation), k -> {
            CacheBuilder builder = CacheBuilder.newBuilder();

            if (annotation.softValues())
                builder = builder.softValues();

            if (annotation.maximumSize() > 0)
                builder = builder.maximumSize(annotation.maximumSize());

            if (annotation.expireAfterAccessSecs() > 0)
                builder.expireAfterAccess(annotation.expireAfterAccessSecs(), TimeUnit.SECONDS);

            if (annotation.expireAfterWriteSecs() > 0)
                builder.expireAfterWrite(annotation.expireAfterWriteSecs(), TimeUnit.SECONDS);

            return builder.build();
        });

        List<Object> cacheKey = Arrays.asList(invocation.getArguments());

        Optional optional = cache.get(cacheKey,
            new Callable<Optional>() {
                @Override public Optional call() throws Exception {
                    Object res;
                    try {
                        res = invocation.proceed();
                    }
                    catch (Throwable throwable) {
                        Throwables.propagateIfPossible(throwable, Exception.class);

                        throw new RuntimeException(throwable);
                    }
                    return Optional.ofNullable(res);
                }
            });

        if (!annotation.cacheNullRval()) {
            if (!optional.isPresent())
                cache.invalidate(cacheKey);
        }

        if (!annotation.cacheNegativeNumbersRval()) {
            if (optional.isPresent()) {
                Object o = optional.get();
                Preconditions.checkState(o instanceof Number, "Invalid return value of method: " + cacheKey);

                Number num = (Number)o;
                if (num.longValue() < 0)
                    cache.invalidate(cacheKey);
            }
        }

        return optional.orElse(null);
    }

    @Nonnull
    private String cacheId(MethodInvocation invocation) {
        final Method invocationMtd = invocation.getMethod();
        final String cls = invocationMtd.getDeclaringClass().getName();
        final String mtd = invocationMtd.getName();

        return cls + "." + mtd;
    }
}
