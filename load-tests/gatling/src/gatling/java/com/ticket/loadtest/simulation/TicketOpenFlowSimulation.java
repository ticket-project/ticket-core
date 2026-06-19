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
            .shareConnections()
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    public TicketOpenFlowSimulation() {
        final ScenarioBuilder scenario = scenario("ticket-open-flow")
                .exec(LoadTestConfig.initializeSession())
                .exec(LoadTestConfig.authenticate())
                .exec(joinQueue())
                .exec(pollUntilAdmitted())
                .exec(enterQueue())
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

    private ChainBuilder joinQueue() {
        return exec(http("queue join")
                .post(LoadTestConfig.queueBaseUrl() + "/api/v1/queue/performances/#{performanceId}/join")
                .headers(LoadTestConfig.authHeaders())
                .check(status().is(200))
                .check(jsonPath("$.seq").ofLong().saveAs("queueSeq"))
                .check(jsonPath("$.queueToken").saveAs("queueToken")));
    }

    private ChainBuilder pollUntilAdmitted() {
        return exec(session -> session.set("pollAttempts", 0))
                .asLongAs(session -> !session.contains("admissionReady")
                        && session.getInt("pollAttempts") < LoadTestConfig.statusPolls()
                ).on(
                        exec(http("queue state")
                                .get(LoadTestConfig.queueBaseUrl() + "/api/v1/queue/performances/#{performanceId}/state")
                                .check(status().is(200))
                                .check(jsonPath("$.admittedUntilSeq").ofLong().saveAs("admittedUntilSeq")))
                                .exec(session -> {
                                    final long admittedUntilSeq = session.getLong("admittedUntilSeq");
                                    final long queueSeq = session.getLong("queueSeq");
                                    if (admittedUntilSeq >= queueSeq) {
                                        return session.set("admissionReady", true);
                                    }
                                    return session.set("pollAttempts", session.getInt("pollAttempts") + 1);
                                })
                                .pause(Duration.ofSeconds(LoadTestConfig.statusPollPauseSeconds()))
                );
    }

    private ChainBuilder enterQueue() {
        return doIf(session -> session.contains("admissionReady")).then(
                exec(http("queue enter")
                        .post(LoadTestConfig.queueBaseUrl() + "/api/v1/queue/performances/#{performanceId}/enter")
                        .headers(LoadTestConfig.queueTokenHeaders())
                        .check(status().is(200))
                        .check(jsonPath("$.admissionToken").saveAs("admissionToken")))
        );
    }

    private ChainBuilder fetchSeatStatus() {
        return exec(http("seat status")
                .get(LoadTestConfig.coreBaseUrl() + "/api/v1/performances/#{performanceId}/seats/status")
                .headers(LoadTestConfig.authAndAdmissionHeaders())
                .check(status().is(200)));
    }

    private ChainBuilder createOrder() {
        return exec(http("create order")
                .post(LoadTestConfig.coreBaseUrl() + "/api/v1/orders")
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
