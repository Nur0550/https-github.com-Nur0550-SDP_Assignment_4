package com.carrental.subsystem.reservation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence gateway for Subsystem A.
 * Directly manages durable file storage on disk (data/reservations.json),
 * surviving application restarts and crashes.
 */
public class ReservationFileRepository {

    private final Path filePath;
    private final Map<String, VehicleReservation> inMemoryCache = new ConcurrentHashMap<>();

    public ReservationFileRepository(String directoryPath) {
        Path dir = Paths.get(directoryPath != null ? directoryPath : "data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory: " + dir, e);
        }
        this.filePath = dir.resolve("reservations.json");
        reloadFromDisk();
    }

    public synchronized void save(VehicleReservation reservation) {
        inMemoryCache.put(reservation.getReservationId(), reservation);
        flushToDisk();
    }

    public synchronized Optional<VehicleReservation> findById(String reservationId) {
        if (reservationId == null) return Optional.empty();
        return Optional.ofNullable(inMemoryCache.get(reservationId));
    }

    public synchronized List<VehicleReservation> findAll() {
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
                VehicleReservation res = parseLine(line);
                if (res != null) {
                    inMemoryCache.put(res.getReservationId(), res);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload reservations from disk: " + filePath, e);
        }
    }

    public synchronized void clearStore() {
        inMemoryCache.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete reservations file: " + filePath, e);
        }
    }

    private synchronized void flushToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (VehicleReservation r : inMemoryCache.values()) {
                lines.add(serialize(r));
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write reservations to disk: " + filePath, e);
        }
    }

    private String serialize(VehicleReservation r) {
        return String.format(Locale.US,
                "{\"reservationId\":\"%s\",\"customerId\":\"%s\",\"vehicleId\":\"%s\",\"rentalDays\":%d,\"dailyRate\":%.2f,\"totalCost\":%.2f,\"createdAt\":\"%s\",\"status\":\"%s\"}",
                escape(r.getReservationId()), escape(r.getCustomerId()), escape(r.getVehicleId()),
                r.getRentalDays(), r.getDailyRate(), r.getTotalCost(),
                r.getCreatedAt().toString(), r.getStatus().name());
    }

    private VehicleReservation parseLine(String json) {
        try {
            String id = extract(json, "reservationId");
            String cust = extract(json, "customerId");
            String veh = extract(json, "vehicleId");
            int days = Integer.parseInt(extract(json, "rentalDays"));
            double rate = Double.parseDouble(extract(json, "dailyRate"));
            double cost = Double.parseDouble(extract(json, "totalCost"));
            LocalDateTime time = LocalDateTime.parse(extract(json, "createdAt"));
            ReservationStatus status = ReservationStatus.valueOf(extract(json, "status"));
            return new VehicleReservation(id, cust, veh, days, rate, cost, time, status);
        } catch (Exception e) {
            System.err.println("Corrupt reservation line ignored: " + json);
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
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
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
