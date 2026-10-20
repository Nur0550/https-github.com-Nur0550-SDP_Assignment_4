package com.carrental.orchestration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence gateway for durable chain state tracking.
 * Saves chain progression and compensation state to disk (data/rental_chains.json).
 */
public class RentalChainRepository {

    private final Path filePath;
    private final Map<String, RentalChainOrder> inMemoryCache = new ConcurrentHashMap<>();

    public RentalChainRepository(String directoryPath) {
        Path dir = Paths.get(directoryPath != null ? directoryPath : "data");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory: " + dir, e);
        }
        this.filePath = dir.resolve("rental_chains.json");
        reloadFromDisk();
    }

    public synchronized void save(RentalChainOrder chain) {
        inMemoryCache.put(chain.getChainId(), chain);
        flushToDisk();
    }

    public synchronized Optional<RentalChainOrder> findById(String chainId) {
        if (chainId == null) return Optional.empty();
        return Optional.ofNullable(inMemoryCache.get(chainId));
    }

    public synchronized List<RentalChainOrder> findAll() {
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
                RentalChainOrder chain = parseLine(line);
                if (chain != null) {
                    inMemoryCache.put(chain.getChainId(), chain);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload chains from disk: " + filePath, e);
        }
    }

    public synchronized void clearStore() {
        inMemoryCache.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete chains file: " + filePath, e);
        }
    }

    private synchronized void flushToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (RentalChainOrder c : inMemoryCache.values()) {
                lines.add(serialize(c));
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write chains to disk: " + filePath, e);
        }
    }

    private String serialize(RentalChainOrder c) {
        return String.format(Locale.US,
                "{\"chainId\":\"%s\",\"customerId\":\"%s\",\"vehicleId\":\"%s\",\"reservationId\":\"%s\",\"depositId\":\"%s\",\"dispatchId\":\"%s\",\"status\":\"%s\",\"failureReason\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\"}",
                escape(c.getChainId()), escape(c.getCustomerId()), escape(c.getVehicleId()),
                escape(c.getReservationId()), escape(c.getDepositId()), escape(c.getDispatchId()),
                c.getStatus().name(), escape(c.getFailureReason()),
                c.getCreatedAt().toString(), c.getUpdatedAt().toString());
    }

    private RentalChainOrder parseLine(String json) {
        try {
            String chainId = extract(json, "chainId");
            String custId = extract(json, "customerId");
            String vehId = extract(json, "vehicleId");
            String resId = extract(json, "reservationId");
            String depId = extract(json, "depositId");
            String dispId = extract(json, "dispatchId");
            ChainStepStatus status = ChainStepStatus.valueOf(extract(json, "status"));
            String failureReason = extract(json, "failureReason");
            LocalDateTime createdAt = LocalDateTime.parse(extract(json, "createdAt"));
            LocalDateTime updatedAt = LocalDateTime.parse(extract(json, "updatedAt"));
            return new RentalChainOrder(chainId, custId, vehId, resId, depId, dispId, status, failureReason, createdAt, updatedAt);
        } catch (Exception e) {
            System.err.println("Corrupt chain order line ignored: " + json);
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
        throw new IllegalArgumentException("Key not found in json: " + key);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
