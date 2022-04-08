package org.apache.ignite.tcbot.engine.conf;

import javax.annotation.Nullable;

public class SmtpSettings {
    /** Default is gmail server: "smtp.gmail.com". */
    private String host;

    /** Default is 465. */
    private Integer socketFactoryPort;

    /** Default is 465. */
    private Integer port;

    @Nullable public String host() {
        return host;
    }
}
