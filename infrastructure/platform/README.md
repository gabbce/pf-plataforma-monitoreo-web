# Entorno Docker central del Prototipo 1

El proyecto Compose `pfc-platform` construye tres imágenes Java, una por
aplicación, y una imagen NGINX con los archivos estáticos del frontend. Los JAR
se construyen con Maven Wrapper y Temurin 25 LTS. El frontend se construye con
Node.js 26.13.1 y npm 12.2.3 a partir de su lockfile. Cada base utiliza una imagen
oficial y volúmenes propios. Las imágenes base se fijan por versión y digest.

| Servicio | Dirección dentro de la red | Volúmenes |
|---|---|---|
| Gestión | `management:8081` | |
| Registros | `records:8082` | |
| Gateway | `gateway:8080` | |
| NGINX con el frontend | `nginx:80` | |
| PostgreSQL 19.0 de Gestión | `postgres:5432` | `postgres_data` |
| MongoDB 9.0.8 de Registros | `mongodb:27017` | `mongo_data` y `mongo_config` |

Gestión y Registros arrancan tras la comprobación de disponibilidad de su
base. El uso de datos por las aplicaciones corresponde a la implementación
de dominio. En esta etapa los componentes son accesibles desde la red interna
de Docker. La publicación por HTTPS y el enrutamiento de API corresponden
a [P1-02.3](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues/13).

## Preparación local

Usar Docker Engine y el plugin Compose en Linux o WSL, con acceso a los
registros de imágenes y dependencias durante la construcción. Ejecutar los
comandos desde la raíz del repositorio. Se necesita `openssl` para generar
las contraseñas locales de inicialización.

```sh
(
  set -eu
  umask 077
  install -d -m 700 .runtime/platform/secrets
  for secret in postgres-password mongo-password; do
    file=".runtime/platform/secrets/$secret"
    if [ ! -e "$file" ]; then
      (set -C; openssl rand -hex 32 > "$file")
    fi
    if [ ! -s "$file" ]; then
      printf 'Archivo de secreto vacío: %s\n' "$file" >&2
      exit 1
    fi
    chmod 0444 "$file"
  done
)
```

El directorio tiene acceso restringido al usuario local. Docker monta cada
archivo en modo de lectura solo en su base, donde el proceso de arranque
puede leerlo tras bajar privilegios. Mantener estos archivos junto a los
volúmenes inicializados para reiniciar con las mismas credenciales.

## Construcción, arranque y diagnóstico

```sh
docker compose -f infrastructure/platform/compose.yaml config --quiet
docker compose -f infrastructure/platform/compose.yaml up --build --wait --wait-timeout 180
docker compose -f infrastructure/platform/compose.yaml ps
```

Para consultar el frontend y los puntos de salud desde la red interna:

```sh
docker compose -f infrastructure/platform/compose.yaml exec -T nginx wget -qO- http://nginx/
docker compose -f infrastructure/platform/compose.yaml exec -T nginx wget -qO- http://management:8081/actuator/health
docker compose -f infrastructure/platform/compose.yaml exec -T nginx wget -qO- http://records:8082/actuator/health
docker compose -f infrastructure/platform/compose.yaml exec -T nginx wget -qO- http://gateway:8080/actuator/health
```

Consultar los logs del servicio que se quiere diagnosticar. Por ejemplo:

```sh
docker compose -f infrastructure/platform/compose.yaml logs --tail 100 postgres
```

Para detener el proyecto y conservar los volúmenes:

```sh
docker compose -f infrastructure/platform/compose.yaml down
```
