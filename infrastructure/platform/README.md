# Entorno Docker central del Prototipo 1

El proyecto Compose `pfc-platform` construye Gestión, Registros y Gateway con
Maven Wrapper y Temurin 25 LTS. El frontend se construye con Node.js 26.13.1 y
npm 12.2.3 a partir de su lockfile y se sirve con NGINX 1.30.7. Las imágenes
base y de bases de datos se fijan por versión y digest.

| Servicio | Dirección interna | Volúmenes |
|---|---|---|
| Gestión | `management:8081` | |
| Registros | `records:8082` | |
| Gateway | `gateway:8080` | |
| NGINX con el frontend | `nginx:80` | |
| PostgreSQL 19.0 de Gestión | `postgres:5432` | `postgres_data` |
| Provisión de PostgreSQL para Gestión | Proceso de ejecución única | |
| MongoDB 9.0.8 de Registros | `mongodb:27017` | `mongo_data` y `mongo_config` |
| Provisión de MongoDB para Registros | Proceso de ejecución única | |

Gestión y Registros esperan que su base esté disponible y que termine su
aprovisionamiento. NGINX es el único servicio con un puerto publicado. Escucha en el host
en `127.0.0.1:8095` y se conecta tanto a la red de entrada como a la privada.
El [auxiliar](../auxiliary/README.md) conserva su propia red y usa la publicación
de NGINX a través del host en Docker Desktop. El resto del central solo usa su
red interna. Solo Gestión y su proceso de provisión comparten con PostgreSQL
la red privada `management-db`. Solo Registros y su proceso de provisión
comparten con MongoDB la red privada `records-db`.

NGINX sirve el frontend y reenvía `/api` y `/integration` a Spring Cloud
Gateway. Gateway conserva el método, la ruta y los parámetros. Las rutas web
de ingreso, listado y detalle de máquinas llegan a Gestión. Registros recibe
DATA en `POST /integration/v1/records`. Gateway reserva para Gestión las rutas
`GET /integration/v1/configuration` y `POST /integration/v1/configuration-results`.
Las sondas Actuator de los servicios son internas.

## Arranque diario

Desde la raíz, en Linux o WSL, usa `make dev` para preparar la configuración,
construir y arrancar el entorno central y cargar la máquina ficticia y las
cuentas de prueba. Abre `http://localhost:8095/` y consulta las credenciales
con `make cuentas`.

| Comando | Operación |
| --- | --- |
| `make estado` | Muestra el estado de los servicios. |
| `make logs` | Sigue los registros de todos los servicios. |
| `make logs SERVICIO=management` | Sigue los registros de Gestión. |
| `make parar` | Detiene el entorno y conserva los datos. |

El [archivo de cuentas de desarrollo](cuentas-desarrollo.env) define en texto
plano la contraseña ficticia `pfc-desarrollo` para las tres cuentas locales.
Make le pasa este archivo solo al proceso de carga de cuentas.
Para reutilizar contraseñas ya cargadas en un volumen propio, guarda los valores
correspondientes en un archivo local con las mismas variables y ejecuta
`make dev CUENTAS=/ruta/al/archivo.env`. La carga rechaza datos incompatibles y
conserva las cuentas existentes.

## Preparación manual

Usar Docker Engine y el plugin Compose en Linux o WSL, con acceso a los
registros de imágenes y dependencias durante la construcción. Ejecutar los
comandos desde la raíz del repositorio, ubicado en el sistema de archivos de
Linux o WSL para conservar los permisos. Los comandos de preparación requieren
OpenSSL. El resguardo cifrado requiere GnuPG.

```sh
./infrastructure/platform/prepare-local-config.sh
```

El script genera las contraseñas de inicialización de PostgreSQL y MongoDB,
las contraseñas de base de Gestión y Registros y el secreto de servicio para
que Registros consulte a Gestión si faltan. Conserva los archivos existentes.
Los secretos usan modo `0444` dentro
del directorio `0700` para que los procesos puedan leer sus archivos montados en
solo lectura después de bajar privilegios.

Abrir `http://localhost:8095/` desde el host Docker. Esta entrada HTTP
corresponde al entorno de desarrollo local. El despliegue de la demo y la
conexión de Stations externas requieren HTTPS.

