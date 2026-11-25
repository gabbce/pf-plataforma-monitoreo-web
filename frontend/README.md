# Base web de la plataforma

La aplicación web se inicializó con create-vite 9.3.4 y la plantilla oficial
React con TypeScript. La base incluye acceso, máquinas autorizadas,
detalle de máquina e histórico en el modo local de escenarios. La
compilación estándar conecta el acceso y el catálogo de máquinas con Gestión
mediante el entorno Compose central.
Consulta la [guía del frontend](../docs/guia-frontend.md) para recorrer
las vistas, verificar la base y mantener sus componentes y referencias.

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

`package.json` declara las dependencias y `package-lock.json` fija las
versiones resueltas. Conserva el lockfile e instala con `npm ci`.

## Desarrollo y compilación

Si usas nvm, ejecuta `nvm install` y `nvm use` desde `frontend/` para seleccionar
el Node del proyecto. Si necesitas instalar la versión de npm declarada,
ejecuta `npm install --global npm@12.2.3` con ese Node activo.

Vuelve a la raíz y ejecuta `make instalar` y `make escenarios`.

Vite indica en la terminal la URL local. Genera la compilación estándar desde
la raíz con Make.

```sh
make build
```

El build compila también Java. En el frontend comprueba los tipos y genera
los archivos estáticos en `dist/`. `make lint` ejecuta Oxlint y TypeScript.
`make test` incluye las pruebas del frontend sobre solicitudes a Gestión,
paginación e intercambio de fechas. Para usar la compilación estándar con
la API, sigue la [guía del entorno Compose](../docs/guia-frontend.md#acceso-y-catálogo-en-compose).

## Organización

`index.html` contiene el elemento raíz que monta React desde `src/main.tsx`.
`src/App.tsx` monta la aplicación. `src/index.css` importa Tailwind CSS,
que se integra en el build mediante `@tailwindcss/vite` en `vite.config.ts`.

## Base de componentes Canavesio

`components.json` configura shadcn/ui con Radix. Los componentes generados
están en `src/components/ui/` y los tokens del tema en `src/index.css`.
La tipografía Inter se sirve localmente. Para incorporar un componente,
ejecuta la CLI desde `frontend/`.

```sh
npx --no-install shadcn add <componente>
```

## Escenarios locales

Para recorrer las cuatro vistas con los
[ejemplos del contrato web](../docs/contratos/web/ejemplos/), inicia Vite
desde la raíz en modo de escenarios.

```sh
make escenarios
```

Usa el identificador y la contraseña ficticios de
[`login-request.json`](../docs/contratos/web/ejemplos/login-request.json).
La sesión de prueba dura hasta cerrar sesión o recargar la página. El
ingreso y las consultas se resuelven en memoria.

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
15/01/2026. La [guía del frontend](../docs/guia-frontend.md#instalar-y-ejecutar)
explica cómo previsualizar el build de escenarios por separado del estándar.
