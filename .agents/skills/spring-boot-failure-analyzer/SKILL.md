---
name: spring-boot-failure-analyzer
description: Diagnose Spring Boot application startup and ApplicationContext failures. Use when logs or code show BeanCreationException, circular dependencies, configuration binding or validation errors, missing beans, failed conditional auto-configuration, profile or property problems, or port conflicts; do not use for steady-state JVM performance incidents.
---

# Spring Boot Failure Analyzer

Find the first actionable cause of a Spring Boot startup failure, propose the smallest safe change, and define a command that proves the fix.

## Workflow

1. Preserve the complete exception chain and the first `APPLICATION FAILED TO START` block. Treat the final exception as a symptom until the earliest application-owned cause is identified.
2. Identify the build tool, Java version, Spring Boot version, active profiles, launch command, and whether the failure reproduces in a test.
3. Classify the failure as configuration binding, bean construction, dependency cycle, conditional auto-configuration, classpath linkage, external resource, web-server binding, or test-slice mismatch.
4. Inspect only the files that can participate in that category. Prefer source, configuration, and build metadata over guesses based on exception names.
5. Gather one decisive piece of evidence. Use a focused test, `--debug` condition report, property binding trace, dependency tree, or port/process check as appropriate.
6. Propose one minimal fix. Preserve the application's intended boundaries; do not mask the failure with broad exclusions, optional injection, lazy initialization, or a disabled validation rule unless that behavior is explicitly intended.
7. Re-run the narrowest failing command, then one broader build check. Report both results.

## Evidence rules

- Separate observations from hypotheses.
- Quote the property path, bean name, condition, missing class, or occupied port that proves the diagnosis.
- Prefer the deepest application-owned `Caused by` entry over framework wrapper exceptions.
- Check whether environment variables override YAML or properties before editing a configuration file.
- For `NoSuchMethodError`, `ClassNotFoundException`, and `NoClassDefFoundError`, inspect the resolved dependency graph and BOM alignment before changing application code.
- For missing beans, inspect component scanning, configuration conditions, qualifiers, generics, and test slices before adding a new bean.
- For cycles, identify the architectural dependency loop. Do not recommend `spring.main.allow-circular-references=true` as the primary fix.

## Output contract

Return these sections:

1. **Root cause** — one sentence with confidence level.
2. **Evidence** — the smallest log or code facts that establish it.
3. **Minimal fix** — exact files and changes, or a patch when requested.
4. **Verification** — commands and expected successful signal.
5. **Secondary findings** — only independent issues that remain after the root cause.

Do not claim success unless the verification command was run. If execution is unavailable, label the fix as unverified.

## Reference

Read [references/diagnostic-playbook.md](references/diagnostic-playbook.md) when choosing category-specific checks or interpreting common Spring wrapper exceptions.
