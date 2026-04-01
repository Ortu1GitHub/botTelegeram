package botTelegram.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class Examen {
    private final List<Pregunta> preguntas;
    private int indiceActual;
    private final Map<Integer, Integer> respuestasUsuario;
    private final Map<Integer, Boolean> respuestasCorrectas;
    private final LocalDateTime inicio;
    private final String categoria;

    public Examen(List<Pregunta> preguntas, String categoria) {
        this.preguntas = new ArrayList<>(preguntas);
        this.indiceActual = 0;
        this.respuestasUsuario = new HashMap<>();
        this.respuestasCorrectas = new HashMap<>();
        this.inicio = LocalDateTime.now();
        this.categoria = categoria;
    }

    public Pregunta getPreguntaActual() {
        if (indiceActual >= 0 && indiceActual < preguntas.size()) return preguntas.get(indiceActual);
        return null;
    }

    public boolean responderPregunta(int opcion) {
        if (indiceActual < 0 || indiceActual >= preguntas.size()) return false;
        Pregunta p = preguntas.get(indiceActual);
        respuestasUsuario.put(indiceActual, opcion);
        boolean correcta = opcion == p.getRespuestaCorrecta();
        respuestasCorrectas.put(indiceActual, correcta);
        return correcta;
    }

    public void siguientePregunta() {
        indiceActual++;
    }

    public int getAciertos() {
        int count = 0;
        for (Boolean b : respuestasCorrectas.values()) if (Boolean.TRUE.equals(b)) count++;
        return count;
    }

    public int getErrores() {
        int count = 0;
        for (Boolean b : respuestasCorrectas.values()) if (!Boolean.TRUE.equals(b)) count++;
        return count;
    }

    public int getIndiceActual() { return indiceActual; }
    public void setIndiceActual(int indice) { this.indiceActual = indice; }
    public int totalPreguntas() { return preguntas.size(); }

    public long tiempoRestanteSegundos(int duracionMinutos) {
        Duration duracion = Duration.between(inicio, LocalDateTime.now());
        long restantes = duracionMinutos * 60 - duracion.getSeconds();
        return Math.max(restantes, 0);
    }

    public String getCategoria() {
        return this.categoria;
    }
}
