---
name: pfc-backend
description: Aplica las fronteras de servicios, la autorización y la conservación del contexto histórico al diseñar, implementar o revisar el backend y los contratos del PFC.
---

# Backend del PFC

Ubica el incremento y las fuentes aplicables con [pfc-fuentes](../pfc-fuentes/SKILL.md). Usa el diseño de plataforma para las responsabilidades y el contrato Station–plataforma para el intercambio de Station.

- En [Gestión](../../../backend/management/pom.xml), identifica máquinas por chasis textual y consulta las fuentes, configuraciones y autorizaciones a través de su contrato. En [Registros](../../../backend/records/pom.xml), conserva los originales y resuelve las consultas operativas. PostgreSQL y MongoDB pertenecen a sus servicios respectivos. El auxiliar se comunica a través de la entrada de plataforma.
- Distingue cuentas web e identidades técnicas de Station. El Gateway enruta y cada servicio aplica los permisos que le corresponden. Station y emulador usan el mismo contrato y las mismas reglas de identidad y autorización.
- En el modelo vigente, `VinculoFuenteDato` conserva una fuente lógica efectiva por Instancia y definición de dato en cada instante. Al revisar vigencias y solapamientos, usa esa pareja de claves. Para registros tardíos, resuelve el vínculo al instante de ocurrencia. Conserva por separado la recepción, la configuración realmente aplicada cuando corresponda y la identidad y el contenido originales. Un cambio posterior de fuente o configuración conserva la interpretación del registro ya almacenado.
- Al revisar reintentos, distingue una repetición idéntica de un conflicto con el mismo identificador. Conserva la primera recepción y verifica la condición de escritura durable antes de confirmar según el contrato.
- En consultas web, conserva los ceros iniciales del chasis, la unidad y calidad de la medición y la distinción entre ocurrencia, recepción y último contacto autenticado. Deriva los casos de prueba del contrato aprobado y del alcance del incremento.

Antes de elegir una API, un starter o un plugin, consulta el [POM raíz](../../../pom.xml), los POM de los módulos y la [guía Java](../../../docs/base-java-p1-01-1.md). Usa el Maven Wrapper del repositorio con el JDK declarado. Usa [pfc-gateway](../pfc-gateway/SKILL.md) para enrutamiento y [pfc-station-sqlite](../pfc-station-sqlite/SKILL.md) para la persistencia local.
