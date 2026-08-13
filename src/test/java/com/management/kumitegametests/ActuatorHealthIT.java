package com.management.kumitegametests;

import com.management.kumitegame.KumiteGameStarter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = KumiteGameStarter.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ActuatorHealthIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    private final TestRestTemplate rest;

    @Autowired
    public ActuatorHealthIT(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    @Order(1)
    void healthIsUpWhenMongoIsReachable() {
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).contains("\"status\":\"UP\"");
                    assertThat(response.getBody()).contains("\"mongo\"");
                });
    }

    @Test
    @Order(2)
    void healthIsDownWhenMongoIsUnreachable() {
        mongo.stop();

        await().atMost(Duration.ofSeconds(40))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
                });
    }
}
