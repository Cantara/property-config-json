package no.cantara.config.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public final class PropertyMapToJsonConverter {
    private final Map<String, String> properties;
    private final ObjectNode json;

    public PropertyMapToJsonConverter(Map<String, String> properties, ObjectNode json) {
        this.properties = properties;
        this.json = json;
    }

    public PropertyMapToJsonConverter(Map<String, String> propertyMap) {
        this(propertyMap, JsonNodeFactory.instance.objectNode());

        List<Property> propertyList = propertyMap.entrySet().stream()
                .map(e -> new PropertyTokenizer(e.getKey(), e.getValue()))
                .map(t -> t.property)
                .collect(Collectors.toList());

        Map<String, JsonNode> parentPathMap = new LinkedHashMap<>();
        parentPathMap.put("ROOT", json);

        Set<String> visitedPaths = new LinkedHashSet<>();

        for (int i = 0; i < propertyList.size(); i++) {
            Property property = propertyList.get(i);

            for (int j = 0; j < property.elements.size(); j++) {
                PropertyElement propertyElement = property.elements.get(j);

                //String pathElements = property.elements.stream().map(m -> m.key() + "[" + m.type() + "]").collect(Collectors.joining(".")) + "=" + property.value;
                String nextParentPathElements = getPathElementsByProperty(property, j + 1);
                String parentPathElements = getPathElementsByProperty(property, j);

                JsonNode parentNode = parentPathMap.get(parentPathElements);

                JsonNode jsonNode;
                switch (propertyElement.type()) {
                    case LEAF_NODE:
                        jsonNode = JsonNodeFactory.instance.textNode(property.value);
                        break;
                    case ARRAY_ELEMENT:
                        jsonNode = JsonNodeFactory.instance.textNode(property.value);
                        break;
                    case OBJECT:
                        jsonNode = JsonNodeFactory.instance.objectNode();
                        break;
                    case ARRAY_OBJECT:
                        jsonNode = JsonNodeFactory.instance.objectNode();
                        break;
                    case ARRAY_NODE:
                        jsonNode = JsonNodeFactory.instance.arrayNode();
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // skip already handled path
                if (!visitedPaths.add(nextParentPathElements)) {
                    continue;
                }

                // set next parentNode
                JsonNode childNode = parentPathMap.computeIfAbsent(nextParentPathElements, k -> jsonNode);

                if (parentNode instanceof ObjectNode) {
                    ((ObjectNode) parentNode).set(propertyElement.key(), childNode);

                } else if (parentNode instanceof ArrayNode) {
                    ((ArrayNode) parentNode).add(childNode);

                } else {
                    throw new IllegalStateException("property-element-pos: " + i + " => " + parentNode);
                }
            }
        }
    }

    private static String getPathElementsByProperty(Property property, int limit) {
        List<String> parentPathElementList = property.elements.stream().limit(limit).map(PropertyElement::key).collect(Collectors.toList());
        return "ROOT" + (parentPathElementList.isEmpty() ? "" : "." + String.join(".", parentPathElementList));
    }

    public Map<String, String> properties() {
        return properties;
    }

    public ObjectNode json() {
        return json;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        PropertyMapToJsonConverter that = (PropertyMapToJsonConverter) obj;
        return Objects.equals(this.properties, that.properties) &&
                Objects.equals(this.json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties, json);
    }

    @Override
    public String toString() {
        return "PropertyMapToJsonConverter[" +
                "properties=" + properties + ", " +
                "json=" + json + ']';
    }
    
    static class PropertyTokenizer {
        private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d+$");
        private final String key;
        private final String value;
        private final Property property;

        PropertyTokenizer(String key, String value, Property property) {
            this.key = key;
            this.value = value;
            this.property = property;
        }

        PropertyTokenizer(String property, String value) {
            this(property, value, tokenize(property, value));
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }

        public Property property() {
            return property;
        }

        static Property tokenize(String property, String value) {
            List<PropertyElement> elementList = new ArrayList<>();


            List<String> list = Arrays.asList(property.split("\\."));
            String previous = null;
            for (int i = 0; i < list.size(); i++) {
                String current = list.get(i);
                String next = (i + 1 < list.size()) ? list.get(i + 1) : null;

                if (isLeafNode(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.LEAF_NODE));

                } else if (isArrayNode(previous, current) && isArrayObject(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_OBJECT));

                } else if (isArrayNode(previous, current) && isArrayElement(current)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_ELEMENT));

                } else if (isArrayNode(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_NODE));

                } else if (isObject(current)) {
                    elementList.add(PropertyElement.of(current, ElementType.OBJECT));

                } else {
                    throw new IllegalStateException(String.format("Unknown type: [elementIndex: %s] %s <- %s", i, current, i > 0 ? list.get(i - 1) : "(null)"));
                }

                previous = current;
            }

            return new Property(property, value, elementList);
        }

        private static boolean isLeafNode(String token, String nextToken) {
            return isObject(token) && nextToken == null;
        }

        private static boolean isObject(String token) {
            return !isArrayElement(token);
        }

        private static boolean isArrayNode(String token, String nextToken) {
            return isObject(token) && isArrayElement(nextToken);
        }

        private static boolean isArrayElement(String token) {
            return ofNullable(token)
                    .map(INTEGER_PATTERN::matcher)
                    .map(Matcher::find)
                    .orElse(false);
        }

        private static boolean isArrayObject(String token, String nextToken) {
            return isArrayElement(token) && (nextToken != null && isObject(nextToken));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            PropertyTokenizer that = (PropertyTokenizer) obj;
            return Objects.equals(this.key, that.key) &&
                    Objects.equals(this.value, that.value) &&
                    Objects.equals(this.property, that.property);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value, property);
        }

        @Override
        public String toString() {
            return "PropertyTokenizer[" +
                    "key=" + key + ", " +
                    "value=" + value + ", " +
                    "property=" + property + ']';
        }
    }

    enum ElementType {
        LEAF_NODE,
        OBJECT,
        ARRAY_NODE,
        ARRAY_ELEMENT,
        ARRAY_OBJECT;
    }

    static class Property {
        private final String key;
        private final String value;
        private final List<PropertyElement> elements;

        Property(String key, String value, List<PropertyElement> elements) {
            this.key = key;
            this.value = value;
            this.elements = elements;
        }

        public String key() {
            return key;
        }

        public String value() {
            return value;
        }

        public List<PropertyElement> elements() {
            return elements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Property property = (Property) o;
            return key.equals(property.key) && Objects.equals(value, property.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            return "Property[" +
                    "key=" + key + ", " +
                    "value=" + value + ", " +
                    "elements=" + elements + ']';
        }

    }

    interface PropertyElement {

        String key();

        ElementType type();

        static PropertyElement of(String property, ElementType type) {
            return new PropertyElementImpl(property, type);
        }
    }

    private static class PropertyElementImpl implements PropertyElement {
        private final String key;
        private final ElementType type;

        public PropertyElementImpl(String key, ElementType type) {
            this.key = key;
            this.type = type;
        }

        @Override
        public ElementType type() {
            return type;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PropertyElementImpl that = (PropertyElementImpl) o;
            return Objects.equals(key, that.key) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, type);
        }

        @Override
        public String toString() {
            return "PropertyElementImpl[" +
                    "key=" + key + ", " +
                    "type=" + type + ']';
        }
    }
}
