package com.ticket.loadtest.simulation;

import com.ticket.loadtest.LoadTestConfig;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class QueueEnterSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .shareConnections()
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    public QueueEnterSimulation() {
        final ScenarioBuilder scenario = scenario("queue-enter")
                .exec(LoadTestConfig.initializeSession())
                .exec(LoadTestConfig.authenticate())
                .exec(http("queue join")
                        .post(LoadTestConfig.queueBaseUrl() + "/api/v1/queue/performances/#{performanceId}/join")
                        .headers(LoadTestConfig.authHeaders())
                        .check(status().is(200))
                        .check(jsonPath("$.queueToken").saveAs("queueToken")))
                .exec(http("queue enter")
                        .post(LoadTestConfig.queueBaseUrl() + "/api/v1/queue/performances/#{performanceId}/enter")
                        .headers(LoadTestConfig.queueTokenHeaders())
                        .check(status().in(200, 403, 410, 429))
                        .check(jsonPath("$.admissionToken").optional().saveAs("admissionToken")));

        setUp(scenario.injectOpen(LoadTestConfig.injection()))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lt(10.0));
    }
}
