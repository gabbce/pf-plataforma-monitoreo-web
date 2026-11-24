# Base web de la plataforma

La aplicación web se inicializó con create-vite 9.3.4 y la plantilla oficial
React con TypeScript. La base incluye acceso, máquinas autorizadas,
detalle de máquina e histórico en el modo local de escenarios.
[P1-01.2](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues/9)

## Diseño de interfaz del Prototipo 1

El prototipo en Figma y la ficha de interacción aprobada guían la implementación
de acceso, máquinas autorizadas, detalle de máquina e histórico. La guía de Figma
reúne las variantes de escritorio, tablet y móvil y los estados alternativos.

- [Prototipo editable en Figma](https://www.figma.com/design/u4Pk0hh7Cdh0AoKbfLbXpH) y [guía de recorrido](https://www.figma.com/design/u4Pk0hh7Cdh0AoKbfLbXpH?node-id=13-3779).
- [Ficha de interfaces y criterios de interacción](../docs/diseno/interfaces-p1.md) y [cierre de revisión](../docs/diseno/cierre-p1/cierre.md).
- [Recursos de marca](../brand/).

## Requisitos

- Node.js 26.13.1 LTS, indicado en `.nvmrc`.
- npm 12.2.3, declarado en `package.json`.

Las dependencias directas tienen versiones exactas en `package.json`.
Conservar `package-lock.json` para instalar la misma resolución de dependencias
con `npm ci`.

## Desarrollo y compilación

Ejecutar desde `frontend/`. Si se usa nvm, ejecutar `nvm install` y `nvm use`
para seleccionar el Node del proyecto. Si se necesita instalar la versión de
npm declarada, ejecutar `npm install --global npm@12.2.3` con ese Node activo.

```sh
npm ci
npm run dev
```

Vite indica en la terminal la URL local. Para generar y servir localmente
la compilación de producción:

```sh
npm run build
npm run preview
```

El build comprueba los tipos y genera los archivos estáticos en `dist/`.
`npm run typecheck` ejecuta la comprobación de TypeScript por separado.
`npm run lint` analiza el código con Oxlint.
`npm test` comprueba la paginación con un mismo corte y el intercambio de fechas.

## Organización

`index.html` contiene el elemento raíz que monta React desde `src/main.tsx`.
`src/App.tsx` monta la aplicación. `src/index.css` importa Tailwind CSS,
que se integra en el build mediante `@tailwindcss/vite` en `vite.config.ts`.

## Base de componentes Canavesio

`components.json` configura shadcn/ui con Radix. Los componentes generados
están en `src/components/ui/` y los tokens del tema en `src/index.css`.
La tipografía Inter se sirve localmente. Para incorporar un componente,
ejecuta desde `frontend/`:

```sh
npx shadcn@4.23.0 add <componente>
```

## Escenarios locales

Para recorrer las cuatro vistas con los
[ejemplos del contrato web](../docs/contratos/web/ejemplos/), inicia Vite en
modo de escenarios:

```sh
npm run dev -- --mode escenarios
```

Usa el identificador y la contraseña ficticios de
[`login-request.json`](../docs/contratos/web/ejemplos/login-request.json).
La sesión de prueba dura hasta cerrar sesión o recargar la página. El
ingreso y las consultas se resuelven en memoria. La compilación estándar
deja el acceso deshabilitado hasta que se conecten las API reales.

Sin parámetro se activa el recorrido normal. Agrega `?scenario=` con una
de estas variantes antes del fragmento `#` de la URL.

| Variante | Comportamiento |
| --- | --- |
| `loading` | La carga permanece visible durante las consultas. |
| `empty-machines` | El ingreso muestra una lista sin máquinas autorizadas. |
| `denied` | Abrir la máquina muestra acceso denegado. |
| `expired` | El primer intento de abrir la máquina devuelve al acceso. |
| `error` | La primera consulta histórica falla y se recupera con «Reintentar». |
| `quality` | El resumen y el histórico incluyen una lectura RPM inválida. |
| `partial` | Solo hay mediciones de temperatura. |
| `stale` | El resumen distingue las lecturas desactualizadas. |
| `gap` | La serie de temperatura tiene una ausencia intermedia. |
| `dense` | Las muestras RPM se concentran en una misma ventana de segundos. |
| `late` | El histórico incluye una medición recibida después de su ocurrencia. |

Para observar una consulta sin resultados, usa un intervalo anterior al
15/01/2026. Para contrastar archivos compilados localmente, ejecuta
`npm run build -- --mode escenarios` y luego `npm run preview`.
