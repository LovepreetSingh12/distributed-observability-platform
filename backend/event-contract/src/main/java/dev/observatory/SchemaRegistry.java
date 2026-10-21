package dev.observatory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class SchemaRegistry {

    public static final String DIALECT ="https://json-schema.org/draft/2020-12/schema";

    private final MongoTemplate db;
    private final ObjectMapper mapper;

    private final Map<String, JsonSchema> cache = Collections.synchronizedMap(
														new LinkedHashMap<>(128, 0.75f, true) {

															@Override
															protected boolean removeEldestEntry(
																	Map.Entry<String, JsonSchema> e) {

																return size() > 512;
															}
														}
													);

    public SchemaRegistry(
            MongoTemplate db,
            ObjectMapper mapper) {

        this.db = db;
        this.mapper = mapper;
    }

    public JsonSchema compile(JsonNode schema) {

        if (!DIALECT.equals(schema.path("$schema").asText())
                || !"object".equals(schema.path("type").asText())) {

            throw new IllegalArgumentException(
                    "Use draft 2020-12 with object payload root"
            );
        }

        checkRefs(schema);

        return JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schema);
    }

    private void checkRefs(JsonNode n) {

        if (n.isObject()) {

            n.fields().forEachRemaining(
                    e -> {

                        if ((e.getKey().equals("$ref")
                                || e.getKey().equals("$dynamicRef"))
                                && !e.getValue().asText().startsWith("#")) {

                            throw new IllegalArgumentException(
                                    "External schema references are disabled"
                            );
                        }

                        checkRefs(e.getValue());
                    }
            );

        } else if (n.isArray()) {

            n.forEach(this::checkRefs);
        }
    }

    public JsonSchema published(
            String tenant,
            String type,
            int version) {

        String key = tenant + ":" + type + ":" + version;

        JsonSchema found = cache.get(key);

        if (found != null) {
            return found;
        }

        Document d = db.findOne(
                Query.query(
					Criteria.where("tenantId")
						.is(tenant)
						.and("eventType")
						.is(type)
						.and("version")
						.is(version)
						.and("published")
						.is(true)
                ),
                Document.class,
                "event_schemas"
        );

        if (d == null) {
            throw new IllegalArgumentException(
                "Published schema version not found"
            );
        }

        try {
            found = compile(
                    mapper.readTree(d.getString("schema"))
            );

            cache.put(key, found);

            return found;

        } catch (IOException e) {

            throw new IllegalArgumentException(
                    "Invalid stored schema"
            );
        }
    }

    public List<String> validate(
            JsonSchema schema,
            JsonNode payload) {

        return schema.validate(payload)
                .stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
    }
}