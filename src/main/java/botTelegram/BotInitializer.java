
package botTelegram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import botTelegram.bot.DrivingBot;
import botTelegram.repository.ResultadoExamenRepository;
import botTelegram.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {
    private static final Logger logger = LoggerFactory.getLogger(BotInitializer.class);

    private final DrivingBot drivingBot;
    private final UsuarioRepository usuarioRepo;
    private final ResultadoExamenRepository resultadoRepo;

    public BotInitializer(DrivingBot drivingBot, UsuarioRepository usuarioRepo,
                          ResultadoExamenRepository resultadoRepo) {
        this.drivingBot = drivingBot;
        this.usuarioRepo = usuarioRepo;
        this.resultadoRepo = resultadoRepo;
    }


    @PostConstruct
    public void init() {
        repararFlagExamenGratis();
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(drivingBot);
            logger.info("Bot registrado correctamente con Timeouts extendidos (75s)...");
        } catch (Exception e) {
            logger.error("Error al registrar el bot: {}", e.getMessage(), e);
        }
    }

    /**
     * Reparación de datos en arranque: marca examen_gratis_usado=true en todos los
     * usuarios no-premium que ya tienen al menos un examen registrado en RESULTADO_EXAMEN
     * pero cuyo flag quedó a NULL (p.ej. por haber sido creados antes de que se añadiera
     * la columna o antes de que el fix estuviera activo).
     */
    private void repararFlagExamenGratis() {
        usuarioRepo.findAll().forEach(u -> {
            if (!u.isPremium() && !u.isAdmin() && !u.isExamenGratisUsado()) {
                boolean tieneExamenes = !resultadoRepo.findByUsuarioId(u.getTelegramId()).isEmpty();
                if (tieneExamenes) {
                    u.setExamenGratisUsado(true);
                    usuarioRepo.save(u);
                    logger.info("Flag examen_gratis_usado reparado para usuario {}", u.getTelegramId());
                }
            }
        });
    }
}

