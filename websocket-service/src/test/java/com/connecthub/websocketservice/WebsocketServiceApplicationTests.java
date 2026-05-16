package com.connecthub.websocketservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class WebSocketServiceApplicationTests {

	@Autowired
	private WebsocketServiceApplication application;

	@Test
	void contextLoads() {
		assertThat(application).isNotNull();
	}

}
