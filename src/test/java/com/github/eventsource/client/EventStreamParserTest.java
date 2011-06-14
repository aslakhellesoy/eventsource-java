package com.github.eventsource.client;

import com.github.eventsource.client.impl.ConnectionHandler;
import com.github.eventsource.client.impl.EventStreamParser;
import com.github.eventsource.client.stubs.StubHandler;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class EventStreamParserTest {
    private static final String ORIGIN = "http://host.com:99/foo";
    public EventSourceHandler eh;
    public ConnectionHandler ch;
    public EventStreamParser esp;

    @Before
    public void setup() {
        eh = mock(EventSourceHandler.class);
        ch = mock(ConnectionHandler.class);
        esp = new EventStreamParser(ORIGIN, eh, ch);
    }

    @Test
    public void dispatchesSingleLineMessage() throws Exception {
        esp.line("data: hello");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void doesntFireMultipleTimesIfSeveralEmptyLines() throws Exception {
        esp.line("data: hello");
        esp.line("");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
        verifyNoMoreInteractions(eh);
    }

    @Test
    public void dispatchesSingleLineMessageWithId() throws Exception {
        esp.line("data: hello");
        esp.line("id: 1");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", "1", ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithCustomEvent() throws Exception {
        esp.line("data: hello");
        esp.line("event: beeroclock");
        esp.line("");

        verify(eh).onMessage(eq("beeroclock"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void ignoresLinesStartingWithColon() throws Exception {
        esp.line(": ignore this");
        esp.line("data: hello");
        esp.line(": this too");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", null, ORIGIN)));
    }

    @Test
    public void dispatchesSingleLineMessageWithoutColon() throws Exception {
        esp.line("data");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("", null, ORIGIN)));
    }

    @Test
    public void setsRetryTimeToSevenSeconds() throws Exception {
        esp.line("retry: 7000");
        esp.line("");

        verify(ch).setReconnectionTimeMillis(7000);
    }

    @Test
    public void doesntSetRetryTimeUnlessEntireValueIsNumber() throws Exception {
        esp.line("retry: 7000L");
        esp.line("");

        verifyNoMoreInteractions(eh);
    }

    @Test
    public void usesTheEventIdOfPreviousEventIfNoneSet() throws Exception {
        esp.line("data: hello");
        esp.line("id: reused");
        esp.line("");
        esp.line("data: world");
        esp.line("");

        verify(eh).onMessage(eq("message"), eq(new MessageEvent("hello", "reused", ORIGIN)));
        verify(eh).onMessage(eq("message"), eq(new MessageEvent("world", "reused", ORIGIN)));
    }

    @Test
    public void eventStreamDataCanBeEasilyParsedInTests() throws Exception {
        StubHandler stubHandler = new StubHandler();
        EventStreamParser esp = new EventStreamParser(null, stubHandler, stubHandler);
        esp.lines("" +
                "data: hello\n" +
                "data: world\n" +
                "\n" +
                "data: bonjour\n" +
                "data: monde\n" +
                "\n");
        assertEquals(asList(new MessageEvent("hello\nworld"), new MessageEvent("bonjour\nmonde")), stubHandler.getMessageEvents());
    }
}
