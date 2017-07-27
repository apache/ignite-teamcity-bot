package org.apache.ignite.ci.util;

import java.io.StringReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * Created by dpavlov on 27.07.2017
 */
public class XmlUtil {
    public static <T> T load(String xml, Class<T> tCls) throws JAXBException {
        JAXBContext jc = JAXBContext.newInstance(tCls);

        Unmarshaller unmarshaller = jc.createUnmarshaller();
        T fosterHome = (T) unmarshaller.unmarshal(new StringReader(xml));

        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(fosterHome, System.out);
        return fosterHome;
    }
}
