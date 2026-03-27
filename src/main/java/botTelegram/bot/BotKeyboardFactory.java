package botTelegram.bot;

import botTelegram.model.Pregunta;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
        filas.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🛑 Cerrar Bot")
                        .callbackData("menu_cancelar")
                        .build()
        ));

        if (esAdmin) {
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text("📊 Ver estadísticas")
                            .callbackData("menu_estadisticas")
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

    // Se mantiene este porque es el que usas en enviarPreguntaExamen
    public static ReplyKeyboardMarkup crearTecladoExamen(int numOpciones) {
        ReplyKeyboardMarkup teclado = new ReplyKeyboardMarkup();
        teclado.setResizeKeyboard(true);
        teclado.setOneTimeKeyboard(false);

        List<KeyboardRow> filas = new ArrayList<>();

        KeyboardRow filaOpciones = new KeyboardRow();
        for (int i = 1; i <= numOpciones; i++) {
            filaOpciones.add(String.valueOf(i));
        }
        filas.add(filaOpciones);

        KeyboardRow filaControles = new KeyboardRow();
        filaControles.add("Retr");
        filaControles.add("Sig");
        filaControles.add("Fin");
        filas.add(filaControles);

        teclado.setKeyboard(filas);
        return teclado;
    }
}