package dmitry.polyakov.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Dmitry Polyakov
 * @created 10.01.2023 23:26
 */
@Configuration
@ComponentScan("dmitry.polyakov")
@EntityScan("dmitry.polyakov")
@EnableJpaRepositories(basePackages ={"dmitry.polyakov.model"})
@EnableScheduling
@Data
@PropertySource("classpath:/application.properties")
public class BotConfig {

    @Value("${bot.name}")
    String botName;

    @Value("${bot.token}")
    String token;
}
