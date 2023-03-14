# Property Config JSON Converter

Converts string map to Jackson json structure.

```java
ApplicationProperties config=ApplicationProperties.builder()
        .classpathPropertiesFile("application-test.properties")
        .build();

PropertyMapToJsonConverter converter=new PropertyMapToJsonConverter(config.map());
ObjectNode json=converter.json();

System.out.println(json.toPrettyString());
```
