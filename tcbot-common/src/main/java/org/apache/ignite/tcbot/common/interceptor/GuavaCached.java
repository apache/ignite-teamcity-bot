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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Note: </b> this caching annotation ignores object called, may be used only for singleton-scope classes.
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface GuavaCached {
    /**
     * Specifies the maximum number of entries the cache may contain.
     */
    long maximumSize() default -1L;

    /**
     *
     */
    boolean softValues() default false;

    /**
     * Cache null as valid return value. For caching Ignite entries it is always require to set this parameter.
     */
    boolean cacheNullRval() default true;

    /**
     * Cache negative number values as valid return value. For caching Ignite entries it is always require to set this
     * parameter.
     */
    boolean cacheNegativeNumbersRval() default true;

    long expireAfterAccessSecs() default -1;

    long expireAfterWriteSecs() default -1;
}
