package com.ticket.loadtest.simulation;

import com.ticket.loadtest.LoadTestConfig;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class HoldRaceSimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    public HoldRaceSimulation() {
        final ScenarioBuilder scenario = scenario("hold-race")
                .exec(LoadTestConfig.initializeSession())
                .exec(LoadTestConfig.authenticate())
                .exec(LoadTestConfig.withConfiguredQueueToken())
                .exec(http("create hold")
                        .post("/api/v1/performances/#{performanceId}/holds")
                        .headers(LoadTestConfig.authAndQueueHeaders())
                        .body(StringBody("""
                                {
                                  "seatIds": #{seatIdsJson}
                                }
                                """))
                        .check(status().in(201, 400, 409, 422)));

        setUp(scenario.injectOpen(LoadTestConfig.injection()))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lt(10.0));
    }
}
