package org.apache.ignite.ci.issue;

public class EventTemplate {
    private final int[] beforeEvent;
    private final int[] eventAndAfter;
    private boolean shouldBeFirst;

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

    public int cntEvents() {
        return beforeEvent.length + eventAndAfter.length;
    }

    public EventTemplate setShouldBeFirst(boolean shouldBeFirst) {
        this.shouldBeFirst = shouldBeFirst;

        return this;
    }

    public boolean shouldBeFirst() {
        return shouldBeFirst;
    }
}
