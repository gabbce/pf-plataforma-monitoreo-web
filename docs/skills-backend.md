# Skills del backend

Las skills del backend están en [`.agents/skills/`](../.agents/skills/).
Codex las descubre al abrir el repositorio desde su raíz. El catálogo cubre la
plataforma y el auxiliar de Station durante los incrementos del proyecto.

## Uso en Codex

Abre Codex en la raíz y usa `/skills` en el CLI o escribe `$` y el nombre de la
skill en el selector. En tareas ordinarias Codex puede seleccionarla por su
descripción. Si hay dos skills de igual nombre, comprueba que la ruta elegida
pertenezca al repositorio. Para verificar descubrimiento por API, consulta
`skills/list` con el directorio raíz en `cwds` y `forceReload: true`.

Una tarea puede combinar una skill del stack con la regla específica del PFC.
Por ejemplo, usa `$pfc-gateway` y `$spring-webflux-testing` al preparar una
prueba de enrutamiento. Usa `$postgresql-table-design` para diseñar restricciones,
`$sql-code-review` para revisar el SQL y `$postgresql-optimization` cuando se
necesite analizar un plan de ejecución.

## Catálogo

Cada nombre enlaza a las instrucciones que descubre Codex. La procedencia y los
términos de redistribución se detallan en la sección de orígenes.

### Java 25

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`java-development`](../.agents/skills/java-development/SKILL.md) | GitHub | Código Java y uso de Maven Wrapper por módulo. |
| [`java-debugging`](../.agents/skills/java-debugging/SKILL.md) | GitHub | Reproducción y depuración de fallos con el JDK y el IDE. |
| [`java-diagnostics`](../.agents/skills/java-diagnostics/SKILL.md) | GitHub | Diagnóstico de JVM, memoria, hilos y arranque. |
| [`java-upgrade`](../.agents/skills/java-upgrade/SKILL.md) | GitHub | Actualización explícita de versión de JDK. |
| [`java-conventions`](../.agents/skills/java-conventions/SKILL.md) | SouJava | Convenciones de Java según la versión declarada. |
| [`141-java-refactoring-with-modern-features`](../.agents/skills/141-java-refactoring-with-modern-features/SKILL.md) | Plinth | Refactorizaciones de Java con APIs compatibles con el JDK del proyecto. |
| [`125-java-concurrency`](../.agents/skills/125-java-concurrency/SKILL.md) | Plinth · adaptación PFC | Concurrencia con Java 25 estable, cancelación, ejecución acotada y cierre. |

### Maven

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`110-java-maven-best-practices`](../.agents/skills/110-java-maven-best-practices/SKILL.md) | Plinth | Revisión del reactor, POM y BOM. |
| [`111-java-maven-dependencies`](../.agents/skills/111-java-maven-dependencies/SKILL.md) | Plinth | Evaluación de dependencias y sus ámbitos. |
| [`112-java-maven-plugins`](../.agents/skills/112-java-maven-plugins/SKILL.md) | Plinth | Revisión e incorporación de plugins Maven. |
| [`114-java-maven-search`](../.agents/skills/114-java-maven-search/SKILL.md) | Plinth | Localización de coordenadas o interpretación de informes de versiones. |
| [`maven-dependency-upgrader`](../.agents/skills/maven-dependency-upgrader/SKILL.md) | Jiangxianze | Plan de actualización y verificación de dependencias, plugins y BOM. |

### Spring Boot y Security

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`java-springboot`](../.agents/skills/java-springboot/SKILL.md) | GitHub | Cambios en servicios existentes, configuración y dependencias. |
| [`create-spring-boot-java-project`](../.agents/skills/create-spring-boot-java-project/SKILL.md) | GitHub | Generación de nuevos componentes con Spring Initializr. |
| [`spring-security`](../.agents/skills/spring-security/SKILL.md) | GitHub | Configuración de seguridad y revisión de MVC frente a WebFlux. |
| [`spring-observability`](../.agents/skills/spring-observability/SKILL.md) | GitHub | Actuator, salud, métricas y registros. |
| [`spring-boot-failure-analyzer`](../.agents/skills/spring-boot-failure-analyzer/SKILL.md) | Jiangxianze | Diagnóstico de fallos de creación del contexto o de configuración. |

