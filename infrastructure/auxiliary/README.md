# Entorno Docker del auxiliar de Station

El proyecto Compose `pfc-auxiliary` ejecuta el módulo `station/auxiliary` en
una red propia, sin publicar puertos ni montar volúmenes del entorno central.
En Docker Desktop llega a la entrada publicada por NGINX a través del host
(`http://host.docker.internal:8095/`). El estado de salud local depende
del auxiliar y su SQLite, de forma que la parada del central permite
seguir operando el auxiliar.

La imagen se construye con Maven Wrapper y Eclipse Temurin 25. El proceso Java
se ejecuta con UID y GID `10001`. El volumen `pfc-auxiliary_sqlite_data` se
monta en `/var/lib/station`, donde el auxiliar abre `station.sqlite` con
SQLite JDBC 3.54.1.0. La ruta se define mediante la propiedad
`station.storage.sqlite-path`. La inicialización crea un archivo SQLite válido
si falta y la comprobación de salud revisa su disponibilidad.

## Arranque y diagnóstico

Ejecutar desde la raíz del repositorio, con Docker en ejecución.
El proyecto auxiliar se puede iniciar de forma independiente.

```sh
docker compose -f infrastructure/auxiliary/compose.yaml up --build --wait
docker compose -f infrastructure/auxiliary/compose.yaml ps
docker compose -f infrastructure/auxiliary/compose.yaml logs --tail 100 auxiliary
```

Para consultar la salud local o comprobar la conectividad HTTP cuando el
central esté disponible:

```sh
docker compose -f infrastructure/auxiliary/compose.yaml exec -T auxiliary curl -fsS http://127.0.0.1:8090/actuator/health
docker compose -f infrastructure/auxiliary/compose.yaml exec -T auxiliary curl -fsS http://host.docker.internal:8095/
```

## Parada y recreación

Para detener temporalmente el auxiliar y volver a arrancarlo:

```sh
docker compose -f infrastructure/auxiliary/compose.yaml stop
docker compose -f infrastructure/auxiliary/compose.yaml start --wait
```

Con la imagen ya construida, recrear el contenedor con:

```sh
docker compose -f infrastructure/auxiliary/compose.yaml up --no-build --force-recreate --wait
```

Para retirar el contenedor y su red, conservar el volumen SQLite con:

```sh
docker compose -f infrastructure/auxiliary/compose.yaml down
```

Mantener el mismo nombre de proyecto Compose al iniciar o recrear el
contenedor permite volver a montar el mismo volumen. Si se indicó un nombre
con `-p`, repetirlo en cada operación. La opción `down --volumes` elimina
el volumen SQLite del proyecto. Conservar el volumen mantiene el archivo
`station.sqlite` en el mismo almacenamiento. El respaldo independiente
requiere una copia separada y un procedimiento de restauración.
