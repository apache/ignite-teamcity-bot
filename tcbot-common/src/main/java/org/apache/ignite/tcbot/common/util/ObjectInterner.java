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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.ignite.tcbot.common.exeption.ExceptionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class with util method for custom strings deduplication (intern analogue).
 */
public class ObjectInterner {
    /** String cache. */
    private static final LoadingCache<String, String> stringCache
        = CacheBuilder
        .<String, String>newBuilder()
        .maximumSize(67537)
        .initialCapacity(67537)
        .build(
            new CacheLoader<String, String>() {
                @Override public String load(String key) {
                    return key;
                }
            }
        );

    /**
     * @param str String.
     */
    public static String internString(String str) {
        if (str == null)
            return null;

        if (str.length() > 300)
            return str;

        try {
            return stringCache.get(str);
        }
        catch (ExecutionException e) {
            throw ExceptionUtil.propagateException(e);
        }
    }

    public static int internFields(Object obj) {
        if (obj == null)
            return 0;

        Field[] fields = obj.getClass().getDeclaredFields();
        AtomicInteger compressed = new AtomicInteger();

        for (Field field : fields) {
            if(Modifier.isStatic(field.getModifiers()))
                continue;

            field.setAccessible(true);


            try {
                Object fldVal = field.get(obj);
                if (fldVal == null)
                    continue;

                if(!Modifier.isFinal(field.getModifiers())) {
                    if (fldVal instanceof String) {
                        String exist = (String)fldVal;
                        String intern = internString(exist);

                        //noinspection StringEquality
                        if (intern != exist) {
                            compressed.incrementAndGet();

                            field.set(obj, intern);
                        }

                        continue;
                    }
                }

                if (fldVal.getClass().getPackage().getName().startsWith("org.apache.ignite.ci"))
                    compressed.addAndGet(internFields(fldVal));
                else if (fldVal instanceof Collection) {
                    Collection collection = (Collection)fldVal;

                    for (Object next : collection) {
                        if (next.getClass().getPackage().getName().startsWith("org.apache.ignite.ci"))
                            compressed.addAndGet(internFields(next));
                    }
                }
                else if(fldVal instanceof Map) {
                    Map map = (Map)fldVal;

                    Set<Map.Entry> set = map.entrySet();

                    for (Map.Entry  nextEntry : set) {
                        Object val = nextEntry.getValue();

                        if (val.getClass().getPackage().getName().startsWith("org.apache.ignite.ci"))
                            compressed.addAndGet(internFields(val));
                    }
                }
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return compressed.get();
    }
}
