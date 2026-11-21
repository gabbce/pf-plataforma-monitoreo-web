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
| MongoDB 9.0.8 de Registros | `mongodb:27017` | `mongo_data` y `mongo_config` |

Gestión y Registros arrancan tras la comprobación de disponibilidad de su
base. NGINX es el único servicio con un puerto publicado. Escucha en el host
en `127.0.0.1:8095` y se conecta tanto a la red de entrada como a la privada.
El [auxiliar](../auxiliary/README.md) conserva su propia red y usa la publicación
de NGINX a través del host en Docker Desktop. El resto del central solo usa su
red interna.

NGINX sirve el frontend y reenvía `/api` y `/integration` a Spring Cloud
Gateway. Gateway conserva el método, la ruta y los parámetros. Reenvía
`POST /integration/v1/records` a Registros y
`GET /integration/v1/configuration` y
`POST /integration/v1/configuration-results` a Gestión. Los servicios de
destino incorporarán los controladores de esas operaciones en sus cards.
Las sondas Actuator de los servicios son internas.

## Preparación local

Usar Docker Engine y el plugin Compose en Linux o WSL, con acceso a los
registros de imágenes y dependencias durante la construcción. Ejecutar los
comandos desde la raíz del repositorio, ubicado en el sistema de archivos de
Linux o WSL para conservar los permisos. Los comandos de preparación requieren
OpenSSL. El resguardo cifrado requiere GnuPG.

```sh
./infrastructure/platform/prepare-local-config.sh
```

El script genera las contraseñas de inicialización si faltan y conserva
las existentes. Los archivos de las contraseñas usan modo `0444` dentro del
directorio `0700` para que cada imagen de base pueda leer su secreto montado
en solo lectura después de bajar privilegios.

Abrir `http://localhost:8095/` desde el host Docker. Esta entrada HTTP
corresponde al entorno de desarrollo local. El despliegue de la demo y la
conexión de Stations externas requieren HTTPS.

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

Este resguardo contiene las contraseñas de inicialización. El respaldo de datos
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
