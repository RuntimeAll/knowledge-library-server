package com.ruoyi.web.core.docs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** Loads versioned semantic documentation; Java declarations remain the type source. */
public final class CoreApiContracts {
    private final Map<String, Contract> operations;

    public CoreApiContracts() {
        try (InputStream input = new ClassPathResource("api/core-contracts.json").getInputStream()) {
            Map<String, Map<String, Contract>> root = new ObjectMapper().readValue(input, new TypeReference<>() { });
            operations = Map.copyOf(root.get("operations"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load core API documentation", exception);
        }
    }

    public Contract require(String operationId) {
        Contract contract = operations.get(operationId);
        if (!complete(operationId)) {
            throw new IllegalStateException("Missing or incomplete API documentation: " + operationId);
        }
        return contract;
    }

    public Map<String, Contract> all() {
        return operations;
    }

    public boolean complete(String operationId) {
        Contract value = operations.get(operationId);
        return value != null && nonempty(value.group()) && nonempty(value.action())
                && nonempty(value.summary()) && nonempty(value.responsibility()) && nonempty(value.permissionRule())
                && value.prdRefs() != null && !value.prdRefs().isEmpty()
                && value.contractRefs() != null && !value.contractRefs().isEmpty()
                && value.invocationRules() != null && !value.invocationRules().isEmpty()
                && value.businessErrors() != null && !value.businessErrors().isEmpty()
                && value.businessErrors().stream().allMatch(error -> error.keySet().containsAll(
                        java.util.Set.of("httpStatus", "code", "condition", "recovery")))
                && value.successExample() != null && value.failureExample() != null && value.requestExample() != null
                && value.parameterDescriptions() != null && !value.parameterDescriptions().isEmpty();
    }

    private static boolean nonempty(String value) {
        return value != null && !value.isBlank();
    }

    public record Contract(String group, String action, String summary, String responsibility,
            java.util.List<String> prdRefs, java.util.List<String> contractRefs, String permissionRule,
            Map<String, String> invocationRules, java.util.List<Map<String, Object>> businessErrors,
            Object successExample, Object failureExample, Object requestExample,
            Map<String, String> parameterDescriptions) { }
}
