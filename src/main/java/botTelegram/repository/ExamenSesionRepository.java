package botTelegram.repository;

import botTelegram.model.ExamenSesion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamenSesionRepository extends JpaRepository<ExamenSesion, Long> {
//    long countByUsuario(Usuario usuario);
//    long countByUsuarioAndAprobado(Usuario usuario, boolean aprobado);
//    long countByFechaBetween(LocalDateTime inicio, LocalDateTime fin);
}