## Gestión y PostgreSQL

El servicio `provision-management` usa la contraseña administrativa de
PostgreSQL para preparar el rol `management_app`, propietario del esquema
`management`, e instalar `btree_gist` en `public`. Verifica la conexión por
SCRAM, los propietarios y los privilegios. El contenedor de Gestión se conecta
con la contraseña de su propio rol y ejecuta las migraciones Flyway al arrancar.

Para un volumen previo al aprovisionamiento, volver a ejecutar
`prepare-local-config.sh` y arrancar el central con los archivos y el volumen
existentes. Si el rol ya existe, conservar la contraseña que se usó al crearlo.
La provisión detecta inconsistencias y se detiene. Para diagnosticar el proceso:

```sh
docker compose -f infrastructure/platform/compose.yaml logs provision-management
```

Para ejecutar el JAR fuera de Compose, configurar `SPRING_DATASOURCE_URL` con
la URL JDBC de la base `management` aprovisionada y accesible desde ese proceso,
`SPRING_DATASOURCE_USERNAME=management_app` y `SPRING_DATASOURCE_PASSWORD` con
el valor de su secreto. El Compose central mantiene PostgreSQL en su red privada.

El arranque habitual crea el esquema de tablas mediante Flyway. Cargar por
separado la máquina ficticia y el catálogo de demostración después de arrancar
el central:

```sh
docker compose -f infrastructure/platform/compose.yaml run --rm management load-demo-fixture
```

Para un JAR con las variables de conexión ya configuradas:

```sh
java -jar backend/management/target/management-0.0.1-SNAPSHOT.jar load-demo-fixture
```

El comando usa un proceso sin servidor HTTP. Carga el chasis textual `000123`
y asocia `engine.rpm` con `rpm-b` y `engine.coolant_temperature` con `coolant-a`
desde el 15/01/2026 a las 12:00 UTC. La transacción audita cada alta efectiva.
La repetición idéntica conserva filas y auditoría. Si encuentra una fila
incompatible, revierte la carga.

### Acceso web de demostración

Gestión guarda las cuentas y sus permisos en su PostgreSQL privado. Cargar
las cuentas después del catálogo, desde Bash. Ingresar contraseñas de
demostración propias cuando se soliciten y conservarlas fuera del
repositorio y de las imágenes.

| Identificador | Acceso a máquinas | Variable de contraseña |
|---|---|---|
| `tecnico-ejemplo` | Servicio Técnico con alcance global | `PFC_DEMO_TECHNICIAN_PASSWORD` |
| `propietario-ejemplo` | Propietario de `000123` desde el 15/01/2026 a las 12:00 UTC | `PFC_DEMO_OWNER_PASSWORD` |
| `propietario-sin-asociacion` | Propietario con listado vacío | `PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD` |

```bash
read -rs -p 'Contraseña de Servicio Técnico: ' PFC_DEMO_TECHNICIAN_PASSWORD
printf '\n'
read -rs -p 'Contraseña del Propietario asociado: ' PFC_DEMO_OWNER_PASSWORD
printf '\n'
read -rs -p 'Contraseña del Propietario sin asociación: ' PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD
printf '\n'
export PFC_DEMO_TECHNICIAN_PASSWORD PFC_DEMO_OWNER_PASSWORD PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD
docker compose -f infrastructure/platform/compose.yaml run --rm \
  -e PFC_DEMO_TECHNICIAN_PASSWORD -e PFC_DEMO_OWNER_PASSWORD \
  -e PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD management load-demo-web-accounts
unset PFC_DEMO_TECHNICIAN_PASSWORD PFC_DEMO_OWNER_PASSWORD PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD
```

