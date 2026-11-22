# Guía del backend

El [contrato web](contratos/web/openapi.yaml) describe seis operaciones de
acceso y consulta del P1. Sus [ejemplos JSON](contratos/web/ejemplos/) sirven
como entradas compartidas para escenarios del frontend y futuras pruebas de
las API. Cada archivo es un objeto Example de OpenAPI. El campo `value`
contiene el cuerpo o valor HTTP y la descripción identifica el escenario.

## Responsabilidades y convenciones

El Maven Wrapper de la raíz compila el reactor con Java 25. Para ejecutar
solo las pruebas de un módulo, usa `./mvnw -pl <ruta-del-módulo> -am verify`.
Las versiones, los paquetes y el arranque directo de cada aplicación están
en la [guía de la base Java](base-java-p1-01-1.md).

| Módulo | Responsabilidad y criterio de prueba |
| --- | --- |
| `backend/management` | Gestión es dueña de identidad, sesión, máquinas, permisos, catálogos y configuración en PostgreSQL. Comprueba sesión y autorización por rol y asociación. Representa el chasis como texto y conserva los ceros iniciales. |
| `backend/records` | Registros conserva los originales en MongoDB, confirma envíos durables y responde consultas operativas. Valida el alcance accediendo a Gestión por API. Conserva ocurrencia y primera recepción en UTC y resuelve el contexto histórico. |
| `infrastructure/api-gateway` | Gateway concentra la entrada, usa WebFlux y dirige cada operación al servicio responsable. Las pruebas de rutas comprueban destino, método y parámetros. Gestión y Registros mantienen sus propios controles de acceso. |
| `station/auxiliary` | El auxiliar usa SQLite en su proyecto Compose independiente. Mantén el aislamiento por Instancia al incorporar los escenarios y verifica recuperación y reenvío de pendientes. El auxiliar se comunica con el central mediante la entrada publicada por NGINX. |

Usa [las skills del backend](skills-backend.md) desde la raíz. Para revisar
un cambio, aplica primero las reglas del PFC y después las skills del stack
pertinentes al módulo.

## Verificar el contrato y los ejemplos

Desde la raíz, con Node.js y npm. La combinación del frontend, Node.js
26.13.1 y npm 12.2.3, sirve también para esta herramienta.

```sh
npm ci --prefix tools/web-contract
npm run verify --prefix tools/web-contract
```

El lockfile de la herramienta fija Redocly CLI 2.82.0. La verificación valida
la estructura, las referencias y los esquemas de los ejemplos, y reúne el
contrato en un archivo temporal para comprobar que todos los ejemplos
versionados están referenciados. Comprueba además que el token CSRF coincide
en ambos ejemplos, que el chasis conserva sus ceros, que la corrida de dos
variables reúne veinte originales en diez instantes y que las respuestas de
histórico y lecturas recientes son coherentes. La corrida principal, los
resultados vacíos, la lectura inválida y los ejemplos con confirmación tardía
se pueden consumir como escenarios independientes según la descripción de
cada ejemplo.

## Compilar y revisar el entorno

```sh
./mvnw clean verify
docker compose -f infrastructure/platform/compose.yaml config --quiet
docker compose -f infrastructure/auxiliary/compose.yaml config --quiet
```

Para iniciar los proyectos y probar la entrada HTTP y las sondas de salud,
sigue las guías del [entorno central](../infrastructure/platform/README.md) y
del [auxiliar](../infrastructure/auxiliary/README.md). Ejecuta las peticiones
web locales contra `http://127.0.0.1:8095`. Las sondas Actuator se consultan
desde la red del proyecto correspondiente. La demo y las Stations externas
usan HTTPS. Aplica el perfil de autenticación local definido en P1-03 y usa
un almacén de cookies de prueba por sesión.

## Comprobar los recorridos funcionales

Ejecuta estos escenarios al implementar P1-03 a P1-06. Usa datos y
credenciales de prueba en el entorno local. Ejecuta las pruebas del módulo
mediante el Wrapper y repite los recorridos integrados por NGINX con ambos
proyectos Compose disponibles.

1. **Autorización web.** Obtén el token de `GET /api/v1/auth/csrf` y envíalo
   con el contexto previo a `POST /api/v1/auth/session`. Consulta la lista,
   el detalle y las mediciones con la cookie resultante. Verifica el acceso
   por rol y asociación. Repite con sesión ausente o vencida (`401`), y con
   una sesión válida que carezca de acceso al chasis (`403`, también para
   chasis fuera de alcance cuya existencia no pueda revelarse). Comprueba
   el rechazo del ingreso con CSRF inválido y el `200` de las colecciones
   vacías autorizadas.
2. **Persistencia e idempotencia.** Envía los veinte registros de la corrida
   principal desde el auxiliar y cuenta los originales en Registros. Reenvía
   el mismo contenido con los mismos identificadores, por ejemplo tras perder
   la respuesta, y comprueba que siguen siendo veinte. Contrasta
   `ACCEPTED` y `ALREADY_STORED` con la conservación durable. Repite con
   credenciales técnicas inválidas, revocadas o ajenas a la Instancia.
   Verifica que el auxiliar mantiene pendientes al perder conectividad y
   recupera el envío en una sesión con alimentación y red.
3. **Histórico y lecturas recientes.** Usa una consulta por variable con
   intervalo de ocurrencia `[from,to)`. Reutiliza `asOf` en la tabla, todas
   las páginas y las series del gráfico. Verifica orden por ocurrencia e
   identificador, total, inclusión de `from` y exclusión de `to`. Comprueba
   por separado un registro tardío ocurrido bajo la fuente A y recibido
   después de que entre en vigencia B. Conserva A y la configuración que
   aplicó la Station. Simula además una confirmación posterior al corte con
   recepción previa y comprueba que las páginas de ese corte se mantienen
   estables. El último dato se determina por ocurrencia. La calidad y la
   vigencia se comprueban por separado del último contacto autenticado.

Los envíos de Station se rigen por el
[contrato Station–plataforma](https://docs.google.com/document/d/1gE5UOsgvys2ZfEnKznHS53ZYaSF5zNEU-Wut-d-YLYA/edit).
Al recibir la señal de apagado, la Station deja de iniciar envíos y conserva
los pendientes para una sesión posterior con conectividad. Usa el mismo
`POST /integration/v1/records` para el envío inicial y el reenvío.

El verificador del contrato y las sondas acreditan coherencia documental y
disponibilidad de la base. La evidencia de los recorridos funcionales se
obtiene al ejecutar esos escenarios contra las API implementadas.
