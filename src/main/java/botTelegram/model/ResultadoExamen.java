package botTelegram.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    public ResultadoExamen() {}

    public ResultadoExamen(String usuarioId, int aciertos, int totalPreguntas, boolean aprobado) {
        this.usuarioId = usuarioId;
        this.aciertos = aciertos;
        this.totalPreguntas = totalPreguntas;
        this.aprobado = aprobado;
        this.fecha = LocalDateTime.now();
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public int getAciertos() {
        return aciertos;
    }

    public void setAciertos(int aciertos) {
        this.aciertos = aciertos;
    }

    public int getTotalPreguntas() {
        return totalPreguntas;
    }

    public void setTotalPreguntas(int totalPreguntas) {
        this.totalPreguntas = totalPreguntas;
    }

    public boolean isAprobado() {
        return aprobado;
    }

    public void setAprobado(boolean aprobado) {
        this.aprobado = aprobado;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
