---
name: pfc-graficos
description: Implementa o revisa gráficos temporales y la consulta de muestras con Recharts 3, usando calidad, ocurrencia y paginación del contrato web del PFC.
---

# Gráficos temporales del PFC

1. Consulta el [OpenAPI](../../../docs/contratos/web/openapi.yaml), los
   [ejemplos](../../../docs/contratos/web/ejemplos/) y el
   [criterio de interfaz](../../../docs/diseno/interfaces-p1.md).
   Usa [Recharts](https://recharts.github.io/en-US/api/) y el componente
   [Chart de shadcn/ui](https://ui.shadcn.com/docs/components/radix/chart)
   de acuerdo con las versiones de `frontend/package.json`.
2. Ordena las muestras por ocurrencia y luego por identificador. Usa
   `[from,to)` y el mismo `asOf` en todas las páginas y series de la
   consulta. El gráfico cubre el conjunto completo del intervalo y la tabla
   puede mostrar una página. Conserva ocurrencia y recepción originales.
3. Conserva un cero válido. Representa las lecturas inválidas y los huecos
   conocidos como valores nulos y usa `connectNulls={false}` en la línea.
   Determina una ausencia a partir del escenario o de la cadencia aplicable
   del catálogo. Un intervalo mayor entre dos registros por sí solo puede
   ser válido. Indica la calidad con texto y la vigencia por separado.
4. Comprueba una muestra intermedia con teclado o puntero. En móvil verifica
   el toque y que se pueda elegir cada muestra cuando varias se concentran
   en un mismo sector. Si los puntos se superponen, ofrece una lista de
   selección vinculada a esas muestras. Usa el mismo contexto de lectura
   al abrir una muestra desde el gráfico o la tabla.
