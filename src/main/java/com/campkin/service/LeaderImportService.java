package com.campkin.service;

import com.campkin.api.ApiModels.LeaderImportResult;
import com.campkin.api.ImportValidationException;
import com.campkin.domain.Camp;
import com.campkin.domain.Domain;
import com.campkin.domain.Leader;
import com.campkin.repo.CampRepository;
import com.campkin.repo.LeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaderImportService {
    private static final Set<String> FEMALE_NAMES = Set.of(
        "abigail", "amal", "amira", "aya", "caroline", "caren", "christina", "dalia", "diana", "eman",
        "farah", "febe", "georgina", "hagar", "hana", "hannah", "heba", "helena", "holy", "irene",
        "jenny", "jessica", "joanna", "joyce", "karin", "linda", "maggie", "marina", "marita", "mariam",
        "marie", "martina", "mary", "maya", "merna", "mira", "miray", "monica", "nada", "nadin",
        "nadine", "nancy", "natalie", "nathalie", "nataly", "nermine", "nour", "rachel", "rana",
        "reem", "rita", "sally", "sandra", "sara", "sarah", "sherry", "veronia", "veronica", "yasmin"
    );
    private static final Set<String> MALE_NAMES = Set.of(
        "adam", "alan", "albert", "amgad", "amir", "andrew", "andy", "anthony", "bassem", "bishoy",
        "christopher", "david", "emad", "fady", "george", "girgis", "hany", "ibrahim", "john", "jony",
        "joseph", "karim", "karl", "kerolos", "khaled", "mark", "martin", "matthew", "michael", "mina",
        "mohamed", "nabil", "peter", "philopater", "ramy", "robert", "romany", "samir", "sameh", "samuel",
        "sherif", "steven", "tony", "wael", "youssef"
    );

    private final CampRepository camps;
    private final LeaderRepository leaders;
    private final ExcelImportService spreadsheets;

    private record Row(String name, String normalized, Domain.Gender gender, boolean genderAssumed) {}

    @Transactional
    public LeaderImportResult importFile(UUID campId, MultipartFile file) throws IOException {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Map<String, String>> rows = spreadsheets.readRows(file);
        if (rows.isEmpty()) throw new IllegalArgumentException("The file has no leader rows");
        List<String> errors = new ArrayList<>();
        List<Row> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Leader> current = leaders.findByCampIdOrderByName(campId);
        long girls = current.stream().filter(l -> l.getGender() == Domain.Gender.FEMALE).count();
        long boys = current.stream().filter(l -> l.getGender() == Domain.Gender.MALE).count();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String name = first(row, "leader name", "name", "leader", "servant name", "servant").trim().replaceAll("\\s+", " ");
            String normalized = NameMatcher.normalize(name);
            String rawGender = first(row, "gender", "sex");
            Domain.Gender gender = spreadsheets.parseGender(rawGender);
            boolean assumed = rawGender.isBlank();
            int number = i + 2;
            boolean unique = !name.isBlank() && seen.add(normalized);
            if (name.isBlank()) errors.add("Row " + number + ": leader name is missing");
            else if (!unique) errors.add("Row " + number + " (" + name + "): duplicate leader name");
            if (!rawGender.isBlank() && gender == Domain.Gender.UNKNOWN)
                errors.add("Row " + number + " (" + (name.isBlank() ? "unnamed leader" : name) + "): gender must be Male or Female, or left empty for an assumption");
            if (unique && (rawGender.isBlank() || gender != Domain.Gender.UNKNOWN)) {
                Leader existing = leaders.findByCampIdAndNormalizedName(campId, normalized).orElse(null);
                if (rawGender.isBlank() && existing != null && !existing.isGenderAssumed()) {
                    gender = existing.getGender();
                    assumed = false;
                } else if (rawGender.isBlank()) gender = inferGender(name, girls, boys);
                if (gender == Domain.Gender.FEMALE) girls++; else boys++;
                valid.add(new Row(name, normalized, gender, assumed));
            }
        }
        if (!errors.isEmpty()) throw new ImportValidationException(errors);

        int added = 0, updated = 0;
        for (Row row : valid) {
            Leader leader = leaders.findByCampIdAndNormalizedName(campId, row.normalized()).orElse(null);
            if (leader == null) {
                leader = new Leader();
                leader.setCamp(camp);
                leader.setNormalizedName(row.normalized());
                added++;
            } else updated++;
            leader.setName(row.name());
            leader.setGender(row.gender());
            leader.setGenderAssumed(row.genderAssumed());
            leaders.save(leader);
        }
        return new LeaderImportResult(valid.size(), added, updated, List.of());
    }

    private Domain.Gender inferGender(String fullName, long girls, long boys) {
        String firstName = NameMatcher.normalize(fullName).split(" ")[0].toLowerCase(Locale.ROOT);
        if (FEMALE_NAMES.contains(firstName)) return Domain.Gender.FEMALE;
        if (MALE_NAMES.contains(firstName)) return Domain.Gender.MALE;
        return girls <= boys ? Domain.Gender.FEMALE : Domain.Gender.MALE;
    }

    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.getOrDefault(key, "").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
