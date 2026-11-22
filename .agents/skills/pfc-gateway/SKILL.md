---
name: pfc-gateway
description: Diseña, diagnostica o revisa rutas, filtros y pruebas del Gateway WebFlux del PFC y su conexión con NGINX y los servicios.
---

# Gateway del PFC

1. Lee el [POM del Gateway](../../../infrastructure/api-gateway/pom.xml) y su configuración en `src/main/resources`. Usa el BOM de Spring Cloud y la documentación de la versión gestionada de Gateway **Server WebFlux**. Consulta la [matriz de proyectos de Spring Cloud](https://docs.spring.io/spring-cloud-release/reference/spring-projects.html).
2. Traza el recorrido desde [NGINX](../../../infrastructure/platform/nginx.conf) por Gateway al servicio dueño de la operación. Para enrutar o ajustar prefijos, compara el [diseño de plataforma](https://docs.google.com/document/d/19Twh3hXmds-d8IhhxrvqQUpINPeg9pSi3yKotonV4Go/edit), el contrato aplicable y la configuración efectiva. Verifica el path, los encabezados y el código de respuesta que llegan a cada frontera.
3. Mantén el enrutamiento separado de las comprobaciones de autorización del servicio. Distingue la configuración y las pruebas reactivas del Gateway de las aplicaciones Spring MVC. Para rutas y filtros reactivos, considera `WebTestClient` y un destino de prueba controlado que permita observar el path y la respuesta.
4. Usa la [guía del entorno central](../../../infrastructure/platform/README.md) y la [guía del auxiliar](../../../infrastructure/auxiliary/README.md) para seleccionar manifiestos y redes. En desarrollo, la entrada publicada es NGINX por HTTP local. Aplica HTTPS al despliegue de la demo y al intercambio con Stations externas según el diseño y el contrato.
