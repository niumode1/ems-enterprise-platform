package com.company.ems.roster;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads CSV/XLSX/XLS rosters into header + raw row maps. Format-independent so
 * the AI mapping and deterministic dictionary can be applied uniformly.
 */
@Component
public class RosterFileParser {

    public record SheetData(List<String> headers, List<Map<String, String>> rows) {
    }

    public SheetData read(byte[] bytes, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        List<List<String>> table;
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            table = parseCsv(bytes);
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            table = parseWorkbook(bytes);
        } else {
            throw new IllegalArgumentException("仅支持 CSV、XLSX、XLS 花名册");
        }
        if (table.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        List<String> headers = table.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int c = 0; c < headers.size(); c++) {
                String header = headers.get(c);
                if (header == null || header.isBlank()) {
                    continue;
                }
                String value = c < cells.size() ? cells.get(c) : null;
                if (value != null && !value.isBlank()) {
                    row.put(header, value.trim());
                    hasValue = true;
                }
            }
            if (hasValue) {
                rows.add(row);
            }
        }
        return new SheetData(headers, rows);
    }

    private List<List<String>> parseWorkbook(byte[] bytes) {
        List<List<String>> table = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                return table;
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int lastColumn = 0;
            for (Row row : sheet) {
                lastColumn = Math.max(lastColumn, row.getLastCellNum());
                List<String> values = new ArrayList<>();
                for (int c = 0; c < lastColumn; c++) {
                    Cell cell = row.getCell(c);
                    values.add(formatCell(cell, formatter));
                }
                table.add(values);
            }
            // trim trailing all-empty rows
            while (!table.isEmpty() && table.get(table.size() - 1).stream().allMatch(String::isBlank)) {
                table.remove(table.size() - 1);
            }
            return table;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法读取 Excel 文件：" + ex.getMessage(), ex);
        }
    }

    private String formatCell(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return formatter.formatCellValue(cell).trim();
    }

    private List<List<String>> parseCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = text.contains("\t") && !text.contains(",") ? '\t' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            rows.add(row);
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).stream().allMatch(String::isEmpty)) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }
}
