# Indicaciones del repositorio

## Propósito y alcance

Este repositorio está destinado a implementar la plataforma de monitoreo del
Proyecto Final de Carrera de Ingeniería en Sistemas de Información de UTN FRSF
(Argentina).

La plataforma forma parte de una solución integral: máquina → adquisición → Station
(procesamiento edge) → comunicación → backend/ingesta → persistencia → servicios/API
→ interfaz web → información para usuarios y decisiones.

- Respeta las responsabilidades de cada componente definidas en las fuentes vigentes.
- Implementa el alcance acordado de monitoreo basado en condición, detección de
  anomalías y diagnóstico técnico, con orientación a mantenimiento y soporte posventa.
- Distingue el título y los objetivos formales del alcance de implementación.
  El mantenimiento predictivo avanzado y la analítica de mejora de fabricación son
  evoluciones futuras; no los presentes como capacidades implementadas o validadas.

## Fuentes de verdad y decisiones

Drive conserva el alcance, los requisitos, el diseño y las decisiones del proyecto.
El repositorio permite comprobar lo efectivamente implementado y sus verificaciones.

Fuentes de entrada:

- [PFC - Contexto operativo y decisiones del proyecto](https://docs.google.com/document/d/1lcZKgqRuqjYk7nvfggFXdzPMhiSFWBaJg3ZnRPmm7cU/edit): estado vigente, decisiones, pendientes e índice de fuentes canónicas.
- [Diseño detallado de Station](https://docs.google.com/document/d/1OQUfzkxnx2ZCBM5eBbHi2rJcabcJF7navQFB8yY4QzY/edit): arquitectura local, adquisición, persistencia, sincronización y HMI.
- [Diseño detallado de la plataforma de monitoreo - Embolsadora](https://docs.google.com/document/d/19Twh3hXmds-d8IhhxrvqQUpINPeg9pSi3yKotonV4Go/edit): arquitectura, tecnologías y comportamiento acordado de la plataforma.
- [Contrato de integración Station-plataforma - Embolsadora](https://docs.google.com/document/d/1gE5UOsgvys2ZfEnKznHS53ZYaSF5zNEU-Wut-d-YLYA/edit): mensajes, confirmaciones, parámetros y reglas de intercambio.
- [Desarrollo Proyecto Final](https://drive.google.com/drive/folders/11P4yMDp7t1O01jwOhaYpOyxkqQZbYotu): carpeta raíz de documentación y trabajo del PFC.

Al iniciar un bloque que dependa del estado, alcance, requisitos o diseño del PFC:

1. Consulta primero el contexto operativo y sus decisiones vigentes.
2. Lee únicamente las fuentes canónicas pertinentes al cambio; el contexto gobierna
   el estado transversal y cada fuente específica gobierna el detalle de su tema.
   Incluye las pestañas pertinentes y usa el índice del contexto para otras fuentes.
3. Revisa `git status`, el contenido local y las verificaciones existentes antes de
   modificar archivos. Conserva los cambios del usuario.

- Los ajustes locales sin impacto funcional no requieren releer Drive.
- Si el código difiere de lo acordado, explicita la discrepancia y evalúa su impacto.
  La implementación existente no modifica por sí sola una decisión aprobada.
- Conserva las decisiones vigentes. Revísalas solo ante nueva evidencia, una
  restricción concreta o indicación del usuario; no conviertas una recomendación
  o propuesta en una decisión aprobada.
- Los documentos preliminares y antecedentes no sobrescriben definiciones vigentes.
- Si una fuente necesaria no está accesible, informa qué falta y cómo afecta la tarea.
  Continúa las tareas independientes y solicita solo la información que impida
  avanzar correctamente; no inventes el contenido de la fuente.

## Código de referencia: AgroTrack

- El repositorio `gabbce/TP-Desarrollo-de-Software-en-la-Nube-2026` debe estar clonado
  en `referencias/agrotrack` para las tareas que requieran consultar su código.
  Si falta, sigue la preparación indicada en `README.md`.
- Es un repositorio independiente, excluido por `.gitignore`. No sobrescribas un clon
  existente ni lo actualices o modifiques como efecto secundario de una consulta.
- Úsalo para evaluar reutilización; sus decisiones no reemplazan las fuentes del PFC.
  Al fundamentar un análisis en ese código, identifica el commit consultado.

## Implementación y validación

- Desarrolla incrementalmente y con orientación al prototipado: primero un flujo
  funcional completo y acotado, luego ampliaciones y ajustes por pruebas y revisión.
  Reutiliza lo pertinente y evita duplicar análisis cerrados.
- Respeta los requisitos, contratos y tecnologías aprobados. Consulta sus fuentes
  antes de proponer sustituciones o nuevas decisiones tecnológicas.
- Usa el mismo modelo, contrato y reglas de autorización para Station y emulador.
  Documenta escenarios y adaptadores en las pruebas, sin añadir banderas de origen
  físico o emulado a los datos o credenciales.
- Distingue arquitectura objetivo, entorno de pruebas y evidencia de implementación.
  Una prueba con escenarios no acredita validación en máquina.
- La instalación física, implementación del controlador real y comprobaciones físicas
  están fuera del alcance del PFC. La parametrización real se incorporará si es posible.
- Ante valores técnicos faltantes, prefiere parámetros simbólicos o configurables;
  si no hace falta cuantificar, expresa la condición sin fijar un valor.
- Usa valores supuestos solo cuando un ejemplo o simulación lo requiera, marcándolos
  como provisionales y no validados; nunca como especificaciones reales de la máquina.
- Distingue hechos respaldados por fuentes, inferencias, decisiones de ingeniería,
  supuestos, pendientes y recomendaciones. No completes silenciosamente los vacíos
  de una fuente con conocimiento general.
- Verifica cada cambio según el comportamiento afectado y los escenarios aplicables.
  Al finalizar, informa qué cambió, cómo se verificó y qué limitaciones permanecen;
  no afirmes que una prueba se ejecutó si no hay evidencia de su ejecución.

## Idioma

- Escribe los mensajes de commit y la documentación en español, incluidos los nombres
  de archivos de documentación, títulos y contenido. Conserva los nombres estándar
  requeridos por las herramientas, como `AGENTS.md` y `README.md`.
- Escribe el código en inglés, incluidos identificadores, nombres de archivos de código
  y comentarios.

## Documentación y continuidad

- Antes de crear o modificar documentos en Drive, presenta el contenido o los cambios
  en la conversación y espera mi aprobación antes de escribirlos. Si ya aprobé el
  contenido o autoricé expresamente una actualización con alcance definido, ejecútala
  dentro de ese alcance sin solicitar nuevamente la misma autorización.
- Incorpora a esos documentos únicamente contenido aprobado para el proyecto;
  no traslades deliberaciones del chat, alternativas descartadas ni sugerencias sin aprobar.
- Guarda en el repositorio las instrucciones de ejecución, pruebas y documentación
  técnica ligada al código. Mantén en Drive los documentos académicos y transversales,
  dentro de la carpeta correspondiente y respetando la estructura existente.
- Este archivo contiene reglas estables y referencias. El estado del proyecto,
  los pendientes y el inventario completo de fuentes permanecen en el contexto operativo.
- Mantén el README como entrada al repositorio y AGENTS.md como guía de trabajo.
  Evita duplicar diseños, estado de entregas académicas o bitácoras del contexto operativo.
- Al cerrar un bloque que produzca decisiones materiales, resuelva pendientes
  o cambie alcance, requisitos, arquitectura, modelo de información o estado del PFC,
  prepara la actualización del contexto operativo con un resumen vigente y referencias
  a los detalles. Escríbela en Drive únicamente cuando cuente con mi aprobación.
- No registres allí cada ajuste menor ni presentes una propuesta pendiente como aprobada.
- Si no puedes realizar una actualización autorizada en Drive, entrega el texto pendiente e indica
  explícitamente que la actualización no se realizó.
- Prefiere PlantUML para diagramas técnicos, salvo una razón concreta para otra herramienta.
- Redacta en forma clara, concisa y sustentada; para documentación académica, usa
  un tono académico sin formalidad excesiva, relleno ni afirmaciones sin justificación.
