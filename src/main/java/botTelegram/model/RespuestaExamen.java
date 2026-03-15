package botTelegram.model;

import jakarta.persistence.*;

@Entity
@Table(name = "respuesta_examen")
public class RespuestaExamen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private ExamenSesion examenSesion;

    @Column(columnDefinition = "TEXT")
    private String preguntaTexto;

    public RespuestaExamen() {}


}
