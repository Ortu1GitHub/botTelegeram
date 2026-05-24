package botTelegram.bot;

import botTelegram.model.EstadoCreacionPregunta;
import botTelegram.model.Examen;
import botTelegram.model.Pregunta;
import botTelegram.model.Usuario;
import botTelegram.repository.UsuarioRepository;
import botTelegram.service.EstadisticaService;
import botTelegram.service.ExportadorExcelService;
import botTelegram.service.PreguntaServiceImple;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.telegram.telegrambots.bots.DefaultBotOptions;

@Component
public class DrivingBot extends TelegramLongPollingBot {

    @Value("${preguntas.archivo.ruta}")
    private String rutaJson;

    private final UsuarioRepository usuarioRepo;
    private final PreguntaServiceImple preguntaService;
    private final EstadisticaService estadisticaService;
    private final ExportadorExcelService exportadorService;
    private final Map<Long, EstadoCreacionPregunta> asistentes = new ConcurrentHashMap<>();


    private final Map<Long, Examen> examenesActivos = new ConcurrentHashMap<>();
    private final Map<Long, Examen> practicasActivas = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> tareasExamen = new ConcurrentHashMap<>();

    private static final int DURACION_MINUTOS_EXAMEN = 40;
    private static final int CANTIDAD_PREGUNTAS_EXAMEN = 30;
    private static final int UMBRAL_APROBADO = 26;

    private static final String SOLO_JSON = "⚠️ Solo se permiten archivos con extensión .json";
    private static final String ARCHIVO_JSON_OK = "✅ Archivo JSON recibido y reemplazado con éxito.";
    private static final String ERROR_JSON = "❌ Error al procesar el archivo JSON.";

    @Value("${telegram.bot.username}")
    private String botUsername;
    @Value("${telegram.bot.token}")
    private String botToken;
    @Value("${telegram.admin.id:}")
    private String adminIdConfigured;
    @Value("${telegram.stars.precio:100}")
    private int starsPrecio;

    @Value("${telegram.imagenes.baseUrl:}")
    private String imagenesBaseUrl;



