package botTelegram.model;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "examen_sesion")
public class ExamenSesion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Usuario usuario;

    @OneToMany(mappedBy = "examenSesion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespuestaExamen> respuestas = new ArrayList<>();

    public ExamenSesion() {
        // Constructor vacío requerido por JPA
    }
}
