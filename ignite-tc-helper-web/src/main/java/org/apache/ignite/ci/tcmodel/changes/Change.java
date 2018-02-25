package org.apache.ignite.ci.tcmodel.changes;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.ci.db.Persisted;

/**
 * Created by Дмитрий on 18.02.2018
 */
@XmlRootElement(name = "change")
@Persisted
public class Change extends ChangeRef {

    /** Comment text */
    @XmlElement public String comment;

    /*
     *<change >
     <user username="akuznetsov" name="Alexey Kuznetsov" id="14" href="/app/rest/latest/users/id:14"/>
     <files count="1">
     <file before-revision="f897370f88b12f7dbaffe4978d1dbc26e0a2516e" after-revision="970b01a6675c10a25d5cf591ac3ab4511b2c2a5e" changeType="edited" file="modules/rest-http/src/main/java/org/apache/ignite/internal/processors/rest/protocols/http/jetty/GridJettyRestHandler.java" relative-file="modules/rest-http/src/main/java/org/apache/ignite/internal/processors/rest/protocols/http/jetty/GridJettyRestHandler.java"/>
     </files>
     <vcsRootInstance id="1166" vcs-root-id="IgniteApache" name="ignite-apache" href="/app/rest/latest/vcs-root-instances/id:1166"/>
     </change> */
}
