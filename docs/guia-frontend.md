# Guía del frontend

La base incluye las vistas de acceso, máquinas autorizadas, detalle e
histórico. Los recorridos locales usan los [ejemplos del contrato web](contratos/web/ejemplos/)
y los [criterios aprobados de las interfaces](diseno/interfaces-p1.md).
El [README del frontend](../frontend/README.md#requisitos) indica las
versiones de Node.js y npm.

## Instalar y ejecutar

Instala y comprueba la base desde la raíz del repositorio.

```sh
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
```

La compilación estándar genera `frontend/dist/`. El acceso permanece
deshabilitado hasta conectar la API y los ejemplos quedan fuera del
sitio compilado.

Inicia Vite en modo de escenarios para recorrer las vistas con los ejemplos.

```sh
npm --prefix frontend run dev -- --mode escenarios
```

Abre la URL que indica Vite y usa las credenciales ficticias de
[`login-request.json`](contratos/web/ejemplos/login-request.json). El
ingreso y las consultas se resuelven localmente. La sesión de prueba se
reinicia al cerrar sesión o recargar. Selecciona una
[variante](../frontend/README.md#escenarios-locales) con `?scenario=`
antes del fragmento `#`, por ejemplo `/?scenario=dense#/`. Sin parámetro
se ejecuta el recorrido normal.

Usa Bash desde la raíz para previsualizar la compilación de escenarios
en un directorio temporal independiente de `frontend/dist/`.

```sh
salida_escenarios=$(mktemp -d -t pfc-frontend.XXXXXXXX)
npm --prefix frontend run build -- --mode escenarios --outDir "$salida_escenarios"
npm --prefix frontend run preview -- --outDir "$salida_escenarios"
```

## Mantener los componentes

Usa las skills del [catálogo frontend](skills-frontend.md) al abrir Codex
desde la raíz. Consulta las reglas del PFC sobre diseño, contrato y
gráficos antes de aplicar la guía de cada biblioteca.

La CLI de shadcn se instala con el lockfile. Desde `frontend/`, consulta
la base configurada y agrega el componente necesario.

```sh
npx --no-install shadcn info --json
npx --no-install shadcn add <componente>
```

Conserva la base Radix configurada en [`components.json`](../frontend/components.json)
y reutiliza los componentes existentes antes de agregar otros. Los
generados quedan en `src/components/ui/`. El tema Canavesio se define en
[`index.css`](../frontend/src/index.css) y el logotipo original reside en
[`brand/`](../brand/). Revisa el código generado, las dependencias y el
lockfile si la CLI los modifica. Repite las comprobaciones afectadas
cuando un componente se comparta entre vistas.

## Verificar los recorridos

Abre Chrome a 360, 768 y 1366 px. Contrasta navegación, jerarquía visual,
legibilidad y marca con la [guía de Figma](https://www.figma.com/design/u4Pk0hh7Cdh0AoKbfLbXpH?node-id=13-3779)
y la [ficha de interfaces](diseno/interfaces-p1.md). En móvil activa la
emulación táctil. Comprueba que el contenido se pueda leer y operar sin
desplazamiento horizontal de la página.

| Recorrido | Comprobación esperada |
| --- | --- |
| Acceso, máquinas, detalle e histórico | Ingresa con los datos ficticios y abre el chasis `000123`. Distingue el último contacto de la fecha de medición. Vuelve del histórico al resumen y comprueba que se conserva el contexto. Tras cerrar sesión, Atrás vuelve a solicitar el ingreso si la ruta anterior estaba protegida. |
| Selector y panel | Abre el selector con teclado, elige «Última hora» y comprueba que ambas tendencias usan el período. Abre «Ver detalle» con Enter, cierra con Escape y verifica que el foco vuelve al disparador. En móvil repite con toques y comprueba el panel de ancho completo. |
| Histórico y paginación | Abre el histórico desde «Última hora». Inicia con RPM, desde 08:11 hasta 09:11 del 15/01/2026 en hora de Argentina. Hay diez registros, con un cero válido. Cambia de página y comprueba que el gráfico conserva el intervalo completo. Toca la parte izquierda de una fila móvil y verifica que abre esa lectura. |
| Filtros | Cambia la variable sin pulsar «Consultar». El resultado visible conserva la consulta aplicada. Introduce un fin igual o anterior al inicio y comprueba el mensaje en el filtro. Corrige y pulsa «Consultar». Al volver al resumen, cambia el período y regresa al histórico. Se conservan los filtros propios del histórico, incluso si se habían inicializado sin editar. |
| Carga, vacío y error | Usa `loading`, `empty-machines` y `error`. Comprueba la carga en el área consultada, el mensaje de lista vacía y la recuperación del histórico con «Reintentar». Para un histórico vacío consulta un intervalo previo al 15/01/2026. |
| Acceso denegado y expiración | Usa `denied` y `expired`. En el primer caso comprueba el mensaje sin datos operativos. En el segundo, el primer intento de abrir el detalle devuelve al ingreso y permite volver a acceder. Prueba también credenciales inválidas y el mensaje genérico. |
| Calidad, vigencia y huecos | Usa `quality`, `stale`, `partial` y `gap`. La invalidez de RPM figura en resumen, tabla y panel, sin dibujar un valor usable. Las lecturas desactualizadas conservan su valor y fecha. Una variable ausente deja visible la otra. El hueco conocido de temperatura interrumpe el trazo. |
| Muestras densas y recepción tardía | Con `dense`, usa «Consultar muestras» para abrir una lectura intermedia por teclado o tacto. Contrasta el valor, la calidad y la hora con la tabla. Con `late` revisa el registro ocurrido a las 09:04:30 y recibido a las 09:12:00. El detalle conserva ambas horas y el contexto original. |

El modo de escenarios comprueba la presentación, la interacción y el
uso de los ejemplos. El acceso, la autorización y las consultas mediante
las API reales se verifican en las cards funcionales del
[Prototipo 1](https://github.com/gabbce/pf-plataforma-monitoreo-web/milestone/1).

## Mantener la referencia de diseño

1. Localiza el marco desde la guía editable de Figma y revisa la ficha
   de interfaces, el [cierre de revisión visual](diseno/cierre-p1/cierre.md)
   y la [card que aplica](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues/16).
2. Si hay una discrepancia, señala el marco, el criterio escrito y el
   comportamiento observado. Aplica el criterio aprobado mientras se
   acuerda el ajuste.
3. Cuando se apruebe un cambio, actualiza el marco y la ficha que lo
   describen. Enlaza la nueva referencia desde la issue pertinente y
   comprueba los recorridos afectados en los anchos aplicables. Conserva
   identificadas las capturas que corresponden a una revisión anterior.