### Pruebas

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`java-junit`](../.agents/skills/java-junit/SKILL.md) | GitHub | Pruebas unitarias, parametrización y resultados deterministas. |
| [`java-testcontainers`](../.agents/skills/java-testcontainers/SKILL.md) | GitHub | Integración con motores reales y contenedores aislados. |
| [`spring-boot-testing`](../.agents/skills/spring-boot-testing/SKILL.md) | GitHub | Selección de pruebas segmentadas, integración y comprobaciones HTTP. |
| [`spring-security-testing`](../.agents/skills/spring-security-testing/SKILL.md) | Spring AI Community | Pruebas de autenticación, autorización y protección web. |
| [`spring-webflux-testing`](../.agents/skills/spring-webflux-testing/SKILL.md) | Spring AI Community | Pruebas reactivas con WebTestClient y StepVerifier. |

### PostgreSQL y SQL

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`postgresql-table-design`](../.agents/skills/postgresql-table-design/SKILL.md) | wshobson | Tipos, claves, restricciones e índices de Gestión. |
| [`supabase-postgres-best-practices`](../.agents/skills/supabase-postgres-best-practices/SKILL.md) | Supabase | Reglas portables de integridad e índices para PostgreSQL local. |
| [`postgresql-optimization`](../.agents/skills/postgresql-optimization/SKILL.md) | GitHub | Planes de consulta y diagnóstico específico de PostgreSQL. |
| [`sql-code-review`](../.agents/skills/sql-code-review/SKILL.md) | GitHub | Consultas parametrizadas, permisos, integridad y mantenibilidad. |
| [`sql-optimization`](../.agents/skills/sql-optimization/SKILL.md) | GitHub | Joins, paginación y lotes, con la sintaxis del motor correspondiente. |

### MongoDB

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`mongodb-schema-design`](../.agents/skills/mongodb-schema-design/SKILL.md) | MongoDB · adaptación PFC | Validación y estructura de los documentos de Registros. |
| [`mongodb-connection`](../.agents/skills/mongodb-connection/SKILL.md) | MongoDB | Configuración del driver, conexión y tiempos de espera. |
| [`mongodb-query-optimizer`](../.agents/skills/mongodb-query-optimizer/SKILL.md) | MongoDB | Índices y forma de la consulta. El análisis en vivo requiere acceso autorizado. |
| [`315-frameworks-spring-mongodb`](../.agents/skills/315-frameworks-spring-mongodb/SKILL.md) | Plinth | Repositorios, plantillas e integración de MongoDB con Spring Data. |

### SQLite

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`sqlite-ops`](../.agents/skills/sqlite-ops/SKILL.md) | claude-mods · adaptación PFC | Concurrencia, bloqueos, planes de consulta y operación de archivos SQLite. |

### Docker

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`docker-project-foundations`](../.agents/skills/docker-project-foundations/SKILL.md) | Docker | Incorporación de servicios y estructura inicial de contenedores. |
| [`docker-compose-patterns`](../.agents/skills/docker-compose-patterns/SKILL.md) | Docker | Salud, dependencias, redes y volúmenes de los dos proyectos Compose. |
| [`docker-build-strategies`](../.agents/skills/docker-build-strategies/SKILL.md) | Docker | Builds Java por etapas, caché y contexto de construcción. |
| [`docker-destructive-guardrails`](../.agents/skills/docker-destructive-guardrails/SKILL.md) | Docker | Evaluación del impacto de operaciones sobre imágenes, redes y volúmenes. |

