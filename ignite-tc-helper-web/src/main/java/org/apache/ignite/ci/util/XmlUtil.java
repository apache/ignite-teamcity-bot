package org.apache.ignite.ci.util;

import java.io.Reader;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 * Created by dpavlov on 27.07.2017
 */
public class XmlUtil {

    /** Cached context to save time on creation ctx each time. */
    private static ConcurrentHashMap<Class, JAXBContext> cachedCtx = new ConcurrentHashMap<>();

    public static <T> T load(Class<T> tCls, Reader reader) throws JAXBException {
        final JAXBContext ctx = cachedCtx.computeIfAbsent(tCls, c -> {
            try {
                return JAXBContext.newInstance(tCls);
            }
            catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        });
        Unmarshaller unmarshaller = ctx.createUnmarshaller();
        T unmarshal = (T)unmarshaller.unmarshal(reader);

        int interned = ObjectInterner.internFields(unmarshal);
       // if (interned > 0)
       //     System.out.println("Strings saved: " + interned);

        return unmarshal;
    }
}
