package botTelegram.repository;

import botTelegram.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    /**
     * Marca el examen gratuito como usado de forma atómica.
     * Solo actualiza si el flag está a null o false, evitando condiciones de carrera.
     * @return 1 si se marcó correctamente, 0 si ya estaba marcado (otro hilo se adelantó).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Usuario u SET u.examenGratisUsado = true WHERE u.telegramId = :id AND (u.examenGratisUsado IS NULL OR u.examenGratisUsado = false)")
    int marcarExamenGratisUsado(@Param("id") String id);
}
