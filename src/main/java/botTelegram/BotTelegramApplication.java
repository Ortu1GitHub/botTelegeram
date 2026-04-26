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
		DefaultBotOptions options = new DefaultBotOptions();

		// En la versión 6.x, los timeouts se gestionan así:
		options.setMaxThreads(10); // Ayuda con el "Thread starvation"

		// Telegram por defecto tiene un timeout de 75s para long polling.
		// Establecemos el tiempo de espera del socket un poco por encima.
		options.setProxyType(DefaultBotOptions.ProxyType.NO_PROXY);

		return options;
	}

}


