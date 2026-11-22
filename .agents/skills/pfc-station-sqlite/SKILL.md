---
name: pfc-station-sqlite
description: Diseña o revisa la persistencia SQLite JDBC por Instancia en el auxiliar de Station, incluidas transacciones, registros pendientes y recuperación tras reinicio.
---

# SQLite del auxiliar de Station

Consulta el [diseño de Station](https://docs.google.com/document/d/1OQUfzkxnx2ZCBM5eBbHi2rJcabcJF7navQFB8yY4QzY/edit), la pestaña «Simulación de Station» del [diseño de plataforma](https://docs.google.com/document/d/19Twh3hXmds-d8IhhxrvqQUpINPeg9pSi3yKotonV4Go/edit) y el alcance del incremento. Comprueba [el POM del auxiliar](../../../station/auxiliary/pom.xml), [su manifiesto Compose](../../../infrastructure/auxiliary/compose.yaml) y la [guía de operación](../../../infrastructure/auxiliary/README.md).

- Mantén la identidad, configuración y persistencia separadas por Instancia. Usa el contrato HTTPS/JSON aprobado para la integración remota. En desarrollo, usa la entrada HTTP local descrita para el auxiliar.
- Usa transacciones JDBC breves para conservar de forma atómica el registro, su condición de pendiente y el contexto asociado cuando corresponda. Coordina las escrituras y conserva la identidad y el instante original al reintentar.
- Para una comprobación de recuperación, usa un archivo de prueba y vuelve a abrirlo tras cerrar el proceso. Comprueba el último estado confirmado, las identidades pendientes y la ausencia de doble contabilización cuando forme parte del alcance. Un reinicio controlado y una interrupción antes del guardado comprueban condiciones diferentes.
- Contrasta cualquier cambio de WAL, `synchronous` o `busy_timeout` con los requisitos de durabilidad y concurrencia y con la versión del driver. Usa [las transacciones SQLite](https://www.sqlite.org/lang_transaction.html) y [la documentación de WAL](https://www.sqlite.org/wal.html) para interpretar el comportamiento. Al inspeccionar datos o planes, trabaja sobre una copia de prueba y preserva el volumen operativo.
