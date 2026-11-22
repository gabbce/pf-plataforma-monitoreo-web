# Spring Boot startup diagnostic playbook

Use this reference after classifying the startup failure. Prefer commands already supported by the repository.

## Configuration binding and validation

Look for the full property path, rejected value origin, target Java type, and active profile. Check, in order:

1. command-line arguments;
2. Java system properties;
3. environment variables;
4. profile-specific configuration;
5. base configuration;
6. defaults in code.

Malformed `Duration`, `DataSize`, enum, URI, collection, or nested record values often appear under `ConfigurationPropertiesBindException` and `BindException`. Missing required values may surface as Jakarta Validation violations. Preserve validation unless the field is genuinely optional.

Useful evidence:

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--debug
./mvnw -q dependency:tree
```

Never paste unredacted environment variables or complete production configuration into an issue.

## Bean creation and missing beans

Trace constructor dependencies from the failed bean to the deepest cause. Check:

- package location relative to the `@SpringBootApplication` scan root;
- `@Configuration` and `@Bean` method conditions;
- `@Profile`, `@ConditionalOnProperty`, `@ConditionalOnClass`, and test slice boundaries;
- qualifier names and generic types;
- optional dependencies absent from the resolved runtime classpath;
- exceptions thrown inside constructors, factory methods, and initialization callbacks.

Adding a fallback bean can hide a missing integration or select the wrong implementation. Confirm ownership first.

## Circular dependencies

Draw the shortest cycle between constructors or configuration methods. Break it at the architectural boundary by extracting an orchestration service, publishing an event, or reversing an invalid dependency. Use `@Lazy` only when deferred construction is intentional and tested.

## Conditional auto-configuration

Use the condition evaluation report to distinguish:

- a required class missing from the classpath;
- a property condition not satisfied;
- an existing user bean causing back-off;
- an auto-configuration explicitly excluded;
- a test slice intentionally omitting infrastructure.

Do not enable every auto-configuration to make a narrow test pass.

## Classpath linkage

For `NoSuchMethodError` or `NoClassDefFoundError`, capture:

```bash
./mvnw -q dependency:tree -Dverbose
./mvnw -q help:effective-pom
```

Find the artifact that supplies the class, the selected version, and the dependency-management source. Prefer BOM alignment or exclusion of the unwanted path over pinning unrelated transitive artifacts one by one.

## External resources and web server

Separate connection refusal, authentication failure, DNS, TLS, schema initialization, and unavailable secrets. For an occupied port, identify the owner without terminating it automatically:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Changing the server port is valid only when a fixed port is not part of the application's contract.

## Verification ladder

Run the smallest relevant check first:

1. focused binding or context test;
2. affected module tests;
3. application startup with the same profile and sanitized inputs;
4. full build.

Report skipped levels and why.
