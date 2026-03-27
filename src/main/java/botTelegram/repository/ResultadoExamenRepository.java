package botTelegram.repository;

import botTelegram.model.ResultadoExamen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ResultadoExamenRepository extends JpaRepository<ResultadoExamen, Long> {
    List<ResultadoExamen> findByUsuarioId(String usuarioId);
    long countByUsuarioIdAndAprobadoTrue(String usuarioId);
    long countByFechaBetween(LocalDateTime desde, LocalDateTime hasta);

    long countByFechaBetweenAndAprobadoTrue(LocalDateTime inicio, LocalDateTime fin);
}
