# Base web de la plataforma

La aplicación web se inicializó con create-vite 9.3.4 y la plantilla oficial
React con TypeScript. La vista inicial muestra el nombre de la plataforma
y sirve como punto de partida para las pantallas de negocio.
[P1-01.2](https://github.com/gabbce/pf-plataforma-monitoreo-web/issues/9)

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

## Organización

`index.html` contiene el elemento raíz que monta React desde `src/main.tsx`.
`src/App.tsx` define la vista inicial. `src/index.css` importa Tailwind CSS,
que se integra en el build mediante `@tailwindcss/vite` en `vite.config.ts`.
