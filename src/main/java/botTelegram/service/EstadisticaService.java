package botTelegram.service;

import botTelegram.model.Examen;
import botTelegram.model.ResultadoExamen;
import botTelegram.repository.ResultadoExamenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class EstadisticaService {

    private final ResultadoExamenRepository repo;

    public EstadisticaService(ResultadoExamenRepository repo) {
        this.repo = repo;
    }

    public void guardarResultado(String usuarioId, Examen examen, int umbral) {
        boolean aprobado = examen.aprobado(umbral);
        ResultadoExamen r = new ResultadoExamen(
                usuarioId,
                examen.getAciertos(),
                examen.totalPreguntas(),
                aprobado
        );
        repo.save(r);
    }

    public String generarEstadisticas(String usuarioId) {
        long totalExamenesUsuario = repo.findByUsuarioId(usuarioId).size();
        long aprobadosUsuario = repo.countByUsuarioIdAndAprobadoTrue(usuarioId);

        LocalDate hoy = LocalDate.now();
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.atTime(LocalTime.MAX);

        // Consultas para el total global de hoy
        long examenesHoy = repo.countByFechaBetween(inicio, fin);
        long aprobadosHoy = repo.countByFechaBetweenAndAprobadoTrue(inicio, fin);

        return "📊 Tus estadísticas:\n" +
                "📝 Exámenes realizados: " + totalExamenesUsuario + "\n" +
                "✅ Exámenes aprobados: " + aprobadosUsuario + "\n\n" +
               // "📅 Hoy se realizaron en total: " + examenesHoy + " exámenes";
                "📅 Hoy se realizaron: " + examenesHoy + " exámenes (" + aprobadosHoy + " aprobados)";
    }
}
