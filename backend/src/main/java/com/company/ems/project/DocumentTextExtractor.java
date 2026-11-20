package com.company.ems.project;

import com.company.ems.config.ConfigService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Best-effort plain text extraction used to index documents for search and,
 * later, AI document Q&A. Unsupported formats are simply not indexed; the
 * supported extension list is configuration-driven, so a company can narrow
 * or extend it without code changes.
 */
@Service
public class DocumentTextExtractor {

    private final ConfigService configService;

    public DocumentTextExtractor(ConfigService configService) {
        this.configService = configService;
    }

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String extension = extension(file.getOriginalFilename());
        if (!supported(extension)) {
            return null;
        }
        long maxBytes = (long) configService.getInt("doc.search.extractMaxMb", 20) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return null;
        }
        int maxChars = Math.max(1000, configService.getInt("doc.search.extractMaxChars", 200000));
        try (InputStream input = file.getInputStream()) {
            String text = switch (extension) {
                case "docx" -> docx(input, maxChars);
                case "xlsx" -> xlsx(input, maxChars);
                default -> new String(input.readAllBytes(), StandardCharsets.UTF_8);
            };
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", " ").trim();
            return cleaned.length() > maxChars ? cleaned.substring(0, maxChars) : cleaned;
        } catch (Exception ex) {
            // Indexing must never block an upload.
            return null;
        }
    }

    private Set<String> supportedExtensions() {
        return Arrays.stream(configService.getString("doc.search.contentExtensions",
                                "txt,md,csv,json,xml,html,htm,log,docx,xlsx").split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private boolean supported(String extension) {
        return StringUtils.hasText(extension) && supportedExtensions().contains(extension);
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String docx(InputStream input, int maxChars) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(input)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                append(text, paragraph.getText(), maxChars);
                if (text.length() >= maxChars) {
                    break;
                }
            }
        }
        return text.toString();
    }

    private String xlsx(InputStream input, int maxChars) throws Exception {
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (StringUtils.hasText(value)) {
                            if (line.length() > 0) {
                                line.append(' ');
                            }
                            line.append(value);
                        }
                    }
                    append(text, line.toString(), maxChars);
                    if (text.length() >= maxChars) {
                        return text.toString();
                    }
                }
            }
        }
        return text.toString();
    }

    private void append(StringBuilder text, String value, int maxChars) {
        if (!StringUtils.hasText(value) || text.length() >= maxChars) {
            return;
        }
        text.append(value).append('\n');
    }
}
