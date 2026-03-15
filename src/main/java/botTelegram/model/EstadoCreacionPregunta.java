package botTelegram.model;

import java.util.ArrayList;
import java.util.List;

public class EstadoCreacionPregunta {
    public List<String> categorias;
    private String texto;
    private List<String> opciones = new ArrayList<>();
    private Integer correcta;
    public int paso = 0; // 0=categorias, 1=texto de la preguntas, 2-opciones, 3= respuesta correcta

    public List<String> getCategorias() { return categorias; }
    public void setCategorias(List<String> categorias) { this.categorias = categorias; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public List<String> getOpciones() { return opciones; }
    public void setOpciones(List<String> opciones) { this.opciones = opciones; }
    public Integer getCorrecta() { return correcta; }
    public void setCorrecta(Integer correcta) { this.correcta = correcta; }
    public int getPaso() { return paso; }
    public void setPaso(int paso) { this.paso = paso; }
}
