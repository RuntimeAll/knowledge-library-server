package com.ruoyi.web.core.docs;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves DTOs with Swagger, then applies the application's actual string serialization. */
public final class CoreApiSchemas {
    private final Map<String, Schema> schemas = new LinkedHashMap<>();
    private final Set<Class<?>> classes = new LinkedHashSet<>();
    private final Map<String, Map<String, String>> descriptions = new LinkedHashMap<>();
    private final boolean responseView;

    public CoreApiSchemas() {
        this(false);
    }

    public CoreApiSchemas(boolean responseView) {
        this.responseView = responseView;
    }

    public synchronized void describe(Type type, Map<String, String> details, String prefix) {
        describe(type, details, prefix, new LinkedHashSet<>());
    }

    private void describe(Type type, Map<String, String> details, String prefix, Set<Class<?>> ancestors) {
        if (type instanceof ParameterizedType generic) {
            for (Type child : generic.getActualTypeArguments()) {
                describe(child, details, prefix + "[]", ancestors);
            }
            return;
        }
        if (!(type instanceof Class<?> bean) || !bean.getName().startsWith("com.ruoyi.") || ancestors.contains(bean)) {
            return;
        }
        Set<Class<?>> next = new LinkedHashSet<>(ancestors);
        next.add(bean);
        Schema resolved = resolve(bean);
        if (resolved.get$ref() == null) {
            return;
        }
        String modelName = resolved.get$ref().substring("#/components/schemas/".length());
        Map<String, Type> fields = new LinkedHashMap<>();
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(bean).getPropertyDescriptors()) {
                if (property.getReadMethod() != null && !property.getReadMethod().isAnnotationPresent(JsonIgnore.class)) {
                    fields.put(property.getName(), property.getReadMethod().getGenericReturnType());
                }
            }
        } catch (IntrospectionException exception) {
            throw new IllegalStateException("Cannot describe " + bean.getName(), exception);
        }
        if (bean.isRecord()) {
            for (var component : bean.getRecordComponents()) {
                fields.put(component.getName(), component.getGenericType());
            }
        }
        fields.forEach((name, fieldType) -> {
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            if (details.containsKey(key)) {
                descriptions.computeIfAbsent(modelName, ignored -> new LinkedHashMap<>()).put(name, details.get(key));
            }
            describe(fieldType, details, key, next);
        });
    }

    public synchronized Schema resolve(Type type) {
        ResolvedSchema resolved = ModelConverters.getInstance(true).resolveAsResolvedSchema(
                new AnnotatedType(type).resolveAsRef(true));
        schemas.putAll(resolved.referencedSchemas);
        collect(type);
        return resolved.schema;
    }

    public Schema response(CoreApi api) {
        if (api.shape() == CoreApi.Shape.BINARY) {
            return new StringSchema().format("binary");
        }
        ObjectSchema envelope = new ObjectSchema();
        envelope.addProperty("code", new IntegerSchema().description("业务码；200表示调用成功，仍需检查逐项结果"));
        envelope.addProperty("msg", new StringSchema().description("对外消息，不能作为稳定的程序分支键"));
        envelope.setRequired(List.of("code", "msg"));
        if (api.shape() == CoreApi.Shape.DATA && (api.response() == Void.class || api.response() == void.class)) {
            return envelope;
        }
        Schema payload = resolve(api.response());
        if (responseView && payload.get$ref() != null) {
            payload = new Schema<>().$ref(payload.get$ref() + ".Response");
        }
        if (api.shape() == CoreApi.Shape.PAGE) {
            envelope.addProperty("rows", new ArraySchema().items(payload));
            envelope.addProperty("total", new IntegerSchema().format("int64").description("符合相同服务端筛选的总记录数"));
            envelope.setRequired(List.of("code", "msg", "rows", "total"));
        } else {
            envelope.addProperty("data", api.shape() == CoreApi.Shape.ARRAY
                    ? new ArraySchema().items(payload) : payload);
            if (!api.value().endsWith(".findSubmission")) {
                envelope.setRequired(List.of("code", "msg", "data"));
            }
        }
        return envelope;
    }

    public static Schema error() {
        return new ObjectSchema().addProperty("code", new IntegerSchema())
                .addProperty("msg", new StringSchema()).required(List.of("code", "msg"));
    }

    public synchronized Map<String, Schema> all() {
        // Resolve every nested Java type before patching: a later resolver result must not undo a patch.
        for (Class<?> type : List.copyOf(classes)) {
            resolve(type);
        }
        for (Class<?> type : classes) {
            patch(type);
        }
        descriptions.forEach((name, fields) -> {
            Schema model = schemas.get(name);
            if (model != null && model.getProperties() != null) {
                fields.forEach((field, description) -> {
                    Schema property = (Schema) model.getProperties().get(field);
                    if (property != null) {
                        property.setDescription(description);
                    }
                });
            }
        });
        if (!responseView) {
            return new LinkedHashMap<>(schemas);
        }
        Map<String, Schema> output = new LinkedHashMap<>();
        schemas.forEach((name, schema) -> {
            Schema copy = io.swagger.v3.core.util.Json31.mapper().convertValue(schema, Schema.class);
            responseReferences(copy);
            output.put(name + ".Response", copy);
        });
        for (Class<?> type : classes) {
            nullableResponse(type, output);
        }
        return output;
    }

    private void nullableResponse(Class<?> bean, Map<String, Schema> output) {
        Schema model = output.get(bean.getName().replace('$', '.') + ".Response");
        if (model == null || model.getProperties() == null) {
            return;
        }
        try {
            for (PropertyDescriptor field : Introspector.getBeanInfo(bean).getPropertyDescriptors()) {
                if (field.getReadMethod() != null && !field.getPropertyType().isPrimitive()) {
                    nullableProperty(model, field.getName());
                }
            }
            if (bean.isRecord()) {
                for (var field : bean.getRecordComponents()) {
                    if (!field.getType().isPrimitive()) {
                        nullableProperty(model, field.getName());
                    }
                }
            }
        } catch (IntrospectionException exception) {
            throw new IllegalStateException("Cannot inspect response nullability " + bean.getName(), exception);
        }
    }

    private void nullableProperty(Schema model, String name) {
        Schema property = (Schema) model.getProperties().get(name);
        if (property == null) {
            return;
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        }
        if (property.getType() != null) {
            types.add(property.getType());
        }
        if (!types.isEmpty()) {
            types.add("null");
            property.setType(null);
            property.setTypes(types);
        } else {
            model.addProperty(name, new Schema<>().description(property.getDescription())
                    .anyOf(List.of(property, new Schema<>().types(Set.of("null")))));
        }
    }

    private void responseReferences(Schema schema) {
        if (schema.get$ref() != null && schema.get$ref().startsWith("#/components/schemas/")) {
            schema.set$ref(schema.get$ref() + ".Response");
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(child -> responseReferences((Schema) child));
        }
        if (schema.getItems() != null) {
            responseReferences(schema.getItems());
        }
        for (List<Schema> children : List.of(schema.getAllOf() == null ? List.<Schema>of() : schema.getAllOf(),
                schema.getAnyOf() == null ? List.<Schema>of() : schema.getAnyOf(),
                schema.getOneOf() == null ? List.<Schema>of() : schema.getOneOf())) {
            children.forEach(this::responseReferences);
        }
        if (schema.getAdditionalProperties() instanceof Schema nested) {
            responseReferences(nested);
        }
    }

    private void collect(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument);
            }
        } else if (type instanceof Class<?> bean && bean.getName().startsWith("com.ruoyi.") && classes.add(bean)) {
            for (Method method : bean.getMethods()) {
                if (isGetter(method) && !method.isAnnotationPresent(JsonIgnore.class)) {
                    collect(method.getGenericReturnType());
                }
            }
            if (bean.isRecord()) {
                for (var component : bean.getRecordComponents()) {
                    collect(component.getGenericType());
                }
            }
        }
    }

    private void patch(Class<?> bean) {
        ResolvedSchema resolved = ModelConverters.getInstance(true).resolveAsResolvedSchema(
                new AnnotatedType(bean).resolveAsRef(true));
        if (resolved.schema == null || resolved.schema.get$ref() == null) {
            return;
        }
        Schema model = schemas.get(resolved.schema.get$ref().substring("#/components/schemas/".length()));
        if (model == null || model.getProperties() == null) {
            return;
        }
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(bean).getPropertyDescriptors()) {
                Method getter = property.getReadMethod();
                if (getter != null) {
                    patchProperty(model, property.getName(), getter.getGenericReturnType(), getter);
                }
            }
            if (bean.isRecord()) {
                for (var component : bean.getRecordComponents()) {
                    patchProperty(model, component.getName(), component.getGenericType(), component.getAccessor());
                }
            }
        } catch (IntrospectionException exception) {
            throw new IllegalStateException("Cannot inspect API model " + bean.getName(), exception);
        }
        String name = bean.getSimpleName();
        if (name.equals("KbRecordsRequest")) {
            model.setRequired(List.of("schemaVersion", "idempotencyKey", "records"));
            model.setAdditionalProperties(false);
        } else if (name.equals("KbAgentSourceRequest")) {
            model.setRequired(List.of("externalKey", "source"));
            model.setAdditionalProperties(false);
        } else if (name.equals("KbAgentProductRequest")) {
            model.setRequired(List.of("externalKey", "product"));
            model.setAdditionalProperties(false);
        }
        patchRequestBoundary(bean, model);
    }

    private void patchRequestBoundary(Class<?> bean, Schema model) {
        // These records explicitly reject unknown JSON members through JsonAnySetter.
        if (bean.getName().startsWith("com.ruoyi.knowledge.importing.dto.KbRecordsRequest")
                || bean == com.ruoyi.knowledge.html.HtmlMetadata.class) {
            model.setAdditionalProperties(false);
        }
        List<String> nullable = switch (bean.getSimpleName()) {
            // saveSource/saveProduct treat precisely these null aggregate overrides as not supplied.
            case "KbAgentSourceRequest" -> List.of("profile", "outlineScopes", "declaredKpIds", "regions", "examDetail");
            case "KbAgentProductRequest" -> List.of("profile", "outlineScopes", "declaredKpIds", "regions",
                    "questionRefs", "referenceSources", "expectedMaterialRowVersion");
            default -> List.of();
        };
        nullable.forEach(field -> nullableProperty(model, field));
    }

    private void patchProperty(Schema model, String name, Type type, AnnotatedElement accessor) {
        if (accessor.isAnnotationPresent(JsonIgnore.class)) {
            model.getProperties().remove(name);
            return;
        }
        Schema property = (Schema) model.getProperties().get(name);
        if (property == null) {
            return;
        }
        JsonFormat format = accessor.getAnnotation(JsonFormat.class);
        boolean serializedString = format != null && format.shape() == JsonFormat.Shape.STRING;
        boolean id = name.equals("id") || name.endsWith("Id") || name.endsWith("Ids") || name.equals("rowVersion")
                || name.endsWith("RowVersion");
        if (type == Long.class && (serializedString || id)) {
            model.addProperty(name, new StringSchema().description(property.getDescription()));
        } else if (type instanceof ParameterizedType parameterized && id
                && parameterized.getActualTypeArguments()[0] == Long.class) {
            model.addProperty(name, new ArraySchema().items(new StringSchema()).description(property.getDescription()));
        }
    }

    private static boolean isGetter(Method method) {
        return method.getParameterCount() == 0 && method.getName().startsWith("get")
                && !method.getName().equals("getClass");
    }
}
