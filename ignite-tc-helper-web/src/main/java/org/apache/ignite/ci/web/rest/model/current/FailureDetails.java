package org.apache.ignite.ci.web.rest.model.current;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dpavlov on 25.10.2017
 *
 * Summary failures from all servers
 */
public class FailureDetails extends AbstractTestMetrics {

    public List<ChainCurrentStatus> servers = new ArrayList<>();

}
