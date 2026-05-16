package com.connecthub.presenceservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false",
		"spring.data.redis.repositories.enabled=false"
})
class PresenceServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
