---
name: pfc-diseno
description: Localiza las referencias de interfaz, marca y decisiones aprobadas del PFC al implementar, contrastar o revisar una vista o componente.
---

# Diseño de interfaz del PFC

- Consulta [Interfaces del P1](../../../docs/diseno/interfaces-p1.md) y el
  [cierre de revisión](../../../docs/diseno/cierre-p1/cierre.md). La ficha
  determina los criterios aprobados. Las capturas y su evidencia corresponden
  a las revisiones que indica cada documento.
- Localiza el nodo desde la [guía editable de Figma](https://www.figma.com/design/u4Pk0hh7Cdh0AoKbfLbXpH?node-id=13-3779)
  y compáralo con la issue aplicable. Para traducirlo a código, usa
  `figma-design-to-code` del plugin oficial y consulta el nodo y sus tokens.
  Cuando Figma no esté accesible, indica qué parte del contraste queda
  pendiente y utiliza la referencia versionada para el resto.
- Reutiliza el [logo original](../../../brand/) y el tema del
  [CSS global](../../../frontend/src/index.css). Identifica cualquier
  discrepancia antes de incorporar un cambio material de diseño y conserva el
  criterio aprobado hasta que se acuerde su ajuste.
- Usa [`pfc-fuentes`](../pfc-fuentes/SKILL.md) si el cambio depende del alcance o
  de una decisión transversal. Aplica las reglas del repositorio al actualizar
  referencias y documentación.
