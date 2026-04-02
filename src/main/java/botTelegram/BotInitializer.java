
package botTelegram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import botTelegram.bot.DrivingBot;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {
    private static final Logger logger = LoggerFactory.getLogger(BotInitializer.class);

    private final DrivingBot drivingBot;

    public BotInitializer(DrivingBot drivingBot) {
        this.drivingBot = drivingBot;
    }


    @PostConstruct
    public void init() {
        // Eliminar en PRO
        logger.debug("PRUEBA: Si ves esto, el modo DEBUG está activo");

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(drivingBot);
            logger.info("Bot registrado correctamente con Timeouts extendidos (75s)...");
        } catch (Exception e) {
            logger.error("Error al registrar el bot: {}", e.getMessage(), e);
        }
    }
}
