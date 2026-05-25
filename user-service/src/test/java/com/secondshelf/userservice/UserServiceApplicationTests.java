package com.secondshelf.userservice;

import com.secondshelf.userservice.config.AdminSeeder;
import com.secondshelf.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class UserServiceApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private AdminSeeder adminSeeder;

	@Test
	void contextLoads() {
	}

}
