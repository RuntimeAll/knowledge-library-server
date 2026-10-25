package com.ruoyi.web.core.docs;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;

/** Builds the public document from selected handlers and one semantic resource. */
public final class CoreApiDocumentation implements OperationCustomizer, OpenApiCustomizer {
    private static final Map<String, String> TAGS = Map.of(
            "asset", "01 文件资产", "source", "02 来源资料", "question", "03 题目与知识内容",
            "product", "04 成品归档", "reference", "05 分类与参数支撑");
    private final CoreApiContracts contracts;
    private final CoreApiSchemas schemas = new CoreApiSchemas();
    private final CoreApiSchemas responseSchemas = new CoreApiSchemas(true);

    public CoreApiDocumentation(CoreApiContracts contracts) {
        this.contracts = contracts;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        CoreApi api = handler.getMethodAnnotation(CoreApi.class);
        if (api == null || !api.published()) {
            return operation;
        }
        CoreApiContracts.Contract contract = contracts.require(api.value());
        verifyPermission(handler, contract);
        operation.setOperationId(api.value());
        operation.setTags(List.of(TAGS.get(contract.group())));
        operation.setSummary("[" + contract.action() + "] " + contract.summary());
        operation.setDescription(description(contract));
        operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
        operation.addExtension("x-kb-group", contract.group());
        operation.addExtension("x-kb-action", contract.action());
        operation.addExtension("x-kb-prd-refs", contract.prdRefs());
        operation.addExtension("x-kb-contract-refs", contract.contractRefs());
        operation.addExtension("x-kb-responsibility", contract.responsibility());
        operation.addExtension("x-kb-permission-rule", contract.permissionRule());
        operation.addExtension("x-kb-invocation-rules", contract.invocationRules());
        operation.addExtension("x-kb-business-errors", contract.businessErrors());
        operation.addExtension("x-kb-request-example", contract.requestExample());
        parameters(operation, handler.getMethod(), api, contract);
        responseSchemas.describe(api.response(), contract.parameterDescriptions(), switch (api.shape()) {
            case PAGE -> "rows[]";
            case ARRAY -> "data[]";
            default -> "data";
        });
        responses(operation, api, contract);
        return operation;
    }

