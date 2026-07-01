package com.gagastudio.finmate.api.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SeedStore {
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();
    private final Map<String, String> missionKeys = new ConcurrentHashMap<>();
    private Path seedDir;

    public SeedStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        this.seedDir = resolveSeedDir();
        collections.clear();
        missionKeys.clear();
        for (String file : List.of(
                "users.json",
                "onboarding-diagnoses.json",
                "onboarding-sessions.json",
                "mydata-connections.json",
                "privacy-settings.json",
                "personas.json",
                "feature-vectors.json",
                "portfolios.json",
                "mission-templates.json",
                "app-experience.json"
        )) {
            collections.put(file, indexById(readList(file)));
        }
    }

    public void reset() {
        load();
    }

    public Path seedDir() {
        return seedDir;
    }

    public Map<String, Object> get(String collection, String id) {
        Map<String, Object> item = collections.getOrDefault(collection, Map.of()).get(id);
        return item == null ? null : deepCopy(item);
    }

    public Map<String, Object> mutable(String collection, String id) {
        return collections.getOrDefault(collection, Map.of()).get(id);
    }

    public Map<String, Object> firstByField(String collection, String field, Object value) {
        return collections.getOrDefault(collection, Map.of()).values().stream()
                .filter(item -> value.equals(item.get(field)))
                .findFirst()
                .map(this::deepCopy)
                .orElse(null);
    }

    public void rememberMission(String idempotencyKey, String missionId) {
        missionKeys.put(idempotencyKey, missionId);
    }

    public String missionFor(String idempotencyKey) {
        return missionKeys.get(idempotencyKey);
    }

    public <T> T convert(Object source, Class<T> targetType) {
        return objectMapper.convertValue(source, targetType);
    }

    private List<Map<String, Object>> readList(String file) {
        Path path = seedDir.resolve(file);
        try {
            return objectMapper.readValue(path.toFile(), LIST_OF_MAPS);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read seed file: " + path, exception);
        }
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object id = item.get("id");
            if (id != null) {
                index.put(id.toString(), deepCopy(item));
            }
        }
        return new ConcurrentHashMap<>(index);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return objectMapper.convertValue(source, Map.class);
    }

    private Path resolveSeedDir() {
        String override = System.getenv("FINMATE_SEED_DIR");
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        for (Path candidate : List.of(
                Path.of("seed"),
                Path.of("../seed"),
                Path.of("../../seed"),
                Path.of("../../../seed")
        )) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Cannot find FinMate seed directory");
    }
}
