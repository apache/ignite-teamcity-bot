package org.apache.ignite.ci.issue;

public class EventTemplate {
    private final int[] beforeEvent;
    private final int[] eventAndAfter;

    public EventTemplate(int[] beforeEvent, int[] eventAndAfter) {
        this.beforeEvent = beforeEvent;
        this.eventAndAfter = eventAndAfter;
    }

    public int[] beforeEvent() {
        return beforeEvent;
    }

    public int[] eventAndAfter() {
        return eventAndAfter;
    }
}
