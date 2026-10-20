package com.carrental.subsystem.dispatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence gateway for Subsystem C.
 * Directly manages durable file storage on disk (data/dispatches.json),
 * surviving application restarts and crashes.
 */
public class DispatchFileRepository {

    private final Path filePath;
    private final Map<String, DispatchRecord> inMemoryCache = new ConcurrentHashMap<>();

    public DispatchFileRepository(String directoryPath) {
        Path dir = Paths.get(directoryPath != null ? directoryPath : "data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory: " + dir, e);
        }
        this.filePath = dir.resolve("dispatches.json");
        reloadFromDisk();
    }

    public synchronized void save(DispatchRecord record) {
        inMemoryCache.put(record.getDispatchId(), record);
        flushToDisk();
    }

    public synchronized Optional<DispatchRecord> findById(String dispatchId) {
        if (dispatchId == null) return Optional.empty();
        return Optional.ofNullable(inMemoryCache.get(dispatchId));
    }

    public synchronized Optional<DispatchRecord> findByReservationId(String reservationId) {
        if (reservationId == null) return Optional.empty();
        return inMemoryCache.values().stream()
                .filter(d -> reservationId.equals(d.getReservationId()))
                .findFirst();
    }

    public synchronized List<DispatchRecord> findAll() {
        return new ArrayList<>(inMemoryCache.values());
    }

    public synchronized void reloadFromDisk() {
        inMemoryCache.clear();
        if (!Files.exists(filePath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                DispatchRecord rec = parseLine(line);
                if (rec != null) {
                    inMemoryCache.put(rec.getDispatchId(), rec);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload dispatches from disk: " + filePath, e);
        }
    }

    public synchronized void clearStore() {
        inMemoryCache.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete dispatches file: " + filePath, e);
        }
    }

    private synchronized void flushToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (DispatchRecord d : inMemoryCache.values()) {
                lines.add(serialize(d));
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write dispatches to disk: " + filePath, e);
        }
    }

    private String serialize(DispatchRecord d) {
        return String.format(Locale.US,
                "{\"dispatchId\":\"%s\",\"reservationId\":\"%s\",\"vehicleId\":\"%s\",\"odometerKm\":%.2f,\"fuelLevelPercent\":%d,\"gpsTrackerId\":\"%s\",\"inspectionPassed\":%b,\"dispatchTime\":\"%s\",\"status\":\"%s\"}",
                escape(d.getDispatchId()), escape(d.getReservationId()), escape(d.getVehicleId()),
                d.getOdometerKm(), d.getFuelLevelPercent(), escape(d.getGpsTrackerId()),
                d.isInspectionPassed(), d.getDispatchTime().toString(), d.getStatus().name());
    }

    private DispatchRecord parseLine(String json) {
        try {
            String id = extract(json, "dispatchId");
            String resId = extract(json, "reservationId");
            String veh = extract(json, "vehicleId");
            double odo = Double.parseDouble(extract(json, "odometerKm"));
            int fuel = Integer.parseInt(extract(json, "fuelLevelPercent"));
            String gps = extract(json, "gpsTrackerId");
            boolean passed = Boolean.parseBoolean(extract(json, "inspectionPassed"));
            LocalDateTime time = LocalDateTime.parse(extract(json, "dispatchTime"));
            DispatchStatus status = DispatchStatus.valueOf(extract(json, "status"));
            return new DispatchRecord(id, resId, veh, odo, fuel, gps, passed, time, status);
        } catch (Exception e) {
            System.err.println("Corrupt dispatch line ignored: " + json);
            return null;
        }
    }

    private String extract(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start != -1) {
            start += pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        pattern = "\"" + key + "\":";
        start = json.indexOf(pattern);
        if (start != -1) {
            start += pattern.length();
            int end = start;
            while (end < json.length() && (Character.isLetterOrDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
                end++;
            }
            return json.substring(start, end);
        }
        throw new IllegalArgumentException("Key not found in json: " + key);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