Este procedimiento manual permite usar contraseñas elegidas al crear las
cuentas. Para el flujo diario, usa el [arranque diario](#arranque-diario).

Las contraseñas admiten hasta 72 bytes UTF-8 y deben contener algún carácter
distinto de espacio en blanco. El comando registra las altas efectivas y
verifica el estado y los hashes de las cuentas existentes. Una repetición con
las mismas contraseñas y datos conserva el contenido y la auditoría. Una
colisión revierte la carga. Para cargar desde un JAR con las variables de
conexión y contraseñas ya exportadas, ejecutar
`java -jar backend/management/target/management-0.0.1-SNAPSHOT.jar load-demo-web-accounts`.

El cliente solicita `GET /api/v1/auth/csrf` y envía la cookie del contexto y
el token en `X-CSRF-TOKEN` al hacer `POST /api/v1/auth/session`. Al ingresar,
Gestión consume el contexto y establece una cookie nueva `PFC_SESSION`. Las
cookies usan `Secure`, `HttpOnly`, `SameSite=Lax` y `Path=/api/v1`. Usar el
host `localhost` para las verificaciones HTTP locales y HTTPS en la demo.
El contexto previo vence a los 5 minutos y la sesión a los 30 minutos del
ingreso. Son valores del P1 configurables en el entorno del proceso de Gestión
mediante `PFC_WEB_CSRF_TTL` y `PFC_WEB_SESSION_TTL`. La API resuelve el
permiso y la asociación vigentes en cada consulta. El campo
`lastAuthenticatedContactAt` refleja el último contacto autenticado de la
máquina. Se devuelve `null` hasta que Gestión registra el primero.

Para cerrar la sesión, el cliente solicita `GET /api/v1/auth/csrf?purpose=logout`
con la cookie `PFC_SESSION` activa y envía el token recibido en `X-CSRF-TOKEN`
al hacer `DELETE /api/v1/auth/session`. Gestión revoca esa sesión y devuelve
`204` con la cookie expirada. Una sesión ausente, inválida, vencida o revocada
recibe `401`. Con una sesión activa, un token ausente, inválido o de otro
contexto recibe `403`.

Para ejecutar las pruebas de Gestión con Docker disponible, usar
`./mvnw -pl backend/management -am verify`. Las pruebas de integración usan
la imagen PostgreSQL fijada por digest y una base separada para el cambio de
vigencia a las 12:05 UTC. Flyway 12.11.0 advierte que PostgreSQL 19 supera su
última versión verificada, PostgreSQL 18.

## Identidad técnica de Station

Gestión conserva en su PostgreSQL el chasis textual, el productor, las operaciones
autorizadas y el verificador de la credencial. Carga primero el catálogo. La
provisión y la revocación se ejecutan por separado del arranque de la plataforma.
En el ejemplo, `station-01` identifica un productor ficticio del chasis `000123`
con permiso para enviar registros (`RECORDS_WRITE`).

Genera una credencial hexadecimal de 32 bytes una sola vez. En Bash, usa una
ruta nueva en un directorio restringido fuera del repositorio para guardarla
cifrada. GnuPG solicita la frase de paso.

```bash
(
  set -euo pipefail
  umask 077
  openssl rand -hex 32 |
    gpg --symmetric --cipher-algo AES256 --output /ruta/protegida/station-01-token.gpg
)
```

Pasa el secreto por la entrada estándar del proceso de provisión.

```bash
(
  set -euo pipefail
  gpg --decrypt /ruta/protegida/station-01-token.gpg |
    docker compose -f infrastructure/platform/compose.yaml run --no-deps --rm -T \
      management provision-station 000123 station-01 RECORDS_WRITE
)
```

El alta devuelve `CREATED`. Repetirla con la misma credencial y operaciones
devuelve `ALREADY_EXISTS` mientras está vigente y `ALREADY_REVOKED` si fue
revocada. Un secreto o alcance incompatible, incluido un secreto vinculado a
otra identidad, detiene el comando con error y conserva los datos previos.

Para revocar la credencial, indica el chasis y el productor.

```sh
docker compose -f infrastructure/platform/compose.yaml run --no-deps --rm -T \
  management revoke-station 000123 station-01
```

El comando devuelve `REVOKED` en la primera ejecución y `ALREADY_REVOKED`
en las siguientes. Gestión audita el alta y la revocación efectivas. En este
paso se conserva una credencial por par Instancia/productor.

## Consulta interna de autorización desde Registros

Registros consulta a Gestión en `POST /internal/v1/station-authorizations/check`
por la red privada del entorno central. NGINX y Gateway excluyen esa ruta.
El cliente toma la URL de `PFC_INTERNAL_MANAGEMENT_URL`, configurada como
`http://management:8081` en Compose local. Registros y Gestión reciben el
archivo `records-to-management-token` bajo `pfc.internal.records-token`.
Registros usa ese secreto como credencial `Authorization: Bearer` de servicio.

El cuerpo JSON lleva `instanceId`, `producerId`, `operation` y `stationToken`.
El cliente solicita `RECORDS_WRITE`. Gestión autentica al servicio antes de
leer el cuerpo de hasta 4 KiB y comprueba la credencial de Station y su alcance
en cada pedido.

| Respuesta | Significado |
| --- | --- |
| `204` | Registros puede usar la credencial para la operación solicitada. |
| `401` | Falta la autenticación de Registros o su credencial es inválida. |
| `403` | La identidad técnica de Station o la operación quedan fuera del alcance autorizado. |
| `400` | La solicitud autenticada tiene un formato inválido. |
| `503` | Gestión no pudo consultar la autorización. |

El cliente distingue una denegación de Station de un problema de comunicación o
configuración. Para la recepción DATA usa la operación privada
`POST /internal/v1/station-authorizations/check-and-record-contact`, con el
mismo cuerpo y tratamiento de estados. Gestión devuelve `204` cuando autoriza
al productor y confirma el contacto con hora UTC del servidor. Una denegación
deja ese instante intacto. Registros tampoco procesa el lote si Gestión falla
al confirmar ese contacto. El máximo por máquina se devuelve en el listado y
el detalle web.

## Consulta interna de contexto DATA

Registros consulta `POST /internal/v1/data-context/resolve` en Gestión con la
misma URL y credencial de servicio. La ruta queda fuera de NGINX y Gateway.
La consulta usa la definición DATA del Tipo asociado al chasis textual.

```json
{
  "instanceId": "000123",
  "definitionId": "engine.rpm",
  "timeValidity": "VALID",
  "occurredAt": "2026-01-15T12:00:00Z"
}
```

La respuesta `200` incluye `typeId`, `definitionId`, `subsystemId`,
`valueType`, `unit` y `sourceId`. El subsistema puede ser nulo.
Con `VALID`, Gestión exige una marca UTC terminada en `Z` y busca la
fuente vigente en ese instante, con el inicio inclusivo y el fin exclusivo.
Con `INVALID`, la marca puede ser nula y `sourceId` es nulo. La conservación
de la marca original y la clasificación `REVIEW` corresponden a Registros
durante la recepción.

Gestión devuelve `400` si la solicitud es inválida, `401` si falla la
autenticación interna, `404` si falta el contexto requerido y `503` si
el catálogo está indisponible. Registros distingue la entrada inválida y
el contexto inexistente de los fallos de comunicación o de servicio.

## Registros y MongoDB

`provision-records` comprueba o prepara la cuenta `records_app` y el índice
único por `(instanceId, recordId)` en `records.operational_records`. La cuenta
de la aplicación puede leer, insertar y actualizar esa colección. Conserva
los secretos al arrancar de nuevo con los volúmenes existentes. El proceso se
detiene si encuentra un rol, una contraseña o un índice incompatibles.

```sh
docker compose -f infrastructure/platform/compose.yaml logs provision-records
```

Registros confirma la inserción con escritura en el journal de MongoDB.
Al reconocer un reenvío o detectar un conflicto, verifica la durabilidad del
original mediante una actualización efectiva de un metadato técnico del mismo
documento. El contenido contractual, el contexto y la primera recepción
permanecen intactos. Si falla esa confirmación, el resultado interno
queda disponible para reintentar. El límite por operación de MongoDB
es de 5 segundos y se configura con `pfc.records.storage-operation-timeout-ms`.

## Recepción de DATA

Station envía un objeto JSON con el arreglo `records` a
`POST /integration/v1/records` usando su credencial `Authorization: Bearer`.
En el despliegue, la entrada es HTTPS. La respuesta `200` contiene el arreglo
`results`, en el orden del envío.

| Estado por registro | Significado |
| --- | --- |
| `ACCEPTED` | MongoDB confirmó un original nuevo. |
| `ALREADY_STORED` | MongoDB confirmó un original existente con el mismo contenido. |
| `REJECTED` | El elemento tiene un error definitivo y la respuesta incluye el motivo. |
| `RETRY` | Un impedimento transitorio deja el registro pendiente de reenvío. |

Cada resultado lleva el `recordId` original. Las confirmaciones incluyen
`storageClass` con `OPERATIONAL` o `REVIEW`. El reenvío mantiene contenido,
identificador y ocurrencia, y conserva la primera recepción. Un registro con
tiempo inválido conserva la marca enviada o `null` y queda en `REVIEW`.

La ruta devuelve `400` si el lote no se puede interpretar o contiene un
elemento sin identificador utilizable, `401` si falta o es inválido el formato
de la credencial, `403` si no autoriza el ámbito y `503` si Gestión no está
disponible para autorizar y registrar el contacto. El límite de entrada es
de 65 536 bytes y 100 registros, configurable con
`pfc.records.ingestion.max-body-bytes` y `pfc.records.ingestion.max-records`.
Un lote demasiado grande devuelve `413` y un tipo de contenido distinto de
JSON devuelve `415`.

## Resguardo de configuración

Conservar una copia cifrada de la configuración junto al procedimiento de
recuperación de los volúmenes. Desde Bash, elegir una ruta nueva para el
archivo `resguardo` en un directorio restringido fuera del repositorio.
GnuPG solicita la frase de paso de forma interactiva.

```bash
(
  set -euo pipefail
  umask 077
  resguardo=/ruta/protegida/pfc-configuracion.tar.gpg
  tar -C .runtime/platform -cf - secrets |
    gpg --symmetric --cipher-algo AES256 --output "$resguardo"
)
```

Para recuperar los archivos, descomprimir la copia propia en un directorio
vacío y restringido. Revisar permisos y correspondencia con el entorno antes
de copiarla a `.runtime/platform`. Conservar las contraseñas asociadas a los
volúmenes ya inicializados.

```bash
(
  set -euo pipefail
  umask 077
  resguardo=/ruta/protegida/pfc-configuracion.tar.gpg
  recuperacion=$(mktemp -d)
  gpg --decrypt "$resguardo" | tar -xpf - -C "$recuperacion"
  printf '%s\n' "$recuperacion"
)
```

Este resguardo contiene las contraseñas de inicialización y la de Gestión. El respaldo de datos
de PostgreSQL, MongoDB y SQLite requiere su propia operación.

## Construcción, arranque y diagnóstico

```sh
docker compose -f infrastructure/platform/compose.yaml config --quiet
docker compose -f infrastructure/platform/compose.yaml up --build --wait --wait-timeout 180
docker compose -f infrastructure/platform/compose.yaml ps
```

Para consultar el frontend desde el host Docker:

```sh
curl -fsS http://localhost:8095/
```

Para consultar las sondas internas de las aplicaciones Java:

```sh
docker compose -f infrastructure/platform/compose.yaml exec -T nginx curl -fsS http://management:8081/actuator/health
docker compose -f infrastructure/platform/compose.yaml exec -T nginx curl -fsS http://records:8082/actuator/health
docker compose -f infrastructure/platform/compose.yaml exec -T nginx curl -fsS http://gateway:8080/actuator/health
```

Para diagnosticar un servicio, consultar su estado con `ps` y revisar sus
logs. Por ejemplo:

```sh
docker compose -f infrastructure/platform/compose.yaml logs --tail 100 postgres
```

## Parada y recreación

La parada temporal conserva los contenedores. Para detenerlos y volver a
arrancar:

```sh
docker compose -f infrastructure/platform/compose.yaml stop
docker compose -f infrastructure/platform/compose.yaml start --wait
```

Con las imágenes ya construidas, recrear los contenedores con:

```sh
docker compose -f infrastructure/platform/compose.yaml up --no-build --force-recreate --wait --wait-timeout 180
```

Para retirar los contenedores y las redes del central, conservar sus volúmenes
nombrados con el siguiente comando:

```sh
docker compose -f infrastructure/platform/compose.yaml down
```

Usar el mismo nombre de proyecto Compose, los mismos volúmenes y las
contraseñas asociadas para volver a montar los datos existentes. Si se indicó
un nombre con `-p`, repetirlo en cada operación. La opción `down --volumes`
elimina los volúmenes del proyecto. La recreación conserva los datos en el
mismo almacenamiento local. El respaldo independiente requiere una copia
separada de los datos y un procedimiento de restauración.
