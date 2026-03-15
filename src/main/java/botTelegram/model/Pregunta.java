package botTelegram.model;


// ...existing code...



import java.util.List;

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

    public String getTexto() { return texto; }


    public List<String> getOpciones() { return opciones; }

    public int getRespuestaCorrecta() { return respuestaCorrecta; }
}
