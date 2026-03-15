package botTelegram.bot;

import botTelegram.model.EstadoCreacionPregunta;
import botTelegram.model.Examen;
import botTelegram.model.Pregunta;
import botTelegram.model.Usuario;
import botTelegram.repository.UsuarioRepository;
import botTelegram.service.EstadisticaService;
import botTelegram.service.PreguntaServiceImple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DrivingBot extends TelegramLongPollingBot {

    private final PreguntaServiceImple preguntaService;
    private final EstadisticaService estadisticaService;
    private final UsuarioRepository usuarioRepo;
    private final Map<Long, EstadoCreacionPregunta> asistentes = new ConcurrentHashMap<>();


    private final Map<Long, Examen> examenesActivos = new ConcurrentHashMap<>();
    private final Map<Long, Examen> practicasActivas = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersExamen = new ConcurrentHashMap<>();

    private static final int DURACION_MINUTOS_EXAMEN = 40;
    private static final int CANTIDAD_PREGUNTAS_EXAMEN = 30;
    private static final int UMBRAL_APROBADO = 26;
    private static final String RUTA_JSON = "C:\\botTelegram (1)\\botTelegram\\botTelegram\\src\\main\\java\\botTelegram\\recursos";

    // Literales para evitar duplicados
    private static final String SOLO_ADMIN = "❌ Solo los administradores pueden usar esta opción.";
    private static final String SOLO_JSON = "⚠️ Solo se permiten archivos con extensión .json";
    private static final String ARCHIVO_JSON_OK = "✅ Archivo JSON recibido y reemplazado con éxito.";
    private static final String ERROR_JSON = "❌ Error al procesar el archivo JSON.";

    @Value("${telegram.bot.username}")
    private String botUsername;
    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${telegram.admin.id:}")
    private String adminIdConfigured;


    @SuppressWarnings("deprecation")
    public DrivingBot(PreguntaServiceImple preguntaService,
                      EstadisticaService estadisticaService,
                      UsuarioRepository usuarioRepo) {
        this.preguntaService = preguntaService;
        this.estadisticaService = estadisticaService;
        this.usuarioRepo = usuarioRepo;
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            manejarCallback(update);
            return;
        }
        if (!update.hasMessage()) return;
        manejarMensaje(update.getMessage());
    }

    private void manejarCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        @SuppressWarnings("null")
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = u != null && Boolean.TRUE.equals(u.isAdmin());
        final String EXAMEN_CATEGORIA_PREFIX = "examen_categoria_";
        if (data.startsWith(EXAMEN_CATEGORIA_PREFIX)) {
            String categoria = data.substring(EXAMEN_CATEGORIA_PREFIX.length());
            iniciarExamenFinal(chatId, categoria);
            return;
        }
        switch (data) {
            case "menu_cancelar":
                enviarMensaje(chatId, "Has cerrado el menú principal. Escribe /start para volver a verlo.");
                break;
            case "menu_practica":
                iniciarPractica(chatId);
                break;
            case "menu_examen":
                mostrarCategoriasExamen(chatId);
                break;
            case "menu_estadisticas":
                if (esAdmin) mostrarEstadisticas(chatId);
                else enviarMensaje(chatId, SOLO_ADMIN);
                break;
            case "menu_subir_json":
                if (esAdmin)
                    enviarMensaje(chatId, "📂 Envía ahora el archivo JSON de preguntas.");
                else
                    enviarMensaje(chatId, SOLO_ADMIN);
                break;
            case "menu_crear_pregunta":
                if (esAdmin) {
                    asistentes.put(chatId, new EstadoCreacionPregunta());
                    enviarMensaje(chatId, "Indica la categoría de la pregunta (ejemplo: camion,coche,moto):");
                } else {
                    enviarMensaje(chatId, SOLO_ADMIN);
                }
                break;
            default:
                enviarMensaje(chatId, "Opción no reconocida.");
                break;
        }
    }

    private void manejarMensaje(Message message) {
        long chatId = message.getChatId();
        // Crear usuario si no existe
        @SuppressWarnings("null")
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        if (u == null) {
            String nombre = message.getFrom() != null ? message.getFrom().getFirstName() : "Usuario";
            u = new Usuario(String.valueOf(chatId), nombre, false);
            usuarioRepo.save(u);
        }
        if (!message.hasText() && !message.hasDocument()) return;
        String texto = message.hasText() ? message.getText().trim() : "";
        if (message.hasDocument()) {
            manejarArchivo(chatId, message.getDocument());
            return;
        }
        if (practicasActivas.containsKey(chatId)) {
            procesarRespuestaPractica(chatId, texto);
            return;
        }
        if (examenesActivos.containsKey(chatId)) {
            procesarRespuestaExamen(chatId, texto);
            return;
        }
        if (asistentes.containsKey(chatId)) {
            manejarAsistente(chatId, texto);
            return;
        }
        if (texto.equalsIgnoreCase("/start") || texto.equalsIgnoreCase("menu")) {
            enviarMenuPrincipal(chatId);
            return;
        }
        enviarMenuPrincipal(chatId);
    }

    private void manejarArchivo(long chatId, Document document) {
        if (document.getFileName().endsWith(".json")) {
            try {
                GetFile getFile = new GetFile(document.getFileId());
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
                String fileUrl = file.getFileUrl(getBotToken());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fileUrl))
                        .build();
                try (HttpClient client = HttpClient.newHttpClient();
                     InputStream inputStream = client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body();
                     FileOutputStream outputStream = new FileOutputStream(RUTA_JSON)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                enviarMensaje(chatId, ARCHIVO_JSON_OK);
                preguntaService.cargarPreguntas();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                enviarMensaje(chatId, ERROR_JSON);
            } catch (Exception ex) {
                enviarMensaje(chatId, ERROR_JSON);
            }
        } else {
            enviarMensaje(chatId, SOLO_JSON);
        }
    }

    private void manejarAsistente(long chatId, String mensaje) {
        EstadoCreacionPregunta estado = asistentes.get(chatId);
        String msg = mensaje.trim();
        if (msg.equalsIgnoreCase("salir")) {
            asistentes.remove(chatId);
            enviarMensaje(chatId, "✅ Asistente cancelado.");
            return;
        }
        if (estado == null) return;
        switch (estado.paso) {
            case 0:
                manejarPasoCategorias(chatId, estado, msg);
                break;
            case 1:
                manejarPasoTexto(chatId, estado, msg);
                break;
            case 2:
                manejarPasoOpciones(chatId, estado, msg);
                break;
            case 3:
                manejarPasoCorrecta(chatId, estado, msg);
                break;
            default:
                enviarMensaje(chatId, "Paso no reconocido en el asistente.");
                break;
        }
    }

    private void manejarPasoCategorias(long chatId, EstadoCreacionPregunta estado, String msg) {
        List<String> categorias = new ArrayList<>();
        String[] cats = msg.split(",");
        for (String cat : cats) {
            cat = cat.trim();
            if (!cat.isEmpty()) categorias.add(cat);
        }
        if (categorias.isEmpty()) {
            enviarMensaje(chatId, "Ingresa al menos una categoría válida o escribe 'salir'.");
            return;
        }
        estado.setCategorias(categorias);
        estado.setPaso(estado.getPaso() + 1);
        enviarMensaje(chatId, "Ingresa el texto de la pregunta:");
    }

    private void manejarPasoTexto(long chatId, EstadoCreacionPregunta estado, String msg) {
        estado.setTexto(msg);
        estado.setPaso(estado.getPaso() + 1);
        enviarMensaje(chatId, "Ingresa la opción 1:");
    }

    private void manejarPasoOpciones(long chatId, EstadoCreacionPregunta estado, String msg) {
        List<String> opciones = estado.getOpciones();
        opciones.add(msg);
        if (opciones.size() < 3) {
            enviarMensaje(chatId, "Ingresa la opción " + (opciones.size() + 1) + ":");
        } else {
            estado.setPaso(estado.getPaso() + 1);
            enviarMensaje(chatId, "Ingresa el índice (1-3) de la opción correcta:");
        }
    }

    private void manejarPasoCorrecta(long chatId, EstadoCreacionPregunta estado, String msg) {
        try {
            int idx = Integer.parseInt(msg) - 1;
            if (idx < 0 || idx > 2) throw new NumberFormatException();
            estado.setCorrecta(idx);
            Pregunta p = new Pregunta(estado.getTexto(), estado.getOpciones(), estado.getCorrecta());
            for (String cat : estado.getCategorias()) {
                preguntaService.agregarPregunta(cat, p);
            }
            asistentes.remove(chatId);
            enviarMensaje(chatId, "✅ Pregunta agregada y guardada en JSON.");
        } catch (NumberFormatException e) {
            enviarMensaje(chatId, "Ingresa un índice válido (1, 2 o 3) o escribe 'salir'.");
        }
    }

    private void enviarMenuPrincipal(long chatId) {
        @SuppressWarnings("null")
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = u != null && Boolean.TRUE.equals(u.isAdmin());

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText("👋 Bienvenido. Elige una opción:");

        List<List<InlineKeyboardButton>> filas = new ArrayList<>();

        // Botones base
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
                        .text("🛑 Cerrar Menú")
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

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(filas);
        mensaje.setReplyMarkup(markup);

        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    private void iniciarPractica(long chatId) {
        List<Pregunta> preguntas = preguntaService.getTodasLasPreguntas();
        if (preguntas.isEmpty()) {
            enviarMensaje(chatId, "No hay preguntas disponibles para practicar.");
            return;
        }

        Collections.shuffle(preguntas);
        Examen practica = new Examen(preguntas);
        practicasActivas.put(chatId, practica);

        enviarPreguntaPractica(chatId, practica);
    }


    private void enviarPreguntaPractica(long chatId, Examen practica) {
        Pregunta p = practica.getPreguntaActual();
        if (p == null) {
            enviarMensaje(chatId, "✅ Fin de la práctica.\nAciertos: " + practica.getAciertos() + ", Errores: " + practica.getErrores());
            practicasActivas.remove(chatId);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🧩 Pregunta práctica ").append(practica.getIndiceActual() + 1)
                .append("/").append(practica.totalPreguntas()).append("\n\n");
        sb.append(p.getTexto()).append("\n");
        for (int i = 0; i < p.getOpciones().size(); i++) {
            sb.append(i + 1).append(". ").append(p.getOpciones().get(i)).append("\n");
        }
        sb.append("\n👉 Para *terminar la práctica*, escribe o pulsa: 'Fin'");

        SendMessage mensaje = new SendMessage(String.valueOf(chatId), sb.toString());
        mensaje.setParseMode("Markdown");

        ReplyKeyboardMarkup teclado = new ReplyKeyboardMarkup();
        teclado.setResizeKeyboard(true);
        KeyboardRow fila = new KeyboardRow();
        for (int i = 1; i <= p.getOpciones().size(); i++) fila.add(String.valueOf(i));
        fila.add("Fin");
        teclado.setKeyboard(Collections.singletonList(fila));
        mensaje.setReplyMarkup(teclado);

        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void procesarRespuestaPractica(long chatId, String mensaje) {
        Examen practica = practicasActivas.get(chatId);

        if (mensaje.equalsIgnoreCase("Fin")) {
            practicasActivas.remove(chatId);
            enviarMensaje(chatId, "✅ Has terminado la práctica.\nAciertos: " + practica.getAciertos() + ", Errores: " + practica.getErrores());
            return;
        }

        try {
            int opcion = Integer.parseInt(mensaje) - 1;
            practica.responderPregunta(opcion);
            boolean ultimaCorrecta = Boolean.TRUE.equals(practica.getUltimaRespuestaCorrecta());
            enviarMensaje(chatId, ultimaCorrecta ? "✅ Correcto" : "❌ Incorrecto");
            practica.siguientePregunta();
            enviarPreguntaPractica(chatId, practica);
        } catch (NumberFormatException ignored) {
            enviarMensaje(chatId, "Selecciona el número de la opción o escribe 'Terminar práctica' o 'Fin'.");
        }
    }

    // 3️⃣ mostrarCategoriasExamen: asegura callback correcto
    private void mostrarCategoriasExamen(long chatId) {
        List<String> categorias = new ArrayList<>(preguntaService.getCategorias());
        if (categorias.isEmpty()) {
            enviarMensaje(chatId, "⚠️ No hay categorías disponibles para el examen.");
            return;
        }

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText("📚 Elige una categoría para el examen final:");

        List<List<InlineKeyboardButton>> filas = new ArrayList<>();
        for (String cat : categorias) {
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(cat)
                            .callbackData("examen_categoria_" + cat)
                            .build()
            ));
        }

        mensaje.setReplyMarkup(new InlineKeyboardMarkup(filas));
        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean esCategoriaValida(String cat) {
        return preguntaService.getCategorias().contains(cat);
    }

    // 4️⃣ iniciarExamenFinal: revisión compatibilidad con categorías
    private void iniciarExamenFinal(long chatId, String categoria) {
        if (!esCategoriaValida(categoria)) {
            enviarMensaje(chatId, "⚠️ La categoría seleccionada no es válida.");
            return;
        }

        List<Pregunta> preguntas = preguntaService.getPreguntasExamen(categoria, CANTIDAD_PREGUNTAS_EXAMEN);
        if (preguntas.isEmpty()) {
            enviarMensaje(chatId, "No hay preguntas disponibles para la categoría: " + categoria);
            return;
        }

        Examen examen = new Examen(preguntas);
        examenesActivos.put(chatId, examen);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (examenesActivos.containsKey(chatId)) {
                    finalizarYGuardarExamen(chatId, examen, "⏱ Se acabó el tiempo (40 minutos).");
                }
            }
        }, DURACION_MINUTOS_EXAMEN * 60 * 1000L);
        timersExamen.put(chatId, timer);

        enviarPreguntaExamen(chatId, examen);
    }


    private void enviarPreguntaExamen(long chatId, Examen examen) {
        Pregunta p = examen.getPreguntaActual();
        if (p == null) {
            finalizarYGuardarExamen(chatId, examen, "🎉 Has contestado todas las preguntas.");
            return;
        }

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));

        StringBuilder sb = new StringBuilder();
        sb.append("Pregunta ").append(examen.getIndiceActual() + 1)
            .append("/").append(examen.totalPreguntas()).append("\n\n");
        sb.append(p.getTexto()).append("\n");
        for (int i = 0; i < p.getOpciones().size(); i++) {
            sb.append(i + 1).append(". ").append(p.getOpciones().get(i)).append("\n");
        }
        sb.append("\n⏱ Tiempo restante: ").append(examen.tiempoRestanteSegundos(DURACION_MINUTOS_EXAMEN)/60).append(" minutos");
        sb.append("\n👉 Para *terminar el examen*, escribe o pulsa: 'Fin'");

        mensaje.setText(sb.toString());

        ReplyKeyboardMarkup teclado = new ReplyKeyboardMarkup();
        teclado.setResizeKeyboard(true);
        List<KeyboardRow> filas = new ArrayList<>();

        // Fila de opciones numéricas
        KeyboardRow filaOpciones = new KeyboardRow();
        for (int i = 1; i <= p.getOpciones().size(); i++) filaOpciones.add(String.valueOf(i));
        filas.add(filaOpciones);

        // Fila de controles
        KeyboardRow filaControles = new KeyboardRow();
        filaControles.add("Sig");
        filaControles.add("Retr");
        filaControles.add("Fin");
        filas.add(filaControles);

        teclado.setKeyboard(filas);
        mensaje.setReplyMarkup(teclado);

        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void procesarRespuestaExamen(long chatId, String mensaje) {
        Examen examen = examenesActivos.get(chatId);
        if (examen == null) return;

        String msg = mensaje.trim();

        // Validar controles
        if (msg.equalsIgnoreCase("Sig")) {
            examen.siguientePregunta();
            enviarPreguntaExamen(chatId, examen);
            return;
        }
        if (msg.equalsIgnoreCase("Retr")) {
            examen.setIndiceActual(Math.max(0, examen.getIndiceActual() - 1));
            enviarPreguntaExamen(chatId, examen);
            return;
        }
        if (msg.equalsIgnoreCase("Fin")) {
            finalizarYGuardarExamen(chatId, examen, "Has decidido terminar el examen.");
            return;
        }

        // Validar opciones numéricas
        try {
            int opcion = Integer.parseInt(msg) - 1;
            if (opcion < 0 || opcion >= examen.getPreguntaActual().getOpciones().size()) {
                enviarMensaje(chatId, "Opción inválida, selecciona un número válido o pulsa Sig, Retr o escribe 'Fin'.");
                return;
            }
            examen.responderPregunta(opcion);
            Boolean correcta = examen.getUltimaRespuestaCorrecta();
            enviarMensaje(chatId, correcta != null && correcta ? "✅ Correcto" : "❌ Incorrecto");
            examen.siguientePregunta();
            enviarPreguntaExamen(chatId, examen);
        } catch (NumberFormatException e) {
            enviarMensaje(chatId, "Selecciona un número válido o pulsa Sig, Retr o escribe 'Fin'.");
        }
    }


    private void finalizarYGuardarExamen(long chatId, Examen examen, String motivo) {
        examenesActivos.remove(chatId);
        Timer t = timersExamen.remove(chatId);
        if (t != null) t.cancel();

        boolean aprobado = examen.aprobado(UMBRAL_APROBADO);
        String resultado = aprobado
                ? String.format("🎉 Aprobado: %d/%d", examen.getAciertos(), examen.totalPreguntas())
                : String.format("❌ Suspendido: %d/%d", examen.getAciertos(), examen.totalPreguntas());
        enviarMensaje(chatId, motivo + "\n" + resultado);

        estadisticaService.guardarResultado(String.valueOf(chatId), examen, UMBRAL_APROBADO);
    }

    //Estadisticas
    private void mostrarEstadisticas(long chatId) {
        String chatIdStr = String.valueOf(chatId);

        String mensaje = estadisticaService.generarEstadisticas(chatIdStr);

        enviarMensaje(chatId, mensaje);
    }


    // Utilidades
    private void enviarMensaje(long chatId, String texto) {
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(texto);
        try { execute(mensaje); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }
}
