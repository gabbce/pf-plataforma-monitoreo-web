# Base Java de P1-01.1

La base Java reúne cuatro aplicaciones Spring Boot con puntos de entrada
independientes. El alcance de esta etapa es la estructura de aplicaciones, el
empaquetado y la configuración de arranque local. El desarrollo funcional y la
infraestructura de despliegue corresponden a tareas posteriores.
[P1-01.1](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues/8)

## Organización y dependencias

| Aplicación | Directorio | Responsabilidad | Puerto local | Dependencias de ejecución |
|---|---|---|---|---|
| Gestión | `backend/management` | Gestión de usuarios, permisos, máquinas y catálogos | 8081 | Spring Web MVC y Actuator |
| Registros operativos | `backend/records` | Ingesta y consulta de registros operativos | 8082 | Spring Web MVC y Actuator |
| Gateway | `infrastructure/api-gateway` | Entrada a las API de la plataforma | 8080 | Spring Cloud Gateway Server WebFlux y Actuator |
| Auxiliar Station | `station/auxiliary` | Aplicación externa para el generador y el emulador | 8090 | Spring Web MVC y Actuator |

Las responsabilidades indican el propósito de cada módulo. En esta etapa cada
aplicación contiene su punto de entrada, configuración de servidor y endpoint
de salud.

El `pom.xml` raíz cumple el rol de padre y agregador. Fija Java 21 y Spring Boot
4.1.2 para los cuatro módulos. Cada aplicación declara el plugin de Spring Boot
para generar su JAR ejecutable. Gateway importa el BOM de Spring Cloud 2025.1.4
en su propio POM. La raíz contiene un único Maven Wrapper 3.3.4, en modalidad
`only-script`, que fija Maven 3.10.0.

## Generación y adaptación

Los proyectos se generaron como aplicaciones Maven con Java 21, Spring Boot
4.1.2, empaquetado JAR y grupo `ar.edu.utn.frsf.pfc`.
[Spring Initializr](https://start.spring.io)

| Artefacto | Paquete | Selección en Initializr |
|---|---|---|
| `management` | `ar.edu.utn.frsf.pfc.management` | `web,actuator` |
| `records` | `ar.edu.utn.frsf.pfc.records` | `web,actuator` |
| `api-gateway` | `ar.edu.utn.frsf.pfc.gateway` | `cloud-gateway-reactive,actuator` |
| `station-auxiliary` | `ar.edu.utn.frsf.pfc.station.auxiliary` | `web,actuator` |

Se conservaron las clases principales generadas y se integraron los POM de las
aplicaciones al padre raíz. Las dependencias de persistencia, seguridad y
mensajería se incorporarán en las tareas que implementen esas responsabilidades.
El auxiliar mantiene su propio módulo de aplicación dentro del mismo build.

## Compilación y arranque local

Desde la raíz del repositorio, con JDK 21 configurado en Linux o WSL:

```sh
./mvnw clean package
```

Para iniciar las aplicaciones, ejecutar cada comando en una terminal distinta:

```sh
java -jar backend/management/target/management-0.0.1-SNAPSHOT.jar
java -jar backend/records/target/records-0.0.1-SNAPSHOT.jar
java -jar infrastructure/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
java -jar station/auxiliary/target/station-auxiliary-0.0.1-SNAPSHOT.jar
```

Cada aplicación escucha en `127.0.0.1` y en el puerto indicado en la tabla.
`SERVER_ADDRESS` y `SERVER_PORT` permiten cambiar esos valores.
Actuator expone `/actuator/health` con el estado general de la aplicación.
