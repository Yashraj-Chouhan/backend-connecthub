package com.connecthub.gatewayservice;

import com.connecthub.gatewayservice.config.GatewayCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class GatewayServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private GatewayCorsProperties gatewayCorsProperties;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	void corsPropertiesLoadConfiguredOrigins() {
		assertThat(gatewayCorsProperties.getAllowedOriginPatterns())
				.isNotEmpty()
				.contains("http://localhost:[*]");
		assertThat(gatewayCorsProperties.getAllowedMethods())
				.contains("GET", "POST", "OPTIONS");
		assertThat(gatewayCorsProperties.getAllowedHeaders())
				.contains("*");
		assertThat(gatewayCorsProperties.isAllowCredentials()).isTrue();
	}

}
