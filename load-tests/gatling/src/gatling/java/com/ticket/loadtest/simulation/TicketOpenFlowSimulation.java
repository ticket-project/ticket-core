package com.ticket.loadtest.simulation;

import com.ticket.loadtest.LoadTestConfig;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.doIf;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class TicketOpenFlowSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    public TicketOpenFlowSimulation() {
        final ScenarioBuilder scenario = scenario("ticket-open-flow")
                .exec(LoadTestConfig.initializeSession())
                .exec(LoadTestConfig.authenticate())
                .exec(enterQueue())
                .exec(pollUntilAdmitted())
                .exec(doIf(session -> session.contains("queueToken")).then(
                        fetchSeatStatus(),
                        createHold()
                ));

        setUp(scenario.injectOpen(LoadTestConfig.injection()))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(10.0),
                        details("seat status").successfulRequests().count().gt(0L),
                        details("create hold").successfulRequests().count().gt(0L)
                );
    }

    private ChainBuilder enterQueue() {
        return exec(http("queue enter")
                .post("/api/v1/queue/performances/#{performanceId}/enter")
                .headers(LoadTestConfig.authHeaders())
                .check(status().is(200))
                .check(jsonPath("$.result").is("SUCCESS"))
                .check(jsonPath("$.data.status").saveAs("queueStatus"))
                .check(jsonPath("$.data.queueEntryId").optional().saveAs("queueEntryId"))
                .check(jsonPath("$.data.queueToken").optional().saveAs("queueToken")));
    }

    private ChainBuilder pollUntilAdmitted() {
        return exec(session -> session.set("pollAttempts", 0))
                .asLongAs(session -> !session.contains("queueToken")
                        && session.contains("queueEntryId")
                        && session.getInt("pollAttempts") < LoadTestConfig.statusPolls()
                        && !"EXPIRED".equals(session.getString("queueStatus"))
                        && !"LEFT".equals(session.getString("queueStatus"))
                ).on(
                        exec(http("queue status")
                                .get("/api/v1/queue/performances/#{performanceId}/status")
                                .queryParam("queueEntryId", "#{queueEntryId}")
                                .headers(LoadTestConfig.authHeaders())
                                .check(status().is(200))
                                .check(jsonPath("$.data.status").saveAs("queueStatus"))
                                .check(jsonPath("$.data.queueToken").optional().saveAs("queueToken")))
                                .exec(session -> session.set("pollAttempts", session.getInt("pollAttempts") + 1))
                                .pause(Duration.ofSeconds(LoadTestConfig.statusPollPauseSeconds()))
                );
    }

    private ChainBuilder fetchSeatStatus() {
        return exec(http("seat status")
                .get("/api/v1/performances/#{performanceId}/seats/status")
                .headers(LoadTestConfig.authAndQueueHeaders())
                .check(status().is(200)));
    }

    private ChainBuilder createHold() {
        return exec(http("create hold")
                .post("/api/v1/performances/#{performanceId}/holds")
                .headers(LoadTestConfig.authAndQueueHeaders())
                .body(StringBody("""
                        {
                          "seatIds": #{seatIdsJson}
                        }
                        """))
                .check(status().in(201, 400, 409, 422)));
    }
}
