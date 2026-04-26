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
    private String telegramId;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "admin")
    private boolean admin;

    @Column(name = "premium")
    private Boolean premium;

    @Column(name = "examen_gratis_usado")
    private Boolean examenGratisUsado;

    public Usuario(String telegramId, String nombre, boolean admin) {
        this.telegramId = telegramId;
        this.nombre = nombre;
        this.admin = admin;
        this.premium = false;
        this.examenGratisUsado = false;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    public boolean isPremium() {
        return Boolean.TRUE.equals(this.premium);
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public boolean isExamenGratisUsado() {
        return Boolean.TRUE.equals(this.examenGratisUsado);
    }

    public void setExamenGratisUsado(boolean examenGratisUsado) {
        this.examenGratisUsado = examenGratisUsado;
    }

    //Constructor vacio requerido por JPA
    public Usuario() {
    }

    public String getNombre() {
        return this.nombre;
    }
}
