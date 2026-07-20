package com.campkin.service;

import com.campkin.api.ApiModels.ImportResult;
import com.campkin.domain.*;
import com.campkin.repo.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

@Service
@RequiredArgsConstructor
public class ExcelImportService {
    private static final Pattern EMBEDDED_DATE = Pattern.compile("\\s*-\\s*(\\d{4}-\\d{2}-\\d{2})\\s*$");
    private final CampRepository camps;
    private final CamperRepository campers;
    private final PreferenceRepository preferences;
    private final NameMatcher matcher;

    @Transactional
    public ImportResult importFile(UUID campId, MultipartFile file) throws IOException {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Map<String, String>> rows = readRows(file);
        if (rows.isEmpty()) throw new IllegalArgumentException("The file has no camper rows");

        List<String> warnings = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int boys = 0, girls = 0, unknown = 0;

        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            String combined = first(row, "name", "camper");
            String name = first(row, "camper name");
            if (name.isBlank()) name = stripEmbeddedDate(combined);
            if (name.isBlank()) continue;

            String normalized = NameMatcher.normalize(name);
            if (!names.add(normalized)) {
                warnings.add("Duplicate name skipped: " + name);
                continue;
            }

            LocalDate birthdate = parseDate(first(row, "date of birth", "birthdate"));
            if (birthdate == null) birthdate = embeddedDate(combined);
            if (birthdate == null) throw new IllegalArgumentException("Missing birthdate on row " + (index + 2) + ": " + name);

            Domain.Gender gender = parseGender(first(row, "gender"));
            if (gender == Domain.Gender.MALE) boys++;
            else if (gender == Domain.Gender.FEMALE) girls++;
            else {
                unknown++;
                warnings.add("Gender needs review: " + name);
            }

            Camper camper = new Camper();
            camper.setCamp(camp);
            camper.setName(name.trim().replaceAll("\\s+", " "));
            camper.setNormalizedName(normalized);
            camper.setGender(gender);
            camper.setBirthdate(birthdate);
            campers.save(camper);

            LinkedHashSet<String> requested = new LinkedHashSet<>();
            for (String column : List.of("roommate preferences", "preferences", "room mates", "manual room mates")) {
                for (String raw : splitPreferences(first(row, column))) {
                    String clean = stripEmbeddedDate(raw).trim().replaceAll("\\s+", " ");
                    if (!clean.isBlank()) requested.add(clean);
                }
            }
            for (String raw : requested) {
                Preference preference = new Preference();
                preference.setCamper(camper);
                preference.setRawName(raw);
                preference.setStatus(Domain.PreferenceStatus.UNRESOLVED);
                preferences.save(preference);
            }
        }

        matcher.resolve(campId);
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        double average = all.stream().mapToInt(c -> c.ageOn(camp.getStartDate())).average().orElse(0);
        return new ImportResult(boys + girls + unknown, boys, girls, unknown, Math.round(average * 10) / 10d, warnings);
    }

    private List<Map<String, String>> readRows(MultipartFile file) throws IOException {
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return csvRows(file.getInputStream());
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) throw new IllegalArgumentException("Choose a .csv, .xlsx, or .xls file");
        return workbookRows(file.getInputStream());
    }

    private List<Map<String, String>> workbookRows(InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) return List.of();
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(normalizeHeader(formatter.formatCellValue(header.getCell(i))));
            List<Map<String, String>> result = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> values = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String value = cell != null && DateUtil.isCellDateFormatted(cell)
                            ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                            : formatter.formatCellValue(cell);
                    values.put(headers.get(c), value == null ? "" : value.trim());
                }
                result.add(values);
            }
            return result;
        }
    }

    private List<Map<String, String>> csvRows(InputStream input) throws IOException {
        String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<List<String>> records = parseCsv(text);
        if (records.size() < 2) return List.of();
        List<String> headers = records.getFirst().stream().map(this::normalizeHeader).toList();
        List<Map<String, String>> result = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> record = records.get(r);
            if (record.stream().allMatch(String::isBlank)) continue;
            Map<String, String> values = new HashMap<>();
            for (int c = 0; c < headers.size(); c++) values.put(headers.get(c), c < record.size() ? record.get(c).trim() : "");
            result.add(values);
        }
        return result;
    }

    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (ch == ',' && !quoted) { row.add(field.toString()); field.setLength(0); }
            else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>();
            } else field.append(ch);
        }
        if (!field.isEmpty() || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) { String value = row.getOrDefault(key, "").trim(); if (!value.isBlank()) return value; }
        return "";
    }

    private String normalizeHeader(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private List<String> splitPreferences(String value) { return value == null ? List.of() : Arrays.stream(value.split("[,;/\\r\\n]+")).map(String::trim).filter(v -> !v.isBlank()).toList(); }
    private String stripEmbeddedDate(String value) { return EMBEDDED_DATE.matcher(value == null ? "" : value).replaceFirst("").trim(); }
    private LocalDate embeddedDate(String value) { Matcher m = EMBEDDED_DATE.matcher(value == null ? "" : value); return m.find() ? LocalDate.parse(m.group(1)) : null; }
    private Domain.Gender parseGender(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (Set.of("m", "male", "boy").contains(normalized)) return Domain.Gender.MALE;
        if (Set.of("f", "female", "girl").contains(normalized)) return Domain.Gender.FEMALE;
        return Domain.Gender.UNKNOWN;
    }
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"))) {
            try { return LocalDate.parse(value.trim(), formatter); } catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("Invalid birthdate: " + value);
    }
}
