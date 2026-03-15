package botTelegram.service;

import botTelegram.model.Pregunta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PreguntaServiceImple {

    private static final String RUTA_JSON = "C:/botTelegram (1)/botTelegram/botTelegram/src/main/java/botTelegram/recursos/preguntas.json";

    private Map<String, List<Pregunta>> preguntasPorCategoria = new HashMap<>();

    public PreguntaServiceImple() {
        cargarPreguntas();
    }

    public void cargarPreguntas() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(RUTA_JSON);
            if (!file.exists()) {
                preguntasPorCategoria = new HashMap<>();
                return;
            }
            Map<String, List<Pregunta>> map = mapper.readValue(file, new TypeReference<Map<String, List<Pregunta>>>() {});
            if (map != null) preguntasPorCategoria.putAll(map);
        } catch (Exception e) {
            e.printStackTrace();
            preguntasPorCategoria = new HashMap<>();
        }
    }

    public List<Pregunta> getPreguntasExamen(String categoria, int cantidad) {
        List<Pregunta> lista = preguntasPorCategoria.getOrDefault(categoria, new ArrayList<>());
        if (lista.isEmpty()) return Collections.emptyList();
        List<Pregunta> copia = new ArrayList<>(lista);
        Collections.shuffle(copia);
        return copia.subList(0, Math.min(cantidad, copia.size()));
    }

    public List<Pregunta> getTodasLasPreguntas() {
        List<Pregunta> todas = new ArrayList<>();
        for (List<Pregunta> lista : preguntasPorCategoria.values()) todas.addAll(lista);
        return todas;
    }

    public Set<String> getCategorias() {
        return preguntasPorCategoria.keySet();
    }

    public void agregarPregunta(String categoria, Pregunta pregunta) {
        preguntasPorCategoria.putIfAbsent(categoria, new ArrayList<>());
        preguntasPorCategoria.get(categoria).add(pregunta);
        guardarPreguntasEnJson();
    }

    public int cargarPreguntasDesdeJson(File archivoJson) {
        int totalCargadas = 0;
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<Pregunta>> nuevas = mapper.readValue(archivoJson,
                    new TypeReference<Map<String, List<Pregunta>>>() {});
            preguntasPorCategoria.clear();
            preguntasPorCategoria.putAll(nuevas);

            for (List<Pregunta> lista : nuevas.values()) totalCargadas += lista.size();

            guardarPreguntasEnJson();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return totalCargadas;
    }

    private void guardarPreguntasEnJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(RUTA_JSON), preguntasPorCategoria);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

