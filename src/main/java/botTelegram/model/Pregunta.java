package botTelegram.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.util.List;

@Getter
public class Pregunta {
    private String texto;
    private List<String> opciones;
    private int respuestaCorrecta;
    private String imagenUrl;

    @JsonIgnore
    private int numero;

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public Pregunta(String texto, List<String> opciones, int respuestaCorrecta) {
        this.texto = texto;
        this.opciones = opciones;
        this.respuestaCorrecta = respuestaCorrecta;
    }

    // Jackson necesita obligatoriamente este constructor vacío
    public Pregunta() {
    }
}
