package com.github.eventsource.client;

import com.github.eventsource.client.impl.ConnectionHandler;
import com.github.eventsource.client.impl.EventStreamParser;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class EventStreamParserTest {
    private static final String ORIGIN = "http://host.com:99/foo";
    public EventSourceHandler eh;
    public ConnectionHandler ch;
    public EventStreamParser md;

    @Before
    public void setup() {
        eh = mock(EventSourceHandler.class);
        ch = mock(ConnectionHandler.class);
        md = new EventStreamParser(eh, ORIGIN, ch);
    }

    @Test
    public void dispatchesSingleLineMessage() throws Exception {
        md.line("data: hello");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void doesntFireMultipleTimesIfSeveralEmptyLines() throws Exception {
        md.line("data: hello");
        md.line("");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
        verifyNoMoreInteractions(eh);
    }

    @Test
    public void dispatchesSingleLineMessageWithId() throws Exception {
        md.line("data: hello");
        md.line("id: 1");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", "1", ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithCustomEvent() throws Exception {
        md.line("data: hello");
        md.line("event: beeroclock");
        md.line("");

        verify(eh).onMessage(eq("beeroclock"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void ignoresLinesStartingWithColon() throws Exception {
        md.line(": ignore this");
        md.line("data: hello");
        md.line(": this too");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithoutColon() throws Exception {
        md.line("data");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("", null, ORIGIN)));
    }

    @Test
    public void setsRetryTimeToSevenSeconds() throws Exception {
        md.line("retry: 7000");
        md.line("");

        verify(ch).setReconnectionTime(7000);
    }

    @Test
    public void doesntSetRetryTimeUnlessEntireValueIsNumber() throws Exception {
        md.line("retry: 7000L");
        md.line("");

        verifyNoMoreInteractions(eh);
    }

    @Test
    public void usesTheEventIdOfPreviousEventIfNoneSet() throws Exception {
        md.line("data: hello");
        md.line("id: reused");
        md.line("");
        md.line("data: world");
        md.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", "reused", ORIGIN)));
        verify(eh).onMessage(eq("message"), eq(new MessageEvent("world", "reused", ORIGIN)));
    }
}
