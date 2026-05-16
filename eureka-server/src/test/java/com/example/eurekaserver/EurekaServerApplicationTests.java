package com.example.eurekaserver;

import com.connecthub.eurekaserver.EurekaServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EurekaServerApplication.class)
class EurekaServerApplicationTests {

	@Autowired
	private EurekaServerApplication application;

	@Test
	void contextLoads() {
		assertThat(application).isNotNull();
	}

}
