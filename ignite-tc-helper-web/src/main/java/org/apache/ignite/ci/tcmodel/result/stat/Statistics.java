package org.apache.ignite.ci.tcmodel.result.stat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.tcmodel.conf.bt.Parameters;
import org.jetbrains.annotations.Nullable;

/**
 * Created by Дмитрий on 01.11.2017.
 */
@XmlRootElement(name = "properties")
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistics extends Parameters {

    @Nullable public Long getBuildDuration() {
        String duration = getParameter("BuildDuration");
        if (duration == null)
            return null;
        return Long.parseLong(duration);
    }
}
