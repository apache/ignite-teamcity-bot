package org.apache.ignite.ci.web;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Component for storing catchable results into ignite and get updates
 */
public class TcUpdatePool {
    private ThreadFactory threadFactory = Executors.defaultThreadFactory();

    private ExecutorService service = Executors.newFixedThreadPool(20, r -> {
        Thread thread = threadFactory.newThread(r);

        thread.setName("tc-upd-" + thread.getName());

        return thread;
    });


    public ExecutorService getService() {
        return service;
    }


    public void stop() {
        service.shutdown();
        try {
            service.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
