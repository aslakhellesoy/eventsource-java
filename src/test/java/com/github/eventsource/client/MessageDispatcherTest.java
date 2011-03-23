package com.github.eventsource.client;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class MessageDispatcherTest {
    private static final String ORIGIN = "http://host.com:99/foo";
    public com.github.eventsource.client.MessageEmitter h;
    public com.github.eventsource.client.MessageDispatcher md;

    @Before
    public void setup() {
        h = mock(com.github.eventsource.client.MessageEmitter.class);
        md = new com.github.eventsource.client.MessageDispatcher(h, ORIGIN);
    }

    @Test
    public void dispatchesSingleLineMessage() throws Exception {
        md.line("data: hello");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void doesntFireMultipleTimesIfSeveralEmptyLines() throws Exception {
        md.line("data: hello");
        md.line("");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("hello", null, ORIGIN)));
        verifyNoMoreInteractions(h);
    }

    @Test
    public void dispatchesSingleLineMessageWithId() throws Exception {
        md.line("data: hello");
        md.line("id: 1");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("hello", "1", ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithCustomEvent() throws Exception {
        md.line("data: hello");
        md.line("event: beeroclock");
        md.line("");

        verify(h).emitMessage(eq("beeroclock"), eq(new com.github.eventsource.client.MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void ignoresLinesStartingWithColon() throws Exception {
        md.line(": ignore this");
        md.line("data: hello");
        md.line(": this too");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithoutColon() throws Exception {
        md.line("data");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("", null, ORIGIN)));
    }

    @Test
    public void setsRetryTimeToSevenSeconds() throws Exception {
        md.line("retry: 7000");
        md.line("");

        verify(h).setReconnectionTime(7000);
    }

    @Test
    public void doesntSetRetryTimeUnlessEntireValueIsNumber() throws Exception {
        md.line("retry: 7000L");
        md.line("");

        verifyNoMoreInteractions(h);
    }

    @Test
    public void usesTheEventIdOfPreviousEventIfNoneSet() throws Exception {
        md.line("data: hello");
        md.line("id: reused");
        md.line("");
        md.line("data: world");
        md.line("");

        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("hello", "reused", ORIGIN)));
        verify(h).emitMessage(eq("message"), eq(new com.github.eventsource.client.MessageEvent("world", "reused", ORIGIN)));
    }
}
