---
name: pfc-frontend
description: Aplica el contrato web y las convenciones del PFC al componer, revisar o verificar vistas con React, Vite y shadcn/ui.
---

# Frontend del PFC

- Revisa [package.json](../../../frontend/package.json) y
  [components.json](../../../frontend/components.json) antes de agregar
  componentes. Genera con la CLI de shadcn de la revisión fijada y conserva
  la base Radix. Los tokens Canavesio residen en
  [index.css](../../../frontend/src/index.css). Consulta
  [`pfc-diseno`](../pfc-diseno/SKILL.md) para presentación e interacción.
- Deriva los escenarios del [contrato web](../../../docs/contratos/web/openapi.yaml)
  y sus [ejemplos JSON](../../../docs/contratos/web/ejemplos/). Usa `value`
  como carga HTTP. Conserva el chasis textual, los instantes UTC y el contexto
  de la consulta. Presenta la hora de Argentina y distingue último contacto,
  ocurrencia y recepción. Mantén las variantes de error y de calidad coherentes
  entre resumen, gráfico y detalle.
- Modela sesión ausente o vencida, acceso denegado y colecciones autorizadas
  vacías con sus comportamientos propios. Un resultado denegado o vacío
  carece de datos operativos. Aplica los filtros históricos al accionar
  «Consultar». Para intervalos y gráficos usa
  [`pfc-graficos`](../pfc-graficos/SKILL.md).
- Sigue el [README del frontend](../../../frontend/README.md) para ejecutar
  las comprobaciones. Contrasta interacciones en Chrome a 360, 768 y 1366 px,
  según el alcance de la tarea. Comprueba teclado, foco restaurado y tacto en
  los controles afectados. Las pruebas con ejemplos acreditan la interfaz
  con escenarios de prueba. Las pruebas de API requieren el recorrido real.
