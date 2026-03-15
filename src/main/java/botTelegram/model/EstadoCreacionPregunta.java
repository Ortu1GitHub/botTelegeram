package botTelegram.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class EstadoCreacionPregunta {
    public List<String> categorias;
    private String texto;
    private List<String> opciones = new ArrayList<>();
    private Integer correcta;
    public int paso = 0; // 0=categorias, 1=texto de la preguntas, 2-opciones, 3= respuesta correcta

}
