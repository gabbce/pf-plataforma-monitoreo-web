# Maven upgrade playbook

## Inspect

```bash
./mvnw --version
./mvnw -q help:active-profiles
./mvnw help:effective-pom -Doutput=target/effective-pom.xml
./mvnw dependency:tree -Dverbose
./mvnw dependency:analyze
```

Add `-pl module -am` to narrow a reactor without losing upstream modules. Use the Enforcer dependency-convergence rule when the repository already adopts it; do not introduce a broad enforcement change inside an unrelated emergency upgrade.

## Find the version owner

1. A parent or imported BOM owns managed dependencies.
2. `<pluginManagement>` owns plugin defaults but does not execute a plugin by itself.
3. A direct dependency can change nearest-wins resolution but may fight a platform BOM.
4. A property can feed multiple declarations; search all modules before changing it.
5. An exclusion changes the graph and requires a reason plus a resolved-tree check.

## Sequence

1. Make the baseline reproducible.
2. Upgrade Maven wrapper, JDK, or build plugins only when required by the target.
3. Upgrade the parent or BOM as one batch.
4. Fix compilation and configuration migrations without unrelated refactors.
5. Run module, reactor, integration, packaging, and startup verification.
6. Inspect the final resolved tree and record the rollback version.

## Diagnose failures

- `NoSuchMethodError` or `ClassNotFoundException`: inspect runtime convergence and shaded artifacts.
- compilation removal: read migration notes and replace the API rather than pinning an arbitrary transitive dependency.
- test-only failure: compare test engine, bytecode agent, container, and logging versions.
- startup condition failure: inspect configuration metadata, renamed properties, and auto-configuration reports.
- serialization drift: run representative payload compatibility tests for Jackson and messaging changes.
