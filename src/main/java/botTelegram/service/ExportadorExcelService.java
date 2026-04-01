package botTelegram.service;

import botTelegram.model.ResultadoExamen;
import botTelegram.model.Usuario;
import botTelegram.repository.ResultadoExamenRepository;
import botTelegram.repository.UsuarioRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExportadorExcelService {

    private final ResultadoExamenRepository resultadoRepo;
    private final UsuarioRepository usuarioRepo;

    public ExportadorExcelService(ResultadoExamenRepository resultadoRepo, UsuarioRepository usuarioRepo) {
        this.resultadoRepo = resultadoRepo;
        this.usuarioRepo = usuarioRepo;
    }

    public ByteArrayInputStream generarExcelEstadisticas(String usuarioId) throws IOException {
        List<ResultadoExamen> examenes;
        String tituloHoja;

        if (usuarioId == null) {
            examenes = resultadoRepo.findAll();
            tituloHoja = "Reporte Global Admin";
        } else {
            examenes = resultadoRepo.findByUsuarioId(usuarioId);
            tituloHoja = "Mis Estadísticas";
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(tituloHoja);

            // Estilo para la cabecera
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Cabeceras
            Row headerRow = sheet.createRow(0);
            String[] columnas = {"Fecha", "Usuario", "Categoría", "Aciertos", "Total", "Resultado"};
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowIdx = 1;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            for (ResultadoExamen res : examenes) {
                Row row = sheet.createRow(rowIdx++);

                // Buscamos el nombre del usuario para que el Excel sea legible
                Usuario u = usuarioRepo.findById(res.getUsuarioId()).orElse(null);
                String nombre = (u != null) ? u.getNombre() : res.getUsuarioId();

                row.createCell(0).setCellValue(res.getFecha().format(formatter));
                row.createCell(1).setCellValue(nombre);
                row.createCell(2).setCellValue(res.getCategoria() != null ? res.getCategoria().replace("_", " ") : "General");
                row.createCell(3).setCellValue(res.getAciertos());
                row.createCell(4).setCellValue(res.getTotalPreguntas());
                row.createCell(5).setCellValue(res.isAprobado() ? "APROBADO" : "SUSPENSO");
            }

            // Autoajustar columnas
            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}