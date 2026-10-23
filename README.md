# Plataforma de monitoreo de una embolsadora de forrajes

Plataforma para recibir, conservar y consultar información operativa de una embolsadora de forrajes, con foco en mantenimiento basado en condición, detección de anomalías y diagnóstico técnico.

Forma parte del Proyecto Final de Carrera de **Gabriel Canavesio**, de Ingeniería en Sistemas de Información en la **Universidad Tecnológica Nacional, Facultad Regional Santa Fe, Argentina**.

## Estado del proyecto

El repositorio se encuentra en etapa de preparación para la implementación. Actualmente contiene las indicaciones de trabajo y la configuración del acceso al código de referencia.

La arquitectura, las tecnologías y el contrato de integración están definidos en la documentación del proyecto. Las funcionalidades descritas a continuación corresponden al alcance previsto; su implementación y validación se incorporarán progresivamente.

## Propósito y alcance

La solución busca transformar datos operativos de la máquina en información útil para el servicio técnico, sus propietarios y la administración de la plataforma.

El alcance de la plataforma comprende:

- Recepción, validación y conservación de registros enviados por las Stations.
- Consulta del último estado conocido, valores operativos, históricos y tendencias.
- Consulta de alertas y envío de notificaciones.
- Registro de observaciones técnicas y exportación de información en CSV.
- Administración de usuarios, máquinas, asociaciones, roles y permisos.
- Gestión de parámetros de monitoreo versionados y seguimiento de su aplicación en la Station.

La información presentada debe permitir distinguir el estado operativo de la máquina, la vigencia de los datos y la situación de comunicación.

El proyecto construye una base de información para futuras capacidades de mantenimiento predictivo y análisis de mejora del producto. El prototipo actual se concentra en monitoreo basado en condición y diagnóstico.

## Arquitectura

La solución completa distribuye responsabilidades entre la máquina, la Station y la plataforma central:

**Máquina → adquisición y procesamiento en Station → comunicación HTTPS/JSON → plataforma central → consulta web y notificaciones.**

La **Station** adquiere señales, ejecuta las funciones de monitoreo F1–F7, genera registros y conserva información pendiente de envío durante períodos sin conectividad. También proporciona la interfaz local de operación.

La **plataforma central** recibe los registros, conserva históricos y ofrece consulta remota, administración, gestión de parámetros y notificaciones.

El backend se organiza en tres servicios:

| Servicio | Responsabilidad principal |
|---|---|
| Gestión | Usuarios, permisos, máquinas, catálogos, parámetros, observaciones y auditoría. |
| Registros operativos | Ingesta, conservación de registros originales, último estado conocido, históricos y exportación. |
| Alertas y notificaciones | Reconstrucción de episodios de alerta recibidos, consulta y notificaciones por correo electrónico. |

Spring Cloud Gateway concentra el acceso a las API. RabbitMQ permite desacoplar el procesamiento interno de transiciones de alerta y sus notificaciones.

Cada servicio administra sus propios datos. La integración con la Station conserva la identidad, el origen y el momento de ocurrencia de los registros, y contempla retransmisiones y confirmaciones de persistencia.

### Tecnologías definidas

| Componente | Tecnologías |
|---|---|
| Interfaz web | React, TypeScript, Vite y Tailwind CSS |
| Servicios backend | Java 21 y Spring Boot |
| Entrada a las API | Spring Cloud Gateway |
| Persistencia de Gestión y Alertas | PostgreSQL |
| Persistencia de Registros operativos | MongoDB |
| Mensajería interna | RabbitMQ |
| Empaquetado y despliegue inicial | Docker y Docker Compose sobre Linux |
| Publicación web y HTTPS | NGINX |

### Simulación e integración

El prototipo contempla un auxiliar externo que genera señales y emula el comportamiento de la Station. Este utiliza el mismo contrato de integración que una Station real y mantiene identificado el origen simulado de los datos.

Las pruebas con simulación permiten evaluar el comportamiento de la plataforma y el intercambio de información. La validación física de sensores, adquisición e instalación en la máquina requiere pruebas específicas.

## Usuarios de la plataforma

- **Servicio Técnico:** consulta información operativa, históricos y alertas para apoyar el diagnóstico y seguimiento técnico.
- **Propietario de máquina:** consulta la información de las máquinas que tiene asociadas.
- **Administración:** gestiona usuarios, máquinas, asociaciones, permisos y configuración según sus autorizaciones.

El Operador interactúa con la interfaz local de la Station y no posee acceso web por ese rol.

## Preparación del repositorio

Para obtener este proyecto mediante SSH:

```sh
git clone git@github.com:gabbce/pf-plataforma-monitoreo-web.git
cd pf-plataforma-monitoreo-web
```

Estos comandos requieren Git y autenticación SSH configurada en GitHub.

### Código de referencia: AgroTrack

Para consultar y evaluar la reutilización de código de [AgroTrack](https://github.com/gabbce/TP-Desarrollo-de-Software-en-la-Nube-2026), clona su repositorio en la ubicación acordada, desde la raíz de este proyecto:

```sh
git clone git@github.com:gabbce/TP-Desarrollo-de-Software-en-la-Nube-2026.git referencias/agrotrack
```

La preparación se realiza una vez por copia del proyecto. Si esa carpeta ya contiene un clon, reutilízalo sin sobrescribirlo.

El directorio `referencias/agrotrack/` está excluido mediante `.gitignore`. AgroTrack conserva su propio historial y remoto; su código no se incorpora automáticamente a los commits de esta plataforma. Las actualizaciones de ese clon se realizan explícitamente desde su propio repositorio.

Las instrucciones para ejecutar y probar la plataforma se agregarán junto con sus componentes ejecutables.

## Documentación del proyecto

Google Drive mantiene las fuentes canónicas del alcance, los requisitos, el diseño y las decisiones del PFC:

- [Contexto operativo y decisiones del proyecto](https://docs.google.com/document/d/1lcZKgqRuqjYk7nvfggFXdzPMhiSFWBaJg3ZnRPmm7cU/edit).
- [Diseño detallado de la plataforma de monitoreo](https://docs.google.com/document/d/19Twh3hXmds-d8IhhxrvqQUpINPeg9pSi3yKotonV4Go/edit).
- [Contrato de integración Station–plataforma](https://docs.google.com/document/d/1gE5UOsgvys2ZfEnKznHS53ZYaSF5zNEU-Wut-d-YLYA/edit).
- [Carpeta de desarrollo del Proyecto Final](https://drive.google.com/drive/folders/11P4yMDp7t1O01jwOhaYpOyxkqQZbYotu).

El repositorio conserva la implementación y su documentación técnica de ejecución y pruebas. Las indicaciones para trabajar en él se encuentran en `AGENTS.md`.
