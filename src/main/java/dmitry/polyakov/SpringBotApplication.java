package dmitry.polyakov;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @author Dmitry Polyakov
 * @created 10.01.2023 23:20
 */
@SpringBootApplication
@EnableJpaRepositories
public class SpringBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBotApplication.class, args);
    }

}
