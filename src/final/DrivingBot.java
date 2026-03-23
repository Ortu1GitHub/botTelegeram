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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class DrivingBot extends TelegramLongPollingBot {

    @Value("${preguntas.archivo.ruta}")
    private String rutaJson;

    private final PreguntaServiceImple preguntaService;
    private final EstadisticaService estadisticaService;
    private final UsuarioRepository usuarioRepo;
    private final Map<Long, EstadoCreacionPregunta> asistentes = new ConcurrentHashMap<>();


    private final Map<Long, Examen> examenesActivos = new ConcurrentHashMap<>();
    private final Map<Long, Examen> practicasActivas = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> tareasExamen = new ConcurrentHashMap<>();

    private static final int DURACION_MINUTOS_EXAMEN = 40;
    private static final int CANTIDAD_PREGUNTAS_EXAMEN = 30;
    private static final int UMBRAL_APROBADO = 26;

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
        // 1. Extraer datos necesarios del CallbackQuery
        String callbackQueryId = update.getCallbackQuery().getId();
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        // 2. CRUCIAL: Notificar a Telegram que hemos recibido el clic.
        // Esto quita el icono de carga del botón inmediatamente.
        try {
            execute(new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(callbackQueryId));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // 3. Determinar si el usuario es administrador (BD o Configuración)
        @SuppressWarnings("null")
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = (u != null && Boolean.TRUE.equals(u.isAdmin()))
                || String.valueOf(chatId).equals(adminIdConfigured);

        // 4. Lógica de categorías de examen
        final String EXAMEN_CATEGORIA_PREFIX = "examen_categoria_";
        if (data.startsWith(EXAMEN_CATEGORIA_PREFIX)) {
            String categoria = data.substring(EXAMEN_CATEGORIA_PREFIX.length());
            iniciarExamenFinal(chatId, categoria);
            return;
        }

        // 5. Procesar acciones del menú
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
                if (esAdmin) enviarMensaje(chatId, "📂 Envía ahora el archivo JSON de preguntas.");
                else enviarMensaje(chatId, SOLO_ADMIN);
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
        if (!document.getFileName().endsWith(".json")) {
            enviarMensaje(chatId, SOLO_JSON);
            return;
        }

        try {
            // 1. Obtener la URL de descarga desde Telegram
            GetFile getFile = new GetFile(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
            String fileUrl = file.getFileUrl(getBotToken());

            // 2. Descargar el contenido del JSON a una cadena (String) para validarlo
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String nuevoJson = response.body();

            // 3. VALIDACIÓN CRÍTICA: Intentar parsear el JSON antes de guardarlo
            // Si el JSON es inválido, Jackson lanzará una excepción aquí y saltará al catch
            if (preguntaService.validarEstructuraJson(nuevoJson)) {

                // 4. Si es válido, lo guardamos físicamente en la ruta configurada
                try (FileOutputStream outputStream = new FileOutputStream(rutaJson)) {
                    outputStream.write(nuevoJson.getBytes());
                }

                // 5. Recargamos la memoria del servicio y avisamos al usuario
                preguntaService.cargarPreguntas();
                enviarMensaje(chatId, ARCHIVO_JSON_OK);

            } else {
                enviarMensaje(chatId, "❌ El archivo JSON está vacío o no tiene el formato esperado.");
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            enviarMensaje(chatId, ERROR_JSON);
        } catch (Exception ex) {
            // Si el error fue por un JSON mal formado, podemos dar un mensaje más específico
            enviarMensaje(chatId, "❌ Error de formato: Asegúrate de que el JSON sea válido (revisa comas y llaves).");
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
        // 1. Obtener datos (Usamos el repositorio que ya es un atributo de la CLASE)
        // Asegúrate de usar el nombre exacto del atributo definido arriba (ej: usuarioRepo)
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = (u != null && u.isAdmin());

        // 2. Configurar el mensaje
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText("👋 Bienvenido. Elige una opción:");

        // 3. ASIGNAR EL TECLADO (Usando la Factoría)
        mensaje.setReplyMarkup(BotKeyboardFactory.crearMenuPrincipalInline(esAdmin));

        // 4. Intentar envío
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

        // MEJORA: Usamos la factoría que ya creamos para el examen
        // Como el teclado de examen ya tiene los números y el botón 'Fin', nos sirve perfectamente
        mensaje.setReplyMarkup(BotKeyboardFactory.crearTecladoExamen(p.getOpciones().size()));

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

        // Programamos la tarea de finalización a los 40 minutos
        ScheduledFuture<?> tarea = scheduler.schedule(() -> {
            finalizarYGuardarExamen(chatId, examen, "⏱️ ¡Tiempo agotado! El examen se ha cerrado automáticamente.");
        }, 40, TimeUnit.MINUTES);

        tareasExamen.put(chatId, tarea);
        enviarPreguntaExamen(chatId, examen);
    }


    private void enviarPreguntaExamen(long chatId, Examen examen) {
        Pregunta p = examen.getPreguntaActual();
        if (p == null) {
            finalizarYGuardarExamen(chatId, examen, "🎉 Has contestado todas las preguntas.");
            return;
        }

        // 1. Construcción del texto (Lógica de presentación)
        StringBuilder sb = new StringBuilder();
        sb.append("Pregunta ").append(examen.getIndiceActual() + 1)
                .append("/").append(examen.totalPreguntas()).append("\n\n");
        sb.append(p.getTexto()).append("\n");

        for (int i = 0; i < p.getOpciones().size(); i++) {
            sb.append(i + 1).append(". ").append(p.getOpciones().get(i)).append("\n");
        }

        long minRestantes = examen.tiempoRestanteSegundos(DURACION_MINUTOS_EXAMEN) / 60;
        sb.append("\n⏱ Tiempo restante: ").append(minRestantes).append(" minutos");
        sb.append("\n👉 Para *terminar el examen*, pulsa: 'Fin'");

        // 2. Configuración del mensaje
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(sb.toString());
        mensaje.setParseMode("Markdown"); // Para que las negritas funcionen

        // 3. ASIGNAR EL TECLADO (Llamada a la Factoría)
        mensaje.setReplyMarkup(BotKeyboardFactory.crearTecladoExamen(p.getOpciones().size()));

        // 4. Envío
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
        // 1. Limpieza de mapas y cancelación de la tarea programada
        examenesActivos.remove(chatId);

        ScheduledFuture<?> tarea = tareasExamen.remove(chatId);
        if (tarea != null) {
            tarea.cancel(false);
        }

        // 2. Cálculo de resultados
        boolean aprobado = examen.aprobado(UMBRAL_APROBADO);
        String resultado = aprobado
                ? String.format("🎉 ¡ENHORABUENA! Has aprobado.\n✅ Aciertos: %d/%d", examen.getAciertos(), examen.totalPreguntas())
                : String.format("❌ Has suspendido.\n⚠️ Aciertos: %d/%d", examen.getAciertos(), examen.totalPreguntas());

        // 3. ENVIAR LOS MENSAJES POR SEPARADO
        // Primero enviamos el texto del motivo (ej: Tiempo agotado) y el resultado
        enviarMensaje(chatId, motivo + "\n\n" + resultado);

        // Luego enviamos el menú principal con su texto por defecto (el que ya tiene tu método)
        enviarMenuPrincipal(chatId);

        // 4. Guardar en Base de Datos
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