    private void parameters(Operation operation, Method method, CoreApi api, CoreApiContracts.Contract contract) {
        List<Parameter> parameters = new ArrayList<>();
        ObjectSchema multipart = new ObjectSchema();
        List<String> requiredParts = new ArrayList<>();
        boolean hasFile = false;
        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            PathVariable path = parameter.getAnnotation(PathVariable.class);
            RequestParam query = parameter.getAnnotation(RequestParam.class);
            RequestBody body = parameter.getAnnotation(RequestBody.class);
            if (body != null) {
                schemas.describe(parameter.getParameterizedType(), contract.parameterDescriptions(), "");
                operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody().required(body.required())
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(schemas.resolve(parameter.getParameterizedType())).example(contract.requestExample()))));
            } else if (path != null) {
                String name = name(path.name(), path.value(), parameter.getName());
                parameters.add(parameter(name, "path", true, scalar(parameter.getType(), name), contract));
            } else if (query != null) {
                String name = name(query.name(), query.value(), parameter.getName());
                boolean file = MultipartFile.class.isAssignableFrom(parameter.getType());
                hasFile |= file;
                Schema shape = file ? new StringSchema().format("binary") : scalar(parameter.getType(), name);
                multipart.addProperty(name, shape.description(explanation(contract, name)));
                if (query.required()) {
                    requiredParts.add(name);
                }
                if (!file) {
                    parameters.add(parameter(name, "query", query.required(), shape, contract));
                }
            } else if (api.queryFields().length > 0 && parameter.getType().getName().startsWith("com.ruoyi.")) {
                queryFields(parameters, parameter.getType(), api.queryFields(), contract);
            }
        }
        if (hasFile) {
            multipart.setRequired(requiredParts);
            parameters.removeIf(parameter -> "query".equals(parameter.getIn()));
            operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody().required(true)
                    .content(new Content().addMediaType("multipart/form-data", new MediaType().schema(multipart).example(contract.requestExample()))));
        }
        if (api.shape() == CoreApi.Shape.PAGE) {
            parameters.add(parameter("pageNum", "query", false, new IntegerSchema()._default(1), contract));
            parameters.add(parameter("pageSize", "query", false, new IntegerSchema()._default(10), contract));
            parameters.add(parameter("orderByColumn", "query", false, new StringSchema(), contract));
            parameters.add(parameter("isAsc", "query", false, new StringSchema(), contract));
            parameters.add(parameter("reasonable", "query", false, new Schema<Boolean>().type("boolean")._default(true), contract));
        }
        operation.setParameters(parameters.isEmpty() ? null : parameters);
    }

    private void queryFields(List<Parameter> parameters, Class<?> type, String[] allowed,
            CoreApiContracts.Contract contract) {
        try {
            Map<String, Class<?>> types = new LinkedHashMap<>();
            for (var property : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                types.put(property.getName(), property.getPropertyType());
            }
            for (String field : allowed) {
                if (!types.containsKey(field)) {
                    throw new IllegalStateException("Unknown API query field " + type.getName() + "." + field);
                }
                parameters.add(parameter(field, "query", false, scalar(types.get(field), field), contract));
            }
        } catch (java.beans.IntrospectionException exception) {
            throw new IllegalStateException("Cannot inspect query type " + type.getName(), exception);
        }
    }

    private Parameter parameter(String name, String location, boolean required, Schema schema,
            CoreApiContracts.Contract contract) {
        Parameter parameter = new Parameter().name(name).in(location).required(required).schema(schema)
                .description(explanation(contract, name));
        if (contract.requestExample() instanceof Map<?, ?> values && values.containsKey(name)) {
            parameter.setExample(values.get(name));
        }
        return parameter;
    }

    private void responses(Operation operation, CoreApi api, CoreApiContracts.Contract contract) {
        Schema error = CoreApiSchemas.error();
        error.addProperty("code", new IntegerSchema().not(new IntegerSchema()._enum(List.of(200))));
        Content content = new Content();
        if (api.shape() == CoreApi.Shape.BINARY) {
            content.addMediaType(api.mediaType(), new MediaType().schema(responseSchemas.response(api))
                    .addExamples("success", new Example().summary("成功文件流").value(contract.successExample())));
            content.addMediaType("application/json", new MediaType().schema(error)
                    .addExamples("businessFailure", new Example().value(contract.failureExample())));
        } else {
            Schema success = responseSchemas.response(api);
            success.addProperty("code", new IntegerSchema()._enum(List.of(200)));
            Schema envelope = new Schema<>().oneOf(List.of(success, error));
            content.addMediaType("application/json", new MediaType().schema(envelope)
                    .addExamples("success", new Example().value(contract.successExample()))
                    .addExamples("businessFailure", new Example().value(contract.failureExample())));
        }
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse()
                .description("检查HTTP状态之外，还须检查JSON code及导入逐项状态；业务拒绝仍可能返回HTTP 200。")
                .content(content)).addApiResponse("default", new ApiResponse()
                .description("代理、容器或网络层失败按实际HTTP状态处理；不得当作业务成功。")));
    }

    @Override
    public void customise(OpenAPI openApi) {
        openApi.setServers(List.of(new Server().url("../../").description("当前部署环境（相对core规范路径）")));
        Map<String, Schema> all = schemas.all();
        all.putAll(responseSchemas.all());
        Set<String> referenced = new LinkedHashSet<>();
        references(Json.mapper().valueToTree(openApi.getPaths()), referenced);
        List<String> pending = new ArrayList<>(referenced);
        for (int index = 0; index < pending.size(); index++) {
            Schema schema = all.get(pending.get(index));
            if (schema == null) {
                throw new IllegalStateException("Unresolved public schema " + pending.get(index));
            }
            Set<String> nested = new LinkedHashSet<>();
            references(Json.mapper().valueToTree(schema), nested);
            for (String name : nested) {
                if (referenced.add(name)) {
                    pending.add(name);
                }
            }
        }
        Map<String, Schema> selected = new java.util.TreeMap<>();
        referenced.forEach(name -> selected.put(name, all.get(name)));
        openApi.getComponents().setSchemas(selected);
    }

    private static void references(com.fasterxml.jackson.databind.JsonNode node, Set<String> result) {
        if (node.isObject()) {
            var reference = node.get("$ref");
            if (reference != null && reference.asText().startsWith("#/components/schemas/")) {
                result.add(reference.asText().substring("#/components/schemas/".length()));
            }
        }
        node.forEach(child -> references(child, result));
    }

    private static Schema scalar(Class<?> type, String name) {
        if (type == Long.class || type == long.class) {
            return new StringSchema();
        }
        if (type == Integer.class || type == int.class) {
            return new IntegerSchema();
        }
        if (type == Boolean.class || type == boolean.class) {
            return new Schema<Boolean>().type("boolean");
        }
        return new StringSchema();
    }

    private static String name(String first, String second, String fallback) {
        return !first.isEmpty() ? first : !second.isEmpty() ? second : fallback;
    }

    private static String explanation(CoreApiContracts.Contract contract, String name) {
        return contract.parameterDescriptions().getOrDefault(name, switch (name) {
            case "pageNum" -> "服务端页码，默认1";
            case "pageSize" -> "每页数量，默认10；现有公共分页工具尚无统一上限";
            case "orderByColumn" -> "若依排序列；仍受实际Mapper排序语句约束";
            case "isAsc" -> "若依排序方向asc/desc；仍受实际Mapper排序语句约束";
            case "reasonable" -> "PageHelper页码合理化开关，默认true：超出末页会回退末页；false按请求页码返回，越界得到空页";
            default -> name + "；含义与适用条件见本接口正式契约";
        });
    }

    private static void verifyPermission(HandlerMethod handler, CoreApiContracts.Contract contract) {
        PreAuthorize permission = handler.getMethodAnnotation(PreAuthorize.class);
        String actual = permission == null ? "authenticated" : permission.value();
        if (!actual.replaceAll("\\s+", "").equals(contract.permissionRule().replaceAll("\\s+", ""))) {
            throw new IllegalStateException("Permission documentation differs for " + handler.getMethod().getName());
        }
    }

    private static String description(CoreApiContracts.Contract contract) {
        StringBuilder text = new StringBuilder(contract.responsibility()).append("\n\n权限：`")
                .append(contract.permissionRule()).append("`\n\n");
        contract.invocationRules().forEach((rule, value) -> text.append("- **").append(rule).append("**：")
                .append(value).append('\n'));
        text.append("\n| HTTP | code | 条件 | 处理 |\n|---|---|---|---|\n");
        for (Map<String, Object> error : contract.businessErrors()) {
            text.append('|').append(error.get("httpStatus")).append('|').append(error.get("code"))
                    .append('|').append(error.get("condition")).append('|').append(error.get("recovery")).append("|\n");
        }
        text.append("\n参数与字段说明：\n\n| 字段 | 含义 |\n|---|---|\n");
        contract.parameterDescriptions().forEach((name, value) -> text.append('|').append(name)
                .append('|').append(value.replace("|", "\\|")).append("|\n"));
        text.append("\n正式契约：").append(String.join("、", contract.contractRefs()));
        return text.toString();
    }
}