    @SuppressWarnings("deprecation")
    public DrivingBot(DefaultBotOptions options,
                      UsuarioRepository usuarioRepo,
                      PreguntaServiceImple preguntaService,
                      EstadisticaService estadisticaService,
                      ExportadorExcelService exportadorService) {
        super(options);
        this.usuarioRepo = usuarioRepo;
        this.preguntaService = preguntaService;
        this.estadisticaService = estadisticaService;
        this.exportadorService = exportadorService;
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasPreCheckoutQuery()) {
            manejarPreCheckout(update.getPreCheckoutQuery());
            return;
        }
        if (update.hasCallbackQuery()) {
            manejarCallback(update);
            return;
        }
        if (!update.hasMessage()) return;
        Message message = update.getMessage();
        if (message.hasSuccessfulPayment()) {
            manejarPagoCompletado(message.getChatId(), message);
            return;
        }
        manejarMensaje(message);
    }

    private void manejarCallback(@Nonnull Update update) {
        String callbackQueryId = update.getCallbackQuery().getId();
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        // --- 1. RESPUESTA INMEDIATA AL CALLBACK (CORRECCIÓN DEL ERROR 400) ---
        try {
            // Usamos la clase AnswerCallbackQuery de la librería
            AnswerCallbackQuery answer =
                    new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            execute(answer);
        } catch (TelegramApiException e) {
            // Si la query es vieja (más de 10s), Telegram lanzará el error 400.
            // Lo capturamos y solo logueamos un aviso para no ensuciar la consola con el stacktrace.
            if (e.getMessage().contains("query is too old")) {
                System.out.println("Callback antiguo ignorado para el chat: " + chatId);
            } else {
                e.printStackTrace();
            }
        }

        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = (u != null && u.isAdmin()) || String.valueOf(chatId).equals(adminIdConfigured);

        // --- 2. LÓGICA DE NAVEGACIÓN (FLECHAS Y FINALIZACIÓN) ---
        if (data.contains("_nav_")) {
            boolean esModoExamen = data.startsWith("examen");
            Examen sesion = esModoExamen ? examenesActivos.get(chatId) : practicasActivas.get(chatId);

            if (sesion != null) {
                if (data.endsWith("_retro")) {
                    int anterior = sesion.anteriorNoRespondido();
                    if (anterior >= 0) {
                        sesion.setIndiceActual(anterior);
                        if (esModoExamen) enviarPreguntaExamen(chatId, sesion);
                        else enviarPreguntaPractica(chatId, sesion);
                    }
                    // Si no hay ninguna sin responder antes: no hace nada
                }
                else if (data.endsWith("_sig")) {
                    if (sesion.getIndiceActual() < sesion.totalPreguntas() - 1) {
                        sesion.siguientePregunta();
                        if (esModoExamen) enviarPreguntaExamen(chatId, sesion);
                        else enviarPreguntaPractica(chatId, sesion);
                    } else {
                        // Estamos en la última pregunta
                        if (esModoExamen) {
                            // En examen: wrap-around a la primera no respondida (si existe y no es la actual)
                            int primero = sesion.primerNoRespondido();
                            if (primero < sesion.totalPreguntas() && primero != sesion.getIndiceActual()) {
                                sesion.setIndiceActual(primero);
                                enviarPreguntaExamen(chatId, sesion);
                            }
                            // Si la única sin responder es la actual, o todas respondidas: no hace nada
                        } else {
                            enviarMensaje(chatId, "ℹ️ Ya estás en la última pregunta. Usa ⬅️ para navegar o 🏁 para finalizar.");
                        }
                    }
                }
                else if (data.endsWith("_fin")) {
                    if (esModoExamen) {
                        finalizarYGuardarExamen(chatId, sesion, "🏁 Has finalizado el examen manualmente.");
                    } else {
                        enviarMensaje(chatId, "🏁 Sesión de práctica terminada.");
                        practicasActivas.remove(chatId);
                        enviarMenuPrincipal(chatId);
                    }
                }
            }
            return;
        }

        // --- 3. SELECCIÓN DE CATEGORÍA (CREACIÓN DE PREGUNTAS) ---
        if (data.startsWith("crear_preg_cat_")) {
            String categoriaSeleccionada = data.substring("crear_preg_cat_".length());
            EstadoCreacionPregunta estado = asistentes.get(chatId);
            if (estado != null) {
                estado.setCategorias(Collections.singletonList(categoriaSeleccionada));
                estado.setPaso(1);
                enviarMensaje(chatId, "✅ Categoría *" + categoriaSeleccionada.toUpperCase() + "* seleccionada.\n\n" +
                        "✍️ *PASO 2:* Escribe el *enunciado* de la pregunta:", "Markdown");
            }
            return;
        }

        // --- 4. SELECCIÓN DE RESPUESTA CORRECTA (CREACIÓN DE PREGUNTAS) ---
        if (data.startsWith("crear_preg_res_")) {
            int respuestaIdx = Integer.parseInt(data.substring("crear_preg_res_".length())) - 1;
            EstadoCreacionPregunta estado = asistentes.get(chatId);
            if (estado != null && estado.getPaso() == 3) {
                estado.setCorrecta(respuestaIdx);
                estado.setPaso(4);
                SendMessage msgFoto = new SendMessage();
                msgFoto.setChatId(String.valueOf(chatId));
                msgFoto.setParseMode("Markdown");
                msgFoto.setText("📸 *PASO 5 (opcional):* Envía una *foto* para esta pregunta o pulsa *Saltar*.");
                msgFoto.setReplyMarkup(new InlineKeyboardMarkup(Collections.singletonList(
                    Collections.singletonList(
                        InlineKeyboardButton.builder().text("⏭ Saltar foto").callbackData("crear_preg_skip_foto").build()
                    )
                )));
                try { execute(msgFoto); } catch (TelegramApiException e) { e.printStackTrace(); }
            }
            return;
        }

        // --- 4b. INTENTO DE RESPUESTA EN PREGUNTA BLOQUEADA ---
        if (data.endsWith("_bloqueada")) {
            // La pregunta ya fue respondida; no se hace nada más.
            return;
        }

        // --- 4c. SALTAR FOTO EN CREACIÓN DE PREGUNTA ---
        if (data.equals("crear_preg_skip_foto")) {
            EstadoCreacionPregunta estado = asistentes.get(chatId);
            if (estado != null && estado.getPaso() == 4) {
                guardarPreguntaConFoto(chatId, estado, null);
            }
            return;
        }

        // --- 5. RESPUESTAS DE EXAMEN ---
        if (data.startsWith("res_examen_")) {
            int opcionSeleccionada = Integer.parseInt(data.split("_")[2]) - 1;
            Examen examen = examenesActivos.get(chatId);
            if (examen != null) {
                // Guard: seguridad ante pulsaciones en mensajes anteriores ya respondidos
                if (examen.estaRespondida(examen.getIndiceActual())) {
                    enviarMensaje(chatId, "🔒 Esta pregunta ya fue respondida.");
                    return;
                }
                boolean acertada = examen.responderPregunta(opcionSeleccionada);
                if (acertada) {
                    enviarMensaje(chatId, "✅ *Correcto* (Guardado)", "Markdown");
                } else {
                    int correcta = examen.getPreguntaActual().getRespuestaCorrecta() + 1;
                    enviarMensaje(chatId, "❌ *Incorrecto*\nLa respuesta correcta era la " + correcta, "Markdown");
                }

                int siguiente = examen.siguienteNoRespondido();
                if (siguiente < examen.totalPreguntas()) {
                    examen.setIndiceActual(siguiente);
                    enviarPreguntaExamen(chatId, examen);
                } else if (examen.todasRespondidas()) {
                    finalizarYGuardarExamen(chatId, examen, "🏁 ¡Examen completado!");
                } else {
                    // Quedan preguntas sin responder antes de la posición actual
                    examen.setIndiceActual(examen.primerNoRespondido());
                    enviarPreguntaExamen(chatId, examen);
                }
            }
            return;
        }

        // --- 6. RESPUESTAS DE PRÁCTICA ---
        if (data.startsWith("res_practica_")) {
            int opcionSeleccionada = Integer.parseInt(data.split("_")[2]) - 1;
            Examen practica = practicasActivas.get(chatId);
            if (practica != null) {
                // Guard: seguridad ante pulsaciones en mensajes anteriores ya respondidos
                if (practica.estaRespondida(practica.getIndiceActual())) {
                    enviarMensaje(chatId, "🔒 Esta pregunta ya fue respondida.");
                    return;
                }
                boolean acertada = practica.responderPregunta(opcionSeleccionada);
                if (acertada) {
                    enviarMensaje(chatId, "✅ *¡Muy bien!*", "Markdown");
                } else {
                    int correcta = practica.getPreguntaActual().getRespuestaCorrecta() + 1;
                    String textoCorrecto = practica.getPreguntaActual().getOpciones().get(correcta - 1);
                    enviarMensaje(chatId, "❌ *Incorrecto*\nLa correcta es la " + correcta + ":\n" + textoCorrecto, "Markdown");
                }

                int siguiente = practica.siguienteNoRespondido();
                if (siguiente < practica.totalPreguntas()) {
                    practica.setIndiceActual(siguiente);
                    enviarPreguntaPractica(chatId, practica);
                } else if (practica.todasRespondidas()) {
                    enviarMensaje(chatId, "🏁 Sesión de práctica terminada.");
                    practicasActivas.remove(chatId);
                    enviarMenuPrincipal(chatId);
                } else {
                    // Quedan preguntas sin responder antes de la posición actual
                    practica.setIndiceActual(practica.primerNoRespondido());
                    enviarPreguntaPractica(chatId, practica);
                }
            }
            return;
        }

        // --- 7. INICIO DE SESIONES Y MENÚS ---
        if (data.startsWith("examen_categoria_")) {
            iniciarExamenFinal(chatId, data.substring("examen_categoria_".length()));
            return;
        }
        if (data.startsWith("practica_cat_")) {
            iniciarPracticaPorCategoria(chatId, data.substring("practica_cat_".length()));
            return;
        }

        switch (data) {
            case "menu_practica": mostrarCategoriasPractica(chatId); break;
            case "menu_examen":
                if (esAdmin || (u != null && u.isPremium()) || (u != null && !u.isExamenGratisUsado())) {
                    if (!esAdmin && (u != null) && !u.isPremium() && !u.isExamenGratisUsado()) {
                        enviarMensaje(chatId, "ℹ️ Tienes *1 examen gratuito* disponible. ¡Buena suerte!", "Markdown");
                    }
                    mostrarCategoriasExamen(chatId);
                } else {
                    enviarMensaje(chatId, "⭐ Ya has usado tu examen gratuito. El Modo Examen es exclusivo para usuarios *Premium*.\nPulsa *Obtener Premium* en el menú para acceder.", "Markdown");
                    enviarMenuPrincipal(chatId);
                }
                break;
            case "comprar_premium":
                if (u != null && u.isPremium()) {
                    enviarMensaje(chatId, "✅ Ya eres usuario Premium. ¡Disfruta del Modo Examen!");
                } else {
                    enviarFacturaPremium(chatId);
                }
                break;
            case "menu_estadisticas": mostrarEstadisticas(chatId); break;
            case "menu_stats_global":
                if (esAdmin) {
                    enviarMensaje(chatId, estadisticaService.generarEstadisticasGlobales(), "Markdown");
                    enviarMenuPrincipal(chatId);
                }
                break;
            case "menu_crear_pregunta":
                if (esAdmin) {
                    asistentes.put(chatId, new EstadoCreacionPregunta());
                    mostrarCategoriasCreacionPregunta(chatId);
                }
                break;
            case "menu_subir_json":
                if (esAdmin) {
                    enviarMensaje(chatId, "📂 Envía ahora el archivo JSON de preguntas.");
                }
                break;
            case "descargar_excel_personal":
                descargarExcel(chatId, String.valueOf(chatId));
                break;
            case "descargar_excel_global":
                if (esAdmin) {
                    descargarExcel(chatId, null);
                }
                break;
            case "menu_cancelar":
                enviarMensaje(chatId, "Bot cerrado. Escribe /start para volver.");
                break;
        }
    }

    // Metodo auxiliar para evitar repetir código en los Case de Excel
    private void descargarExcel(long chatId, String usuarioId) {
        try {
            ByteArrayInputStream bis = exportadorService.generarExcelEstadisticas(usuarioId);
            String nombre = (usuarioId == null) ? "Reporte_Global.xlsx" : "Mis_Estadisticas.xlsx";
            enviarDocumento(chatId, bis, nombre);
            enviarMenuPrincipal(chatId);
        } catch (IOException e) {
            enviarMensaje(chatId, "❌ Error al generar el Excel.");
        }
    }

    private void manejarMensaje(Message message) {
        long chatId = message.getChatId();
        String texto = message.hasText() ? message.getText().trim() : "";

        // 1. Registro de usuario (si no existe en la BD)
        if (usuarioRepo.findById(String.valueOf(chatId)).isEmpty()) {
            String nombre = message.getFrom() != null ? message.getFrom().getFirstName() : "Usuario";
            usuarioRepo.save(new Usuario(String.valueOf(chatId), nombre, false));
        }

        // 2. Comandos globales y gestión de archivos
        if (texto.equalsIgnoreCase("/start") || texto.equalsIgnoreCase("menu")) {
            // Si hay un examen activo, finalizarlo correctamente para guardar resultados
            Examen examenActivo = examenesActivos.get(chatId);
            if (examenActivo != null) {
                finalizarYGuardarExamen(chatId, examenActivo, "⚠️ Examen interrumpido al volver al menú.");
            } else {
                practicasActivas.remove(chatId);
                asistentes.remove(chatId);
                ScheduledFuture<?> tareaActiva = tareasExamen.remove(chatId);
                if (tareaActiva != null) tareaActiva.cancel(false);
                enviarMenuPrincipal(chatId);
            }
            return;
        }

        if (message.hasDocument()) {
            manejarArchivo(chatId, message.getDocument());
            return;
        }

        // 3. Procesamiento del Asistente de Creación
        if (asistentes.containsKey(chatId)) {
            EstadoCreacionPregunta estado = asistentes.get(chatId);
            // Si estamos en el paso de foto y el admin envía una imagen
            if (estado.getPaso() == 4 && message.hasPhoto()) {
                List<PhotoSize> fotos = message.getPhoto();
                String fileId = fotos.get(fotos.size() - 1).getFileId();
                guardarPreguntaConFoto(chatId, estado, fileId);
                return;
            }
            manejarAsistente(chatId, texto);
            return;
        }

        // 4. Bloqueo de texto durante Exámenes o Prácticas
        if (examenesActivos.containsKey(chatId) || practicasActivas.containsKey(chatId)) {
            enviarMensaje(chatId, "⚠️ Por favor, utiliza los botones de la pregunta para responder.");
        }
    }

    private void manejarArchivo(long chatId, Document document) {
        // 1. Verificación de extensión
        if (!document.getFileName().endsWith(".json")) {
            enviarMensaje(chatId, SOLO_JSON);
            // Volvemos al menú si el archivo no es un JSON
            enviarMenuPrincipal(chatId);
            return;
        }

        try {
            // Obtener la URL de descarga desde Telegram
            GetFile getFile = new GetFile(document.getFileId());
            File file = execute(getFile);
            String fileUrl = file.getFileUrl(getBotToken());

            // Descargar el contenido del JSON
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String nuevoJson = response.body();

            // 2. VALIDACIÓN CRÍTICA
            if (preguntaService.validarEstructuraJson(nuevoJson)) {
                // Guardado físico
                try (FileOutputStream outputStream = new FileOutputStream(rutaJson)) {
                    outputStream.write(nuevoJson.getBytes());
                }

                // Recarga de memoria
                preguntaService.cargarPreguntas();
                enviarMensaje(chatId, ARCHIVO_JSON_OK);
            } else {
                enviarMensaje(chatId, "❌ El archivo JSON está vacío o no tiene el formato esperado.");
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            enviarMensaje(chatId, ERROR_JSON);
        } catch (Exception ex) {
            enviarMensaje(chatId, "❌ Error de formato: Asegúrate de que el JSON sea válido (revisa comas y llaves).");
        } finally {
            // Volver al menu principal en caso de exito/error
            enviarMenuPrincipal(chatId);
        }
    }

    private void guardarPreguntaConFoto(long chatId, EstadoCreacionPregunta estado, String imagenUrl) {
        Pregunta p = new Pregunta(estado.getTexto(), estado.getOpciones(), estado.getCorrecta());
        if (imagenUrl != null) p.setImagenUrl(imagenUrl);
        for (String cat : estado.getCategorias()) {
            preguntaService.agregarPregunta(cat, p);
        }
        asistentes.remove(chatId);
        String cat = estado.getCategorias().get(0).toUpperCase();
        String confirmacion = imagenUrl != null
            ? "✅ ¡Pregunta *con foto* añadida a la categoría *" + cat + "*!"
            : "✅ ¡Pregunta añadida a la categoría *" + cat + "* (sin foto).";
        enviarMensaje(chatId, confirmacion, "Markdown");
        enviarMenuPrincipal(chatId);
    }

    private void manejarAsistente(long chatId, String mensaje) {
        EstadoCreacionPregunta estado = asistentes.get(chatId);
        String msg = mensaje.trim();
        if (msg.equalsIgnoreCase("salir")) {
            asistentes.remove(chatId);
            enviarMensaje(chatId, "✅ Asistente cancelado.");
            enviarMenuPrincipal(chatId);
            return;
        }
        if (estado == null) return;

        switch (estado.getPaso()) {
            case 1:
                // Recibimos el TEXTO de la pregunta
                manejarPasoTexto(chatId, estado, msg);
                break;
            case 2:
                // Recibimos las OPCIONES una por una (se llamará 3 veces)
                manejarPasoOpcionesSecuencial(chatId, estado, msg);
                break;
            case 3:
                // Recibimos el ÍNDICE de la correcta
                manejarPasoCorrecta(chatId, estado, msg);
                break;
            case 4:
                // Esperando foto — re-enviar recordatorio CON el botón Saltar
                SendMessage recordatorio = new SendMessage();
                recordatorio.setChatId(String.valueOf(chatId));
                recordatorio.setParseMode("Markdown");
                recordatorio.setText("📸 Envía una *foto* para la pregunta o pulsa *Saltar foto*.");
                recordatorio.setReplyMarkup(new InlineKeyboardMarkup(Collections.singletonList(
                    Collections.singletonList(
                        InlineKeyboardButton.builder().text("⏭ Saltar foto").callbackData("crear_preg_skip_foto").build()
                    )
                )));
                try { execute(recordatorio); } catch (TelegramApiException e) { e.printStackTrace(); }
                break;
            default:
                enviarMensaje(chatId, "Paso no reconocido en el asistente.");
                break;
        }
    }

    private void manejarPasoTexto(long chatId, EstadoCreacionPregunta estado, String msg) {
        estado.setTexto(msg);
        estado.setPaso(2);
        estado.getOpciones().clear();
        enviarMensaje(chatId, "✍️ Texto guardado. Ahora, ingresa la *Opción 1*:", "Markdown");
    }

    private void manejarPasoOpcionesSecuencial(long chatId, EstadoCreacionPregunta estado, String msg) {
        List<String> opciones = estado.getOpciones();
        opciones.add(msg);

        if (opciones.size() < 3) {
            enviarMensaje(chatId, "✍️ Ingresa la *Opción " + (opciones.size() + 1) + "*:", "Markdown");
        } else {
            estado.setPaso(3);

            SendMessage mensaje = new SendMessage();
            mensaje.setChatId(String.valueOf(chatId));
            mensaje.setParseMode("Markdown");

            StringBuilder sb = new StringBuilder("✅ Opciones guardadas:\n");
            for (int i = 0; i < opciones.size(); i++) {
                sb.append(i + 1).append(". ").append(opciones.get(i)).append("\n");
            }
            sb.append("\n🔢 Por último, selecciona la *respuesta correcta*:");
            mensaje.setText(sb.toString());

            // Botones Inline 1, 2, 3
            List<InlineKeyboardButton> filaBotones = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                filaBotones.add(InlineKeyboardButton.builder()
                        .text(String.valueOf(i))
                        .callbackData("crear_preg_res_" + i)
                        .build());
            }
            mensaje.setReplyMarkup(new InlineKeyboardMarkup(Collections.singletonList(filaBotones)));

            try {
                execute(mensaje);
            } catch (TelegramApiException e) { e.printStackTrace(); }
        }
    }

    private void manejarPasoCorrecta(long chatId, EstadoCreacionPregunta estado, String msg) {
        try {
            int idx = Integer.parseInt(msg) - 1;
            if (idx < 0 || idx > 2) throw new NumberFormatException();

            estado.setCorrecta(idx);
            estado.setPaso(4);

            SendMessage msgFoto = new SendMessage();
            msgFoto.setChatId(String.valueOf(chatId));
            msgFoto.setParseMode("Markdown");
            msgFoto.setText("📸 *PASO 5 (opcional):* Envía una *foto* para esta pregunta o pulsa *Saltar*.");
            msgFoto.setReplyMarkup(new InlineKeyboardMarkup(Collections.singletonList(
                Collections.singletonList(
                    InlineKeyboardButton.builder().text("⏭ Saltar foto").callbackData("crear_preg_skip_foto").build()
                )
            )));
            try { execute(msgFoto); } catch (TelegramApiException e) { e.printStackTrace(); }

        } catch (NumberFormatException e) {
            enviarMensaje(chatId, "⚠️ Por favor, introduce un número válido (1, 2 o 3) o escribe 'salir'.");
        }
    }

    private void enviarMenuPrincipal(long chatId) {
        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = (u != null && u.isAdmin()) || String.valueOf(chatId).equals(adminIdConfigured);
        boolean esPremium = (u != null && u.isPremium());

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        String textoMenu = esPremium
                ? "¡Bienvenido, usuario ⭐ Premium! Elige una opción:"
                : "👋 ¡Bienvenido al DrivingBot! Recuerda que el modo práctica es gratuito y dispones de 1 examen antes de ser Premium.\nElige una opción:";
        mensaje.setText(textoMenu);

        mensaje.setReplyMarkup(BotKeyboardFactory.crearMenuPrincipalInline(esAdmin, esPremium));

        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("403")) {
                System.out.println("El usuario con ID " + chatId + " ha bloqueado al bot. Vuelva a arrancarlo...");
            } else {
                e.printStackTrace();
            }
        }
    }

    private void enviarPreguntaPractica(long chatId, Examen practica) {
        Pregunta p = practica.getPreguntaActual();
        if (p == null) {
            enviarMensaje(chatId, "🏁 Sesión de práctica terminada.");
            practicasActivas.remove(chatId);
            enviarMenuPrincipal(chatId);
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Línea 1: Contador de posición + progreso
        sb.append("🧩 *Práctica ").append(practica.getIndiceActual() + 1)
                .append("/").append(practica.totalPreguntas()).append("*");
        sb.append(" · 📊 Respondidas: ").append(practica.getTotalRespondidas())
                .append("/").append(practica.totalPreguntas()).append("\n");

        // Línea 2: Indicador de pregunta ya respondida (bloqueada)
        Integer respuestaPrevia = practica.getRespuestaUsuario(practica.getIndiceActual());
        if (respuestaPrevia != null) {
            sb.append("🔒 _Respondida — opción ").append(respuestaPrevia + 1).append("_\n");
        }

        // Línea 3: Instrucción de fin
        sb.append("🏁 Para terminar, pulsa: *Fin*\n\n");

        // Línea 4: Enunciado
        sb.append("*").append(p.getTexto()).append("*");

        SendMessage mensaje = new SendMessage(String.valueOf(chatId), sb.toString());
        mensaje.setParseMode("Markdown");

        // Teclado: bloqueado si ya respondida, normal si no
        InlineKeyboardMarkup teclado;
        if (respuestaPrevia != null) {
            teclado = BotKeyboardFactory.crearTecladoRespondida(p.getOpciones(), "practica", respuestaPrevia);
        } else {
            teclado = BotKeyboardFactory.crearTecladoOpcionesInline(p.getOpciones(), "practica");
        }

        try {
            InputFile inputFile = getInputFilePregunta(p);
            if (inputFile != null) {
                SendPhoto foto = new SendPhoto();
                foto.setChatId(String.valueOf(chatId));
                foto.setPhoto(inputFile);
                foto.setCaption(sb.toString());
                foto.setParseMode("Markdown");
                foto.setReplyMarkup(teclado);
                execute(foto);
            } else {
                mensaje.setReplyMarkup(teclado);
                execute(mensaje);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

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
                            .text(cat.toUpperCase())
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

    private void iniciarExamenFinal(long chatId, String categoria) {
        // Guard 1: no iniciar si ya hay un examen activo para este usuario
        if (examenesActivos.containsKey(chatId)) {
            enviarMensaje(chatId, "⚠️ Ya tienes un examen en curso. Usa los botones para continuar o pulsa *Fin* para terminarlo.", "Markdown");
            return;
        }

        Usuario u = usuarioRepo.findById(String.valueOf(chatId)).orElse(null);
        boolean esAdmin = (u != null && u.isAdmin()) || String.valueOf(chatId).equals(adminIdConfigured);
        boolean tienePremium = (u != null && u.isPremium());
        boolean gratisDisponible = (u != null && !u.isExamenGratisUsado());

        // Guard 2: bloquear a usuarios no premium que ya usaron su examen gratuito
        if (!esAdmin && !tienePremium && !gratisDisponible) {
            enviarMensaje(chatId, "⭐ Ya has usado tu examen gratuito. El Modo Examen requiere Premium. Usa el menú para obtenerlo.");
            enviarMenuPrincipal(chatId);
            return;
        }
        if (!esCategoriaValida(categoria)) {
            enviarMensaje(chatId, "⚠️ La categoría seleccionada no es válida.");
            return;
        }

        List<Pregunta> preguntas = preguntaService.getPreguntasExamen(categoria, CANTIDAD_PREGUNTAS_EXAMEN);
        if (preguntas.isEmpty()) {
            enviarMensaje(chatId, "No hay preguntas disponibles para la categoría: " + categoria);
            return;
        }

        // Guard 3: marcar el examen gratuito de forma ATÓMICA en BD para evitar condiciones de
        // carrera con el hilo de Telegram (maxThreads=10). El UPDATE solo aplica si el flag es
        // NULL o false; si otro hilo se adelantó (devuelve 0 filas), bloqueamos aquí.
        if (!esAdmin && !tienePremium) {
            int marcados = usuarioRepo.marcarExamenGratisUsado(String.valueOf(chatId));
            if (marcados == 0) {
                enviarMensaje(chatId, "⭐ Ya has usado tu examen gratuito. El Modo Examen requiere Premium. Usa el menú para obtenerlo.");
                enviarMenuPrincipal(chatId);
                return;
            }
        }

        Examen examen = new Examen(preguntas, categoria);
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
        // Línea 1: Contador de posición
        sb.append("📝 *Pregunta ").append(examen.getIndiceActual() + 1)
                .append("/").append(examen.totalPreguntas()).append("*");

        // Línea 1b: Progreso de respondidas (misma línea visual, separado por ·)
        sb.append(" · 📊 Respondidas: ").append(examen.getTotalRespondidas())
                .append("/").append(examen.totalPreguntas()).append("\n");

        // Línea 2: Tiempo restante
        long minRestantes = examen.tiempoRestanteSegundos(DURACION_MINUTOS_EXAMEN) / 60;
        sb.append("⏱ Tiempo restante: ").append(minRestantes).append(" minutos\n");

        // Línea 3: Indicador de pregunta ya respondida (bloqueada)
        Integer respuestaPrevia = examen.getRespuestaUsuario(examen.getIndiceActual());
        if (respuestaPrevia != null) {
            sb.append("🔒 _Respondida — opción ").append(respuestaPrevia + 1).append("_\n");
        }

        // Línea 4: Instrucción de fin
        sb.append("🏁 Para terminar el examen, pulsa: *Fin*\n\n");

        // Línea 5: Enunciado de la pregunta
        sb.append("*").append(p.getTexto()).append("*");

        // 2. Configuración del mensaje
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(sb.toString());
        mensaje.setParseMode("Markdown");

        // Teclado: bloqueado si ya respondida, normal si no
        InlineKeyboardMarkup teclado;
        if (respuestaPrevia != null) {
            teclado = BotKeyboardFactory.crearTecladoRespondida(p.getOpciones(), "examen", respuestaPrevia);
        } else {
            teclado = BotKeyboardFactory.crearTecladoOpcionesInline(p.getOpciones(), "examen");
        }

        // 4. Envío
        try {
            InputFile inputFile = getInputFilePregunta(p);
            if (inputFile != null) {
                SendPhoto foto = new SendPhoto();
                foto.setChatId(String.valueOf(chatId));
                foto.setPhoto(inputFile);
                foto.setCaption(sb.toString());
                foto.setParseMode("Markdown");
                foto.setReplyMarkup(teclado);
                execute(foto);
            } else {
                mensaje.setReplyMarkup(teclado);
                execute(mensaje);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void finalizarYGuardarExamen(long chatId, Examen examen, String motivo) {
        examenesActivos.remove(chatId);
        ScheduledFuture<?> tarea = tareasExamen.remove(chatId);
        if (tarea != null) tarea.cancel(false);
        int aciertos = examen.getAciertos();
        boolean aprobado = aciertos >= UMBRAL_APROBADO;

        String resultado = (aprobado ? "🎉 ¡APROBADO!" : "❌ SUSPENDIDO") +
                "\n✅ Aciertos: " + aciertos + "/" + examen.totalPreguntas();

        enviarMensaje(chatId, motivo + "\n\n" + resultado);

        estadisticaService.guardarResultado(String.valueOf(chatId), examen, UMBRAL_APROBADO);
        enviarMenuPrincipal(chatId);
    }

    private void mostrarEstadisticas(long chatId) {
        String chatIdStr = String.valueOf(chatId);

        String reporte = estadisticaService.generarEstadisticas(chatIdStr);

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(reporte);
        mensaje.setParseMode("Markdown");

        try {
            execute(mensaje);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        enviarMenuPrincipal(chatId);
    }


    private InputFile getInputFilePregunta(Pregunta p) {
        // Determinar carpeta de imágenes = misma carpeta que el JSON
        java.io.File carpeta = new java.io.File(rutaJson).getParentFile();

        String nombre = p.getImagenUrl();

        // Sin imagenUrl explícito → fallback por número (1.jpg, 2.jpg, ...)
        if (nombre == null || nombre.isBlank()) {
            if (p.getNumero() > 0) {
                nombre = p.getNumero() + ".jpg";
            } else {
                return null;
            }
        }

        // Intentar como archivo local
        java.io.File archivo = new java.io.File(carpeta, nombre);
        if (archivo.exists()) {
            return new InputFile(archivo);
        }

        // Si empieza por http o es un Telegram file_id → InputFile por string
        if (nombre.startsWith("http://") || nombre.startsWith("https://") || !nombre.contains(".")) {
            return new InputFile(nombre);
        }

        // Fallback: baseUrl + nombre
        if (imagenesBaseUrl != null && !imagenesBaseUrl.isBlank()) {
            return new InputFile(imagenesBaseUrl + nombre);
        }

        return null;
    }

    private void enviarMensaje(long chatId, String texto) {
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(texto);
        try { execute(mensaje); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void enviarMensaje(long chatId, String texto, String parseMode) {
        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText(texto);
        mensaje.setParseMode(parseMode);
        try { execute(mensaje); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void mostrarCategoriasCreacionPregunta(long chatId) {
        List<String> categorias = new ArrayList<>(preguntaService.getCategorias());
        if (categorias.isEmpty()) {
            enviarMensaje(chatId, "⚠️ No hay categorías disponibles. Asegúrate de que el JSON esté cargado.");
            return;
        }

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText("✍️ *PASO 1:* Selecciona la *categoría* de la nueva pregunta:");
        mensaje.setParseMode("Markdown");

        List<List<InlineKeyboardButton>> filas = new ArrayList<>();
        for (String cat : categorias) {
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(cat.toUpperCase())
                            .callbackData("crear_preg_cat_" + cat)
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

    // --- NUEVOS MÉTODOS PARA PRÁCTICA POR CATEGORÍA ---

    private void mostrarCategoriasPractica(long chatId) {
        List<String> categorias = new ArrayList<>(preguntaService.getCategorias());
        if (categorias.isEmpty()) {
            enviarMensaje(chatId, "⚠️ No hay categorías disponibles para practicar.");
            return;
        }

        SendMessage mensaje = new SendMessage();
        mensaje.setChatId(String.valueOf(chatId));
        mensaje.setText("📝 Selecciona qué categoría quieres practicar:");

        List<List<InlineKeyboardButton>> filas = new ArrayList<>();
        for (String cat : categorias) {
            filas.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(cat.toUpperCase())
                            .callbackData("practica_cat_" + cat)
                            .build()
            ));
        }

        mensaje.setReplyMarkup(new InlineKeyboardMarkup(filas));
        try {
            execute(mensaje);
        } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void iniciarPracticaPorCategoria(long chatId, String categoria) {
        // Obtenemos todas las preguntas de esa categoría específica
        List<Pregunta> preguntas = preguntaService.getPreguntasExamen(categoria, 100);

        if (preguntas.isEmpty()) {
            enviarMensaje(chatId, "No hay preguntas en la categoría: " + categoria);
            return;
        }

        Collections.shuffle(preguntas);
        Examen practica = new Examen(preguntas, categoria);
        practicasActivas.put(chatId, practica);

        enviarMensaje(chatId, "🚀 Iniciando práctica de: " + categoria.toUpperCase());
        enviarPreguntaPractica(chatId, practica);
    }

    private void manejarPreCheckout(PreCheckoutQuery query) {
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        answer.setPreCheckoutQueryId(query.getId());
        answer.setOk(true);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void manejarPagoCompletado(long chatId, Message message) {
        usuarioRepo.findById(String.valueOf(chatId)).ifPresent(u -> {
            u.setPremium(true);
            usuarioRepo.save(u);
        });
        enviarMensaje(chatId, "⭐ ¡Pago recibido! Ya eres usuario *Premium*.\n¡Ahora tienes acceso completo al Modo Examen!", "Markdown");
        enviarMenuPrincipal(chatId);
    }

    private void enviarFacturaPremium(long chatId) {
        // La librería 6.9.7.1 rechaza providerToken vacío aunque la API de Telegram lo requiera para Stars (XTR).
        // Solución: llamada HTTP directa que omite la validación del SDK.
        try {
            String body = "{\"chat_id\":" + chatId
                    + ",\"title\":\"DrivingBot Premium\""
                    + ",\"description\":\"Acceda al Modo Examen: 30 preguntas, cron\\u00f3metro de 40 min, historial de resultados y exportaci\\u00f3n Excel para varios tipos de veh\\u00edculos\""
                    + ",\"payload\":\"premium_" + chatId + "\""
                    + ",\"provider_token\":\"\""
                    + ",\"currency\":\"XTR\""
                    + ",\"prices\":[{\"label\":\"Acceso Premium\",\"amount\":" + starsPrecio + "}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + getBotToken() + "/sendInvoice"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarDocumento(long chatId, ByteArrayInputStream stream, String nombreArchivo) {
        SendDocument sd = new SendDocument();
        sd.setChatId(String.valueOf(chatId));
        sd.setDocument(new InputFile(stream, nombreArchivo));
        sd.setCaption("✅ Aquí tienes tu reporte en formato Excel.");

        try {
            execute(sd);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }
}
