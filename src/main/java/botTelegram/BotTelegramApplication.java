package botTelegram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.bots.DefaultBotOptions;

@SpringBootApplication
public class BotTelegramApplication {

	public static void main(String[] args) {
		SpringApplication.run(BotTelegramApplication.class, args);
	}

	@Bean
	public DefaultBotOptions defaultBotOptions() {

		return new DefaultBotOptions();
	}

}