### OpenAPI

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`openapi-spec-generation`](../.agents/skills/openapi-spec-generation/SKILL.md) | wshobson | Diseño y validación de contratos OpenAPI 3.1. |
| [`openapi-to-application-java-spring-boot`](../.agents/skills/openapi-to-application-java-spring-boot/SKILL.md) | GitHub | Generación de interfaces y DTO si se adopta ese flujo para Spring MVC. |

### Reglas del PFC

| Skill | Proveedor | Uso |
| --- | --- | --- |
| [`pfc-fuentes`](../.agents/skills/pfc-fuentes/SKILL.md) | PFC | Consulta del contexto operativo, pestañas canónicas y seguimiento de cada incremento. |
| [`pfc-backend`](../.agents/skills/pfc-backend/SKILL.md) | PFC | Fronteras de servicios, permisos y conservación del contexto histórico. |
| [`pfc-gateway`](../.agents/skills/pfc-gateway/SKILL.md) | PFC | Rutas y pruebas de Gateway Server WebFlux y entrada NGINX. |
| [`pfc-station-sqlite`](../.agents/skills/pfc-station-sqlite/SKILL.md) | PFC | Transacciones JDBC, almacenamiento por Instancia y recuperación del auxiliar. |

## Orígenes y licencias

| Proveedor | Revisión | Licencia |
| --- | --- | --- |
| [Awesome GitHub Copilot](https://github.com/github/awesome-copilot/tree/f10fdb8b2660ce85c98f220b2f1ca24653768a5c) | `f10fdb8b2660` | [MIT](licencias/skills-backend/github-awesome-copilot-LICENSE) |
| [Juan Antonio Breña Moral (Plinth)](https://github.com/jabrena/plinth/tree/6167e206e46246f0c933fdf071c9cbee5af79772) | `0.19.0` · `6167e206e462` | [Apache-2.0](licencias/skills-backend/jabrena-plinth-LICENSE) |
| [SouJava](https://github.com/soujava/agent-skills/tree/7e2201aa18d8bb5335479d87e9eea83aacf3bd26) | `7e2201aa18d8` | [MIT](licencias/skills-backend/soujava-agent-skills-LICENSE) |
| [Jiangxianze](https://github.com/Jiangxianze/spring-agent-skills/tree/f228731681cc37e6abd83058a4d2a6311eba994f) | `f228731681cc` | [Apache-2.0](licencias/skills-backend/jiangxianze-spring-agent-skills-LICENSE) |
| [Spring AI Community](https://github.com/spring-ai-community/spring-testing-skills/tree/eaa61bf86929a4d636b6b971e693b798f749cdbf) | `v1.1.0` · `eaa61bf86929` | [Apache-2.0](licencias/skills-backend/spring-ai-community-spring-testing-skills-LICENSE) |
| [wshobson (licencia a nombre de Seth Hobson)](https://github.com/wshobson/agents/tree/91ff75307f5668916f3eb77d5ba556266c961376) | `91ff75307f56` | [MIT](licencias/skills-backend/wshobson-agents-LICENSE) |
| [Supabase](https://github.com/supabase/agent-skills/tree/544bfc56c89afe2b87b20017a59b2c6e9502a1fb) | `v0.1.9` · `544bfc56c89a` | [MIT](licencias/skills-backend/supabase-agent-skills-LICENSE) |
| [MongoDB](https://github.com/mongodb/agent-skills/tree/6ebabd86ebdbe85f37082e9a7d37e18ebf6ebba0) | `v1.2.1` · `6ebabd86ebdb` | [Apache-2.0](licencias/skills-backend/mongodb-agent-skills-LICENSE) |
| [Docker](https://github.com/docker/skills/tree/8afc2c6ef4e0ec2ea8e4ba024d0b884ea930b926) | `v0.3.1` · `8afc2c6ef4e0` | [Apache-2.0](licencias/skills-backend/docker-skills-LICENSE) |
| [claude-mods (0xdarkmatter)](https://github.com/0xdarkmatter/claude-mods/tree/25a03abe26f6c595243a81104e831ab8c597bdbf) | `25a03abe26f6` | [MIT](licencias/skills-backend/0xdarkmatter-claude-mods-LICENSE) |

Las cuatro skills propias y las tres adaptaciones se mantienen en este
repositorio. Su revisión es el commit de Git que las contiene.

| Adaptación | Origen exacto | Alcance de los cambios |
| --- | --- | --- |
| `125-java-concurrency` | [Plinth `0.19.0` · `6167e206e462`](https://github.com/jabrena/plinth/tree/6167e206e46246f0c933fdf071c9cbee5af79772/skills/125-java-concurrency) | Guía acotada a las APIs estables de Java 25. Incluye los límites del auxiliar y de WebFlux. |
| `mongodb-schema-design` | [MongoDB `v1.2.1` · `6ebabd86ebdb`](https://github.com/mongodb/agent-skills/tree/6ebabd86ebdbe85f37082e9a7d37e18ebf6ebba0/skills/mongodb-schema-design) | Corrección de enlaces relativos en cinco referencias. |
| `sqlite-ops` | [claude-mods `25a03abe26f6`](https://github.com/0xdarkmatter/claude-mods/tree/25a03abe26f6c595243a81104e831ab8c597bdbf/skills/sqlite-ops) | Distinción entre consistencia y durabilidad con WAL y precedencia de la regla de Station. |

## Compatibilidad y límites

- Usa los POM, el Wrapper y los manifiestos del repositorio para determinar las
  versiones y variantes efectivas. Las skills de Spring que muestran ejemplos
  de otro parche de Boot o Spring Cloud requieren contrastar las APIs con el
  BOM vigente. La guía [Java](base-java-p1-01-1.md) describe la base del build.
- Usa la documentación del motor instalado para tipos, índices y planes. Las
  instrucciones de Supabase específicas de su servicio gestionado, identidad o
  API quedan fuera del trabajo con PostgreSQL local. Usa las reglas del PFC para
  las fronteras de cada servicio y para la durabilidad de SQLite. En WAL,
  `synchronous=NORMAL` conserva consistencia, pero puede perder commits ante
  una caída del sistema o un corte de energía. Para Station, el requisito de
  durabilidad guía la configuración del driver y su comprobación.
- Las skills copiadas se usan como instrucciones y referencias. Los módulos,
  herramientas de generación o diagnóstico y MCP que aparezcan como opciones
  se evalúan cuando la tarea requiere incorporarlos.
- En Compose, pasa los manifiestos explícitos según las guías del
  [entorno central](../infrastructure/platform/README.md) y del
  [auxiliar](../infrastructure/auxiliary/README.md). Los scripts Docker de
  origen suponen un directorio con su manifiesto o Dockerfile.

## Restauración y actualización

Un clon de la rama contiene las skills y sus recursos. El [lock compartido](../skills-lock.json) fija el commit y el hash de las 35
copias externas del backend sin cambios. Git restaura las tres adaptaciones y las cuatro skills propias
junto con el resto del repositorio.

Si faltan archivos de las copias externas fijadas en el lock, ejecuta este
comando desde la raíz con Node `>=22.20.0`.

```sh
npx --yes skills@1.9.0 experimental_install --agent codex --frozen-lockfile
```

Para actualizar una skill, elige y revisa la revisión candidata, instala solo la
seleccionada con `--skill`, `--agent codex` y `--copy`, y revisa el cambio del
lock y de los archivos. Registra en esta guía la procedencia y la licencia de
la nueva revisión. Comprueba el descubrimiento y las tareas afectadas antes de
integrar el cambio. Para una adaptación, compara las dos revisiones de origen
y revisa si corresponde conservar cada ajuste local. Actualiza su procedencia
en la tabla de adaptaciones y conserva la copia resultante en Git.
[Comandos de skills 1.9.0](https://github.com/vercel-labs/skills/blob/v1.9.0/docs/commands.md)
