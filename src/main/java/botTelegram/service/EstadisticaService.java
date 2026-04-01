package botTelegram.service;

import botTelegram.model.Examen;
import botTelegram.model.ResultadoExamen;
import botTelegram.model.Usuario;
import botTelegram.repository.ResultadoExamenRepository;
import botTelegram.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadisticaService {

    private final ResultadoExamenRepository repo;
    private final UsuarioRepository usuarioRepo;

    public EstadisticaService(ResultadoExamenRepository repo, UsuarioRepository usuarioRepo) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
    }

    public void guardarResultado(String usuarioId, Examen examen, int umbral) {
        boolean aprobado = examen.getAciertos() >= umbral;
        ResultadoExamen r = new ResultadoExamen(
                usuarioId,
                examen.getAciertos(),
                examen.totalPreguntas(),
                aprobado,
                examen.getCategoria()
        );
        repo.save(r);
    }

    public String generarEstadisticas(String usuarioId) {
        // Buscamos al usuario para personalizar el saludo
        Usuario usuario = usuarioRepo.findById(usuarioId).orElse(null);

        // Si el usuario fuera admin, podrías lanzar un mensaje de error o
        // simplemente procesar sus propios datos (aunque no verá el botón).
        String nombreUsuario = (usuario != null) ? usuario.getNombre() : "Usuario";

        // FILTRO CRÍTICO: Obtenemos SOLO los exámenes de este usuario específico
        List<ResultadoExamen> examenes = repo.findByUsuarioId(usuarioId);
        long totalExamenes = examenes.size();

        if (examenes.isEmpty()) {
            return "📊 *MIS ESTADÍSTICAS*\n\n" + nombreUsuario + ", aún no has realizado ningún examen. ¡Ánimo con el primero! 🚗";
        }

        // 1. Cálculos de Hoy (Filtrado sobre la lista del usuario)
        LocalDate hoy = LocalDate.now();
        List<ResultadoExamen> examenesHoy = examenes.stream()
                .filter(r -> r.getFecha().toLocalDate().isEqual(hoy))
                .toList();

        // 2. Media de Errores Total (Solo del usuario)
        double mediaErrores = 0;
        if (totalExamenes > 0) {
            int sumaErrores = examenes.stream()
                    .mapToInt(r -> r.getTotalPreguntas() - r.getAciertos())
                    .sum();
            mediaErrores = (double) sumaErrores / totalExamenes;
        }

        // 3. Lógica de Categoría más examinada (Solo del usuario)
        Map<String, Long> frecuencias = examenes.stream()
                .filter(r -> r.getCategoria() != null)
                .collect(Collectors.groupingBy(ResultadoExamen::getCategoria, Collectors.counting()));

        String categoriaGanadora = "N/A";
        if (!frecuencias.isEmpty()) {
            long maxExamenes = Collections.max(frecuencias.values());
            long empates = frecuencias.values().stream().filter(v -> v == maxExamenes).count();
            if (empates > 1) {
                categoriaGanadora = "VARIAS";
            } else {
                categoriaGanadora = frecuencias.entrySet().stream()
                        .filter(e -> e.getValue() == maxExamenes)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("N/A");
            }
        }

        // Construcción del mensaje de respuesta
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *MIS ESTADÍSTICAS*\n\n");
        sb.append("👤 *Usuario:* ").append(nombreUsuario).append("\n");
        sb.append("📅 *Histórico Global:*\n");
        sb.append("   • Exámenes realizados: ").append(totalExamenes).append("\n");

        // Desglose Histórico por Categoría (Usa tu metodo auxiliar appendDesglose)
        sb.append("   • ✅ Aprobados:\n");
        appendDesglose(sb, examenes, true);
        sb.append("   • ❌ Suspendidos:\n");
        appendDesglose(sb, examenes, false);

        sb.append("\n🕒 *Actividad de Hoy:*\n");
        if (examenesHoy.isEmpty()) {
            sb.append("   _No has realizado exámenes hoy._\n");
        } else {
            sb.append("   • ✅ Aprobados:\n");
            appendDesglose(sb, examenesHoy, true);
            sb.append("   • ❌ Suspendidos:\n");
            appendDesglose(sb, examenesHoy, false);
        }

        sb.append("\n📈 *Rendimiento:*\n");
        sb.append("   • Media de errores: ").append(String.format("%.2f", mediaErrores)).append(" por examen\n");
        sb.append("   • Categoría más practicada: ").append(categoriaGanadora.toUpperCase()).append("\n");

        // Tasa de éxito final
        long totalAprobados = examenes.stream().filter(ResultadoExamen::isAprobado).count();
        double porcentaje = ((double) totalAprobados / totalExamenes) * 100;
        sb.append("\n🎯 *Tasa de éxito total:* ").append(String.format("%.1f", porcentaje)).append("%");

        return sb.toString();
    }

    //Metodo auxiliar para añadir el desglose por categorías al StringBuilder

    private void appendDesglose(StringBuilder sb, List<ResultadoExamen> lista, boolean buscarAprobados) {
        Map<String, Long> porCat = lista.stream()
                .filter(r -> r.isAprobado() == buscarAprobados)
                .collect(Collectors.groupingBy(
                        r -> r.getCategoria() != null ? r.getCategoria().toUpperCase() : "GENERAL",
                        Collectors.counting()
                ));

        if (porCat.isEmpty()) {
            sb.append("       - Ninguno\n");
        } else {
            porCat.forEach((cat, count) ->
                    sb.append("       - ").append(cat).append(": ").append(count).append("\n")
            );
        }
    }

    public String generarEstadisticasGlobales() {
        List<ResultadoExamen> todos = repo.findAll();
        if (todos.isEmpty()) return "General: No hay exámenes registrados aún.";

        long total = todos.size();
        long aprobados = todos.stream().filter(ResultadoExamen::isAprobado).count();
        long suspensos = total - aprobados;
        // --- NUEVA LÓGICA: Encontrar la categoría más examinada ---
        // 1. Contamos cuántas veces aparece cada categoría
        Map<String, Long> conteoCategorias = todos.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategoria() != null ? r.getCategoria().replace("_", " ").toUpperCase() : "GENERAL",
                        Collectors.counting()
                ));

        // 2. Encontramos el número máximo de apariciones
        long maxApariciones = conteoCategorias.values().stream()
                .max(Long::compare)
                .orElse(0L);

        // 3. Filtramos todas las categorías que tengan ese número máximo
        String categoriasMasPopulares = conteoCategorias.entrySet().stream()
                .filter(entry -> entry.getValue() == maxApariciones)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));

        // Si no hay datos, manejamos el valor por defecto
        if (maxApariciones == 0) categoriasMasPopulares = "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("📊 sESTADÍSTICAS GLOBALES DEL SISTEMA\n");
        sb.append("--------------------------------------------\n");
        sb.append("✅ Total Aprobados: ").append(aprobados).append("\n");
        sb.append("❌ Total Suspensos: ").append(suspensos).append("\n");
        sb.append("⭐ Categoría más popular: ").append(categoriasMasPopulares).append("\n");
        sb.append("📈 Tasa de éxito: ").append(String.format("%.1f%%", ((double)aprobados/total)*100)).append("\n\n");

        sb.append("👥 DESGLOSE POR USUARIO:\n");

        // Agrupamos: Usuario -> Categoría -> Lista de resultados
        Map<String, Map<String, List<ResultadoExamen>>> agrupado = todos.stream()
                .collect(Collectors.groupingBy(
                        r -> {
                            Usuario usu = usuarioRepo.findById(r.getUsuarioId()).orElse(null);
                            return (usu != null) ? usu.getNombre() : "ID: " + r.getUsuarioId();
                        },
                        Collectors.groupingBy(r -> r.getCategoria() != null ? r.getCategoria().toUpperCase() : "GENERAL")
                ));

        agrupado.forEach((nombre, categorias) -> {
            sb.append("\n👤 ").append(nombre).append("*\n");
            categorias.forEach((cat, examenes) -> {
                long ap = examenes.stream().filter(ResultadoExamen::isAprobado).count();
                long sus = examenes.size() - ap;
                sb.append("   • ").append(cat).append(": ").append(ap).append("✅ / ").append(sus).append("❌\n");
            });
        });

        return sb.toString();
    }
}