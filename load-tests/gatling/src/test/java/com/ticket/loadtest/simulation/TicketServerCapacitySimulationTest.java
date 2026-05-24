package com.ticket.loadtest.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketServerCapacitySimulationTest {

    private static final Path SIMULATION_SOURCE = Path.of(
            "src/gatling/java/com/ticket/loadtest/simulation/TicketServerCapacitySimulation.java"
    );

    @Test
    void countsOnlySuccessfulSeatSelectionAndOrderRequests() throws IOException {
        String source = Files.readString(SIMULATION_SOURCE, StandardCharsets.UTF_8);

        String selectSeat = methodBody(source, "selectSeat");
        assertTrue(selectSeat.contains(".check(status().is(200))"));
        assertFalse(selectSeat.contains("status().in("));

        String createOrder = methodBody(source, "createOrder");
        assertTrue(createOrder.contains(".check(status().is(201))"));
        assertFalse(createOrder.contains("status().in("));
    }

    private static String methodBody(final String source, final String methodName) {
        Pattern pattern = Pattern.compile(
                "private ChainBuilder " + methodName + "\\(\\) \\{(.*?)\\n    \\}",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(source);
        assertTrue(matcher.find(), methodName + " method should exist");
        return matcher.group(1);
    }
}
