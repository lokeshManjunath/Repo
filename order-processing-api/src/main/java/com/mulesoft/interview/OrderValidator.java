package com.mulesoft.interview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderValidator {

    private static final double MAX_ORDER_AMOUNT  = 100_000.0;
    private static final double MIN_ORDER_AMOUNT  = 0.01;
    private static final int    MAX_ITEMS         = 50;
    private static final double EXPRESS_THRESHOLD  = 1_000.0;
    private static final double PRIORITY_THRESHOLD = 10_000.0;
    private static final String ORDER_ID_PREFIX    = "ORD";

    private static final Map<String, List<String>> TRANSITIONS;
    static {
        Map<String, List<String>> t = new HashMap<>();
        t.put("PENDING",    Arrays.asList("CONFIRMED", "CANCELLED"));
        t.put("CONFIRMED",  Arrays.asList("PROCESSING", "CANCELLED"));
        t.put("PROCESSING", Arrays.asList("SHIPPED", "CANCELLED"));
        t.put("SHIPPED",    Collections.singletonList("DELIVERED"));
        t.put("DELIVERED",  Collections.emptyList());
        t.put("CANCELLED",  Collections.emptyList());
        TRANSITIONS = Collections.unmodifiableMap(t);
    }

    public static Map<String, Object> validate(Map<String, Object> orderData) {
        List<String> errors = new ArrayList<>();
        double parsedAmount = 0.0;

        Object customerId = orderData.get("customerId");
        if (customerId == null || customerId.toString().trim().isEmpty()) {
            errors.add("customerId is required and cannot be blank");
        }

        Object itemsObj = orderData.get("items");
        if (itemsObj == null) {
            errors.add("items array is required");
        } else if (itemsObj instanceof List) {
            List<?> items = (List<?>) itemsObj;
            if (items.isEmpty()) {
                errors.add("Order must contain at least one item");
            } else if (items.size() > MAX_ITEMS) {
                errors.add("Order cannot exceed " + MAX_ITEMS + " items");
            }
        } else {
            errors.add("items must be a JSON array");
        }

        Object amountObj = orderData.get("totalAmount");
        if (amountObj == null) {
            errors.add("totalAmount is required");
        } else {
            try {
                parsedAmount = Double.parseDouble(amountObj.toString());
                if (parsedAmount < MIN_ORDER_AMOUNT) {
                    errors.add("totalAmount must be at least " + MIN_ORDER_AMOUNT);
                } else if (parsedAmount > MAX_ORDER_AMOUNT) {
                    errors.add("totalAmount exceeds maximum of $" + String.format("%.2f", MAX_ORDER_AMOUNT));
                }
            } catch (NumberFormatException e) {
                errors.add("totalAmount must be a valid numeric value");
            }
        }

        Object address = orderData.get("shippingAddress");
        if (address instanceof Map) {
            Map<?, ?> addr = (Map<?, ?>) address;
            if (addr.get("street") == null || addr.get("city") == null || addr.get("country") == null) {
                errors.add("shippingAddress must include street, city, and country");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid",        errors.isEmpty());
        result.put("errors",       errors);
        result.put("priority",     determinePriority(parsedAmount));
        result.put("parsedAmount", parsedAmount);
        return result;
    }

    public static String generateOrderId() {
        int suffix = (int) (Math.random() * 9000) + 1000;
        return ORDER_ID_PREFIX + "-" + System.currentTimeMillis() + "-" + suffix;
    }

    public static boolean isValidStatusTransition(String currentStatus, String newStatus) {
        if (currentStatus == null || newStatus == null) return false;
        List<String> allowed = TRANSITIONS.getOrDefault(currentStatus.toUpperCase(), Collections.emptyList());
        return allowed.contains(newStatus.toUpperCase());
    }

    private static String determinePriority(double amount) {
        if (amount >= PRIORITY_THRESHOLD) return "PRIORITY";
        if (amount >= EXPRESS_THRESHOLD)  return "EXPRESS";
        return "STANDARD";
    }
}