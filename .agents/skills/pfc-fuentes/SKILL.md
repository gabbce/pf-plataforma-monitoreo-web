---
name: pfc-fuentes
description: Localiza y aplica las fuentes canónicas de Drive y las issues del PFC al revisar el alcance, la arquitectura, los contratos o las decisiones vigentes.
---

# Fuentes del PFC

Empieza por [AGENTS.md](../../../AGENTS.md) y por el [Contexto operativo](https://docs.google.com/document/d/1lcZKgqRuqjYk7nvfggFXdzPMhiSFWBaJg3ZnRPmm7cU/edit). Lee «Estado actual» y las decisiones o el índice de fuentes que afecten a la tarea. Si hay varias pestañas en una fuente, comprueba cuál contiene el detalle aplicable.

| Necesidad | Fuente principal |
|---|---|
| Alcance, aceptación y cobertura del incremento | [Registro de incrementos](https://docs.google.com/document/d/1HEcfnedONjGtWyyDrDRskmFqxkQ3vq_fDSl03aKEdiA/edit), en la pestaña del incremento correspondiente. |
| Responsabilidades, modelo, persistencia y despliegue de la plataforma | [Diseño de plataforma](https://docs.google.com/document/d/19Twh3hXmds-d8IhhxrvqQUpINPeg9pSi3yKotonV4Go/edit), incluidas «Modelos por servicio» y «Simulación de Station» cuando apliquen. |
| Ejecución local, recuperación y persistencia en Station | [Diseño de Station](https://docs.google.com/document/d/1OQUfzkxnx2ZCBM5eBbHi2rJcabcJF7navQFB8yY4QzY/edit). |
| Identidad, mensajes, confirmaciones y parámetros de Station | [Contrato Station–plataforma](https://docs.google.com/document/d/1gE5UOsgvys2ZfEnKznHS53ZYaSF5zNEU-Wut-d-YLYA/edit). |
| Dependencias, alcance operativo y aceptación del paso de trabajo | [Tablero](https://github.com/users/gabbce/projects/1), card padre y [subissue](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues) correspondiente. |

Cita la pestaña o sección que fundamenta una decisión. Contrasta con el código y las pruebas para determinar el estado implementado. Si difieren, identifica la discrepancia, conserva la decisión aprobada y señala su impacto. Si la fuente necesaria es inaccesible, indica qué decisión queda sin verificar y continúa con lo sustentado.

Usa el contrato de Station para la integración de cualquier productor. Para el contrato web, localiza el artefacto versionado cuando exista y verifica su alcance en la card. Aplica las reglas de aprobación de [AGENTS.md](../../../AGENTS.md) a cualquier actualización de fuentes o del registro de trabajo.
