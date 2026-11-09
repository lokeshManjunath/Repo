package com.mulesoft.interview;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderUtils {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    private static final List<String> REVENUE_STATUSES = List.of("CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED");

    public static String getCurrentTimestamp() {
        return ZonedDateTime.now().format(ISO_FMT);
    }

    public static Map<String, Object> buildAuditEntry(String orderId, String action, String performedBy) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("auditId",       UUID.randomUUID().toString());
        entry.put("orderId",       orderId != null ? orderId : "UNKNOWN");
        entry.put("action",        action != null ? action : "UNKNOWN");
        entry.put("performedBy",   performedBy != null ? performedBy : "system");
        entry.put("timestamp",     getCurrentTimestamp());
        entry.put("correlationId", UUID.randomUUID().toString());
        return entry;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> calculateOrderMetrics(List<Object> orders) {
        long total      = orders.size();
        long pending    = countByStatus(orders, "PENDING");
        long confirmed  = countByStatus(orders, "CONFIRMED");
        long processing = countByStatus(orders, "PROCESSING");
        long shipped    = countByStatus(orders, "SHIPPED");
        long delivered  = countByStatus(orders, "DELIVERED");
        long cancelled  = countByStatus(orders, "CANCELLED");

        double totalRevenue = orders.stream()
            .filter(o -> o instanceof Map)
            .map(o -> (Map<String, Object>) o)
            .filter(o -> REVENUE_STATUSES.contains(String.valueOf(o.getOrDefault("status", ""))))
            .mapToDouble(o -> {
                Object amt = o.get("totalAmount");
                try { return amt != null ? Double.parseDouble(amt.toString()) : 0.0; }
                catch (NumberFormatException e) { return 0.0; }
            }).sum();

        double avg = total > 0 ? totalRevenue / total : 0.0;

        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("PENDING",    pending);
        byStatus.put("CONFIRMED",  confirmed);
        byStatus.put("PROCESSING", processing);
        byStatus.put("SHIPPED",    shipped);
        byStatus.put("DELIVERED",  delivered);
        byStatus.put("CANCELLED",  cancelled);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalOrders",   total);
        metrics.put("byStatus",      byStatus);
        metrics.put("totalRevenue",  round2(totalRevenue));
        metrics.put("avgOrderValue", round2(avg));
        metrics.put("generatedAt",   getCurrentTimestamp());
        return metrics;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "[redacted]";
        int at = email.indexOf('@');
        if (at <= 1) return "****" + email.substring(at > 0 ? at : 0);
        return email.charAt(0) + "****" + email.substring(at);
    }

    @SuppressWarnings("unchecked")
    private static long countByStatus(List<Object> orders, String status) {
        return orders.stream()
            .filter(o -> o instanceof Map)
            .map(o -> (Map<String, Object>) o)
            .filter(o -> status.equalsIgnoreCase(String.valueOf(o.getOrDefault("status", ""))))
            .count();
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}