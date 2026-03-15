package botTelegram.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
public class ResultadoExamen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String usuarioId; // chatId en String
    private int aciertos;
    private int totalPreguntas;
    private boolean aprobado;
    private LocalDateTime fecha;

    public ResultadoExamen(String usuarioId, int aciertos, int totalPreguntas, boolean aprobado) {
        this.usuarioId = usuarioId;
        this.aciertos = aciertos;
        this.totalPreguntas = totalPreguntas;
        this.aprobado = aprobado;
        this.fecha = LocalDateTime.now();
    }

    public ResultadoExamen() {
    }
}
