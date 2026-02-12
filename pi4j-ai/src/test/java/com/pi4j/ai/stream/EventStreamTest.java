package com.pi4j.ai.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class EventStreamTest {

    @Test
    void subscribeAndResultWorks() throws Exception {
        EventStream<String, String> stream = new EventStream<String, String>();
        List<String> received = new ArrayList<String>();
        stream.subscribe(received::add);

        stream.push("a");
        stream.push("b");
        stream.end("done");

        assertEquals(2, received.size());
        assertEquals("a", received.get(0));
        assertEquals("b", received.get(1));
        assertEquals("done", stream.result().get());
    }

    @Test
    void errorCompletesExceptionally() {
        EventStream<String, String> stream = new EventStream<String, String>();
        stream.error(new IllegalStateException("boom"));

        ExecutionException exception = assertThrows(ExecutionException.class, () -> stream.result().get());
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }
}
