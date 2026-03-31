package botTelegram.bot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BotKeyboardFactory {

    // Se mantiene este porque es el que usas en enviarMenuPrincipal
    public static InlineKeyboardMarkup crearMenuPrincipalInline(boolean esAdmin) {
        List<List<InlineKeyboardButton>> filas = new ArrayList<>();

        filas.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("📝 Modo práctica")
                        .callbackData("menu_practica")
                        .build()
        ));
        filas.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🎯 Examen Final")
                        .callbackData("menu_examen")
                        .build()
        ));
        // Botón de estadísticas visible para TODOS
        filas.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("📊 Mis Estadísticas")
                        .callbackData("menu_estadisticas")
                        .build()
        ));
        filas.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🛑 Cerrar Bot")
                        .callbackData("menu_cancelar")
                        .build()
        ));

        if (esAdmin) {
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text("📊 Ver estadísticas TOTALES")
                            .callbackData("menu_stats_global")
                            .build()
            ));
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text("📂 Subir preguntas JSON")
                            .callbackData("menu_subir_json")
                            .build()
            ));
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text("➕ Crear pregunta")
                            .callbackData("menu_crear_pregunta")
                            .build()
            ));
        }

        return new InlineKeyboardMarkup(filas);
    }

    public static InlineKeyboardMarkup crearTecladoOpcionesInline(List<String> opciones, String tipo) {
        List<List<InlineKeyboardButton>> filas = new ArrayList<>();

        for (int i = 0; i < opciones.size(); i++) {
            // Ejemplo de callbackData: "res_examen_1" o "res_practica_1"
            String callbackData = "res_" + tipo + "_" + (i + 1);

            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(opciones.get(i))
                            .callbackData(callbackData)
                            .build()
            ));
        }

        // Fila de navegación: [ ⬅️ ] [ ➡️ ] [ 🏁 Fin ]
        List<InlineKeyboardButton> filaControl = new ArrayList<>();
        filaControl.add(InlineKeyboardButton.builder().text("⬅️").callbackData(tipo + "_nav_retro").build());
        filaControl.add(InlineKeyboardButton.builder().text("➡️").callbackData(tipo + "_nav_sig").build());
        filaControl.add(InlineKeyboardButton.builder().text("🏁 Fin").callbackData(tipo + "_nav_fin").build());
        filas.add(filaControl);

        return new InlineKeyboardMarkup(filas);
    }
}