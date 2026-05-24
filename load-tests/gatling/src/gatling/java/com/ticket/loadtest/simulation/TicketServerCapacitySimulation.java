package com.ticket.loadtest.simulation;

import com.ticket.loadtest.LoadTestConfig;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class TicketServerCapacitySimulation extends Simulation {

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.baseUrl())
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    public TicketServerCapacitySimulation() {
        final ScenarioBuilder scenario = scenario("ticket-server-capacity")
                .exec(LoadTestConfig.initializeSessionWithNextSeatId())
                .exec(LoadTestConfig.authenticate())
                .exec(LoadTestConfig.withAdmissionToken())
                .exec(fetchSeatStatus())
                .exec(selectSeat())
                .exec(createOrder());

        setUp(scenario.injectOpen(LoadTestConfig.injection()))
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(10.0),
                        details("seat status").successfulRequests().count().gt(0L),
                        details("select seat").successfulRequests().count().gt(0L),
                        details("create order").successfulRequests().count().gt(0L)
                );
    }

    private ChainBuilder fetchSeatStatus() {
        return exec(http("seat status")
                .get("/api/v1/performances/#{performanceId}/seats/status")
                .headers(LoadTestConfig.authAndAdmissionHeaders())
                .check(status().is(200)));
    }

    private ChainBuilder selectSeat() {
        return exec(http("select seat")
                .post("/api/v1/performances/#{performanceId}/seats/#{seatId}/select")
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
                .check(status().is(201)));
    }
}
