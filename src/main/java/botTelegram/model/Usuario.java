package botTelegram.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "usuario")
public class Usuario implements Serializable {
    @Id
    @Column(name = "telegram_id", length = 50)
    private String telegramId; // guardamos chatId como string

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "admin")
    private boolean admin;

    public Usuario(String telegramId, String nombre, boolean admin) {
        this.telegramId = telegramId;
        this.nombre = nombre;
        this.admin = admin;
    }

    public boolean isAdmin() { return admin; }

}
