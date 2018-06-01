package org.apache.ignite.ci.web;

import org.apache.ignite.Ignition;

public class LauncherIgniteClientMode {
    public static void main(String[] args) throws Exception {
        Ignition.setClientMode(true);

        Launcher.runServer(true);
    }
}
