package org.apache.ignite.ci.tcmodel.conf.bt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Collection of parameters in build
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class Parameters {
    @XmlElement(name="property")
    List<Property> properties;

    public String getParameter(String key) {
        if(properties==null) {
            return null;
        }
        final Optional<Property> any = properties.stream().filter(property ->
            Objects.equals(property.name, key)).findAny();
        return any.map(Property::getValue).orElse(null);
    }
}
