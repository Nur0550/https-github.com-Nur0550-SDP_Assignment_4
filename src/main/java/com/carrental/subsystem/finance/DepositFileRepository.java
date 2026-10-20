package com.carrental.subsystem.finance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence gateway for Subsystem B.
 * Directly manages durable file storage on disk (data/deposits.json),
 * surviving application restarts and crashes.
 */
public class DepositFileRepository {

    private final Path filePath;
    private final Map<String, DepositTransaction> inMemoryCache = new ConcurrentHashMap<>();

    public DepositFileRepository(String directoryPath) {
        Path dir = Paths.get(directoryPath != null ? directoryPath : "data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory: " + dir, e);
        }
        this.filePath = dir.resolve("deposits.json");
        reloadFromDisk();
    }

    public synchronized void save(DepositTransaction transaction) {
        inMemoryCache.put(transaction.getTransactionId(), transaction);
        flushToDisk();
    }

    public synchronized Optional<DepositTransaction> findById(String transactionId) {
        if (transactionId == null) return Optional.empty();
        return Optional.ofNullable(inMemoryCache.get(transactionId));
    }

    public synchronized Optional<DepositTransaction> findByReservationId(String reservationId) {
        if (reservationId == null) return Optional.empty();
        return inMemoryCache.values().stream()
                .filter(t -> reservationId.equals(t.getReservationId()))
                .findFirst();
    }

    public synchronized List<DepositTransaction> findAll() {
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
                DepositTransaction tx = parseLine(line);
                if (tx != null) {
                    inMemoryCache.put(tx.getTransactionId(), tx);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload deposits from disk: " + filePath, e);
        }
    }

    public synchronized void clearStore() {
        inMemoryCache.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete deposits file: " + filePath, e);
        }
    }

    private synchronized void flushToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (DepositTransaction t : inMemoryCache.values()) {
                lines.add(serialize(t));
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write deposits to disk: " + filePath, e);
        }
    }

    private String serialize(DepositTransaction t) {
        return String.format(Locale.US,
                "{\"transactionId\":\"%s\",\"reservationId\":\"%s\",\"customerId\":\"%s\",\"depositAmount\":%.2f,\"insurancePremium\":%.2f,\"creditScore\":%d,\"timestamp\":\"%s\",\"status\":\"%s\"}",
                escape(t.getTransactionId()), escape(t.getReservationId()), escape(t.getCustomerId()),
                t.getDepositAmount(), t.getInsurancePremium(), t.getCreditScore(),
                t.getTimestamp().toString(), t.getStatus().name());
    }

    private DepositTransaction parseLine(String json) {
        try {
            String id = extract(json, "transactionId");
            String resId = extract(json, "reservationId");
            String cust = extract(json, "customerId");
            double dep = Double.parseDouble(extract(json, "depositAmount"));
            double ins = Double.parseDouble(extract(json, "insurancePremium"));
            int credit = Integer.parseInt(extract(json, "creditScore"));
            LocalDateTime time = LocalDateTime.parse(extract(json, "timestamp"));
            DepositStatus status = DepositStatus.valueOf(extract(json, "status"));
            return new DepositTransaction(id, resId, cust, dep, ins, credit, time, status);
        } catch (Exception e) {
            System.err.println("Corrupt deposit line ignored: " + json);
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
