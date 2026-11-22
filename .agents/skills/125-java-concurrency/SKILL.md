---
name: 125-java-concurrency
description: Revisa o diseña concurrencia con APIs estables de Java 25, incluida publicación segura, ejecución acotada, cancelación y cierre de tareas del backend y del auxiliar.
license: Apache-2.0
metadata:
  author: Juan Antonio Breña Moral (Plinth)
  upstream: jabrena/plinth@6167e206e46246f0c933fdf071c9cbee5af79772
---

# Concurrencia en Java 25

Adaptación del PFC de [Plinth 0.19.0](https://github.com/jabrena/plinth/tree/6167e206e46246f0c933fdf071c9cbee5af79772/skills/125-java-concurrency). Se reorganizaron las instrucciones y se limitaron los patrones a las APIs estables de Java 25.

1. Confirma el JDK en el [POM raíz](../../../pom.xml). La política de esta skill es usar Java 25 sin funciones en vista previa. `StructuredTaskScope` es una API preview en ese JDK. Usa las APIs estables de [`java.util.concurrent` en Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/package-summary.html).
2. Identifica el propietario de cada estado mutable y las operaciones que deben ser atómicas. Prefiere valores inmutables, publicación segura y confinamiento cuando un único hilo puede poseer el estado. Usa locks o colecciones concurrentes según la operación. Una secuencia de operaciones sobre una colección concurrente requiere su propia garantía de atomicidad.
3. Limita el trabajo pendiente y los accesos a recursos escasos. Para un conjunto fijo de workers con capacidad de espera limitada, considera `ThreadPoolExecutor` con una cola acotada y una política de saturación explícita. Para I/O bloqueante, `Executors.newVirtualThreadPerTaskExecutor()` es una API estable. Limita la admisión o los recursos con un semáforo u otro límite antes de acumular tareas. Java 25 ya incorpora [JEP 491](https://openjdk.org/jeps/491), que elimina el pinning causado por monitores en los casos que describe. Decide los cambios de locking por corrección y evidencia.
4. Define cómo se propagan los errores, se cancelan las tareas y se cierra el ejecutor. Propaga `InterruptedException` o restablece la interrupción si la capturas para finalizar. Aplica tiempos de espera a las operaciones externas y a la espera de cierre. Si hay reintentos, conserva la identidad y comprueba su idempotencia antes de repetir efectos.
5. En el auxiliar, aplica [pfc-station-sqlite](../pfc-station-sqlite/SKILL.md) a la escritura y recuperación de pendientes. El límite de una cola en memoria debe conservar el trabajo ya confirmado según el diseño vigente. En el Gateway, conserva las fronteras reactivas indicadas por [pfc-gateway](../pfc-gateway/SKILL.md).

Para comprobar concurrencia, coordina los pasos con señales controladas como `CountDownLatch` y usa tiempos de espera acotados. Verifica saturación, cancelación, cierre y conservación de los efectos confirmados cuando sean parte del escenario. Compila y prueba con el Wrapper y el JDK declarado cuando la tarea cambie código.
