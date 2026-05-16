package com.connecthub.messageservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class MessageServiceApplicationTests {

	@Autowired
	private MessageServiceApplication application;

	@Test
	void contextLoads() {
		assertThat(application).isNotNull();
	}

}
