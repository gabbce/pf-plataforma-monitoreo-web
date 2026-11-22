---
name: maven-dependency-upgrader
description: Plan and validate safe Maven dependency, plugin, BOM, Java, Spring Boot, Spring Cloud, or Spring AI upgrades. Use when resolving version drift, CVE remediation, convergence errors, removed APIs, Jakarta migration, build plugin compatibility, or multi-module upgrade sequencing.
---

# Maven Dependency Upgrader

Upgrade the smallest coherent version set while preserving a reproducible baseline and a fast rollback path.

## Capture the baseline

1. Record Java and Maven versions, active profiles, repository mirrors, wrapper version, and the exact failing or passing command.
2. Read the reactor POMs, imported BOMs, parent POM, plugin management, and dependency management before editing leaf versions.
3. Run the existing build and save relevant test, dependency convergence, and application startup results.
4. Generate the effective POM and dependency tree for disputed artifacts. Distinguish declared, managed, and transitive versions.

## Plan the upgrade

1. Identify the version owner. Prefer a framework or platform BOM over scattered explicit versions.
2. Check the target's Java baseline, removed APIs, configuration changes, database driver behavior, bytecode tooling, and build-plugin requirements.
3. Keep framework families compatible. Treat Spring Boot, Spring Cloud, Spring AI, Hibernate, Jackson, Netty, Kotlin, and test libraries as coordinated sets when their compatibility guidance requires it.
4. Split unrelated upgrades. Upgrade build prerequisites first, the platform next, adapters after that, and application code last.
5. For security remediation, verify the resolved artifact version rather than assuming a direct declaration wins.

## Validate each batch

Run the smallest relevant module tests first, then the full reactor. Include dependency convergence, integration tests, packaging, startup smoke tests, and native or container builds when the project uses them. Compare runtime behavior, not only compilation.

Do not delete tests, exclusions, enforcer rules, or compiler warnings merely to obtain a green build. Explain any temporary compatibility shim and its removal condition.

## Output contract

Provide:

- baseline and target versions with their version owner;
- compatibility constraints and evidence found in the repository;
- an ordered, reviewable change plan;
- exact validation commands and observed results;
- remaining risks, rollback commit or version, and follow-up cleanup.

When context is missing, label assumptions. Do not claim a dependency is upgraded until the resolved tree and relevant tests prove it.

## Reference

Read [references/upgrade-playbook.md](references/upgrade-playbook.md) for Maven inspection commands, version-ownership patterns, and failure triage.
