package botTelegram.service;

import botTelegram.model.Pregunta;
import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
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

    // Inyectamos la ruta desde application.properties
    @Value("${preguntas.archivo.ruta}")
    private String rutaJson;

    private Map<String, List<Pregunta>> preguntasPorCategoria = new HashMap<>();

    public PreguntaServiceImple() {
    }

    @PostConstruct
    public void init() {
        cargarPreguntas();
    }

    public void cargarPreguntas() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            File file = new File(rutaJson);

            // 1. Si el archivo no existe, inicializamos un mapa vacío y salimos
            if (!file.exists()) {
                this.preguntasPorCategoria = new HashMap<>();
                return;
            }

            // 2. Leemos el archivo y REEMPLAZAMOS el mapa actual (en lugar de hacer putAll)
            // Esto elimina cualquier categoría antigua que ya no esté en el JSON
            Map<String, List<Pregunta>> nuevoMap = mapper.readValue(file,
                    new TypeReference<Map<String, List<Pregunta>>>() {});

            if (nuevoMap != null) {
                this.preguntasPorCategoria = nuevoMap;
            } else {
                this.preguntasPorCategoria = new HashMap<>();
            }

        } catch (Exception e) {
            // En caso de error crítico (archivo corrupto), evitamos que el bot deje de funcionar
            // inicializando un mapa vacío para evitar NullPointerExceptions
            e.printStackTrace();
            this.preguntasPorCategoria = new HashMap<>();
        }
    }

    public List<Pregunta> getPreguntasExamen(String categoria, int cantidad) {
        List<Pregunta> lista = preguntasPorCategoria.getOrDefault(categoria, new ArrayList<>());
        if (lista.isEmpty()) return Collections.emptyList();
        List<Pregunta> copia = new ArrayList<>(lista);
        Collections.shuffle(copia);
        return copia.subList(0, Math.min(cantidad, copia.size()));
    }


    public Set<String> getCategorias() {
        return preguntasPorCategoria.keySet();
    }

    public void agregarPregunta(String categoria, Pregunta pregunta) {
        preguntasPorCategoria.putIfAbsent(categoria, new ArrayList<>());
        preguntasPorCategoria.get(categoria).add(pregunta);
        guardarPreguntasEnJson();
    }

    public boolean validarEstructuraJson(String contenidoJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Intentamos convertir el String en el mapa que usa tu aplicación
            // Si falla, lanzará una excepción
            mapper.readValue(contenidoJson, new TypeReference<Map<String, List<Pregunta>>>() {});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void guardarPreguntasEnJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(rutaJson), preguntasPorCategoria);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

