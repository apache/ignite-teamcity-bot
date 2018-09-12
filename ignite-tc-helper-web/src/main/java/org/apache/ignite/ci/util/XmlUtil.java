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

    /**
     * @param t Text to process.
     */
    public static String xmlEscapeText(CharSequence t) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);

            switch(c){
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '\"': sb.append("&quot;"); break;
                case '&': sb.append("&amp;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c>0x7e)
                        sb.append("&#").append((int)c).append(";");
                    else
                        sb.append(c);
            }
        }

        return sb.toString();
    }
}
