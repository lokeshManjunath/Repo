package com.mulesoft.interview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderRepository {

    private static final ConcurrentHashMap<String, Map<String, Object>> STORE =
            new ConcurrentHashMap<>();

    public static Map<String, Object> save(String orderId, Map<String, Object> order) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId cannot be null or blank");
        }
        STORE.put(orderId, order);
        return order;
    }

    public static Map<String, Object> findById(String orderId) {
        if (orderId == null) return null;
        return STORE.get(orderId);
    }

    public static List<Map<String, Object>> findAll() {
        List<Map<String, Object>> orders = new ArrayList<>(STORE.values());
        orders.sort((a, b) -> {
            String tsA = a.get("createdAt") != null ? a.get("createdAt").toString() : "";
            String tsB = b.get("createdAt") != null ? b.get("createdAt").toString() : "";
            return tsB.compareTo(tsA);
        });
        return Collections.unmodifiableList(orders);
    }

    public static boolean exists(String orderId) {
        return orderId != null && STORE.containsKey(orderId);
    }

    public static boolean delete(String orderId) {
        if (orderId == null) return false;
        return STORE.remove(orderId) != null;
    }

    public static int count() { return STORE.size(); }

    public static void clearAll() { STORE.clear(); }
}