package botTelegram.model;

import lombok.Getter;

import java.util.List;

@Getter
public class Pregunta {
    private String texto;
    private List<String> opciones;
    private int respuestaCorrecta;

    public Pregunta() {}

    public Pregunta(String texto, List<String> opciones, int respuestaCorrecta) {
        this.texto = texto;
        this.opciones = opciones;
        this.respuestaCorrecta = respuestaCorrecta;
    }


}
