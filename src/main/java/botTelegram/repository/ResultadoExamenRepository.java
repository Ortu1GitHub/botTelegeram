package botTelegram.repository;

import botTelegram.model.ResultadoExamen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultadoExamenRepository extends JpaRepository<ResultadoExamen, Long> {
    List<ResultadoExamen> findByUsuarioId(String usuarioId);
}
