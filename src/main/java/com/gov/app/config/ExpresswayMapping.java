package com.gov.app.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExpresswayMapping {

    public record Expressway(String code, String name, List<Long> cameraIds) {}

    private static final Map<String, Expressway> BY_CODE = new LinkedHashMap<>();

    static {
        register("BKE", "Bukit Timah Expressway",
                List.of(2701L,2702L,2703L,2704L,2705L,2706L,2707L,2708L));
        register("PIE", "Pan Island Expressway",
                List.of(4701L,4702L,4704L,4705L,4706L,4707L,4708L,4709L,4710L,4712L,4713L,4714L,4716L));
        register("CTE", "Central Expressway",
                List.of(1701L,1702L,1703L,1704L,1705L,1706L,1707L,1709L,1711L));
        register("ECP", "East Coast Parkway",
                List.of(1001L,1002L,1003L,1004L,1501L,1502L,1503L,1504L,1505L));
        register("TPE", "Tampines Expressway",
                List.of(7791L,7793L,7794L,7795L,7796L,7797L,7798L));
        register("SLE", "Seletar Expressway",
                List.of(9701L,9702L,9703L,9704L,9705L,9706L));
        register("KJE", "Kranji Expressway",
                List.of(8701L,8702L,8704L,8706L));
        register("AYE", "Ayer Rajah Expressway",
                List.of(1801L,1802L,4798L,4799L));
        register("KPE", "Kallang-Paya Lebar Expressway",
                List.of(3793L,3795L,3796L,3797L,3798L,5794L,5795L,5797L,5798L,5799L));
    }

    private static void register(String code, String name, List<Long> cameraIds) {
        BY_CODE.put(code, new Expressway(code, name, cameraIds));
    }

    public Expressway getByCode(String code) {
        return BY_CODE.get(code.toUpperCase());
    }

    public Map<String, Expressway> getAll() {
        return Collections.unmodifiableMap(BY_CODE);
    }

    public String resolveExpressway(Long cameraId) {
        for (var entry : BY_CODE.entrySet()) {
            if (entry.getValue().cameraIds().contains(cameraId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
