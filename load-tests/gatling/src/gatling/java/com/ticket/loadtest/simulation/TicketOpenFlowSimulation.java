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
                .exec(doIf(session -> session.contains("admissionToken")).then(
                        fetchSeatStatus(),
                        createOrder()
                ));

        setUp(scenario.injectOpen(LoadTestConfig.injection()))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(10.0),
                        details("seat status").successfulRequests().count().gt(0L),
                        details("create order").successfulRequests().count().gt(0L)
                );
    }

    private ChainBuilder enterQueue() {
        return exec(http("queue enter")
                .post("/api/v1/queue/performances/#{performanceId}/enter")
                .headers(LoadTestConfig.authHeaders())
                .check(status().is(200))
                .check(jsonPath("$.status").saveAs("queueStatus"))
                .check(jsonPath("$.queueSessionId").saveAs("queueSessionId"))
                .check(jsonPath("$.admissionToken").optional().saveAs("admissionToken")));
    }

    private ChainBuilder pollUntilAdmitted() {
        return exec(session -> session.set("pollAttempts", 0))
                .asLongAs(session -> !session.contains("admissionToken")
                        && session.contains("queueSessionId")
                        && session.getInt("pollAttempts") < LoadTestConfig.statusPolls()
                        && !"EXPIRED".equals(session.getString("queueStatus"))
                        && !"LEFT".equals(session.getString("queueStatus"))
                ).on(
                        exec(http("queue status")
                                .get("/api/v1/queue/performances/#{performanceId}/status")
                                .headers(LoadTestConfig.queueSessionHeaders())
                                .check(status().is(200))
                                .check(jsonPath("$.status").saveAs("queueStatus"))
                                .check(jsonPath("$.admissionToken").optional().saveAs("admissionToken")))
                                .exec(session -> session.set("pollAttempts", session.getInt("pollAttempts") + 1))
                                .pause(Duration.ofSeconds(LoadTestConfig.statusPollPauseSeconds()))
                );
    }

    private ChainBuilder fetchSeatStatus() {
        return exec(http("seat status")
                .get("/api/v1/performances/#{performanceId}/seats/status")
                .headers(LoadTestConfig.authAndAdmissionHeaders())
                .check(status().is(200)));
    }

    private ChainBuilder createOrder() {
        return exec(http("create order")
                .post("/api/v1/orders")
                .headers(LoadTestConfig.authAndAdmissionHeaders())
                .body(StringBody("""
                        {
                          "performanceId": #{performanceId},
                          "seatIds": #{seatIdsJson}
                        }
                        """))
                .check(status().in(201, 400, 409, 422)));
    }
}
