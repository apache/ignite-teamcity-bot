package org.apache.ignite.ci.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectInterner {
    private static final LoadingCache<String, String> stringCache
        = CacheBuilder
        .<String, String>newBuilder()
        .maximumSize(67537)
        .initialCapacity(67537)
        .build(
            new CacheLoader<String, String>() {
                @Override public String load(String key) throws Exception {
                    return key;
                }
            }
        );

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
