package org.apache.ignite.ci.web;

/**
 * Created by Дмитрий on 02.11.2017
 */
public interface IBackgroundUpdatable {
    /**
     * Sets flag indicating if update required from HTML interface, new results will be prepared by updater soon
     * @param update update flag
     */
    public void setUpdateRequired(boolean update);
}
