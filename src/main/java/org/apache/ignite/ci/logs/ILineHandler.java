package org.apache.ignite.ci.logs;

import java.io.File;

/**
 * Created by dpavlov on 24.07.2017.
 */
public interface ILineHandler extends AutoCloseable {
    public void accept(String line, File file);
}
