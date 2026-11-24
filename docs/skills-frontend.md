# Skills del frontend

El catálogo de la plataforma vive en [`.agents/skills/`](../.agents/skills/).
Abre Codex desde la raíz y localiza las skills con `/skills` o el selector
`$`. Si un plugin o una instalación personal ofrece el mismo nombre,
comprueba que el selector indique la ruta del repositorio.

## Uso por tarea

| Skill | Aplicación |
| --- | --- |
| [`shadcn`](../.agents/skills/shadcn/SKILL.md) | Componentes, variantes y primitivas Radix de la base. Usa la CLI fijada en el lockfile del frontend. |
| [`vercel-react-best-practices`](../.agents/skills/vercel-react-best-practices/SKILL.md) | Revisión de React, renderizados, carga de datos y tamaño del bundle. |
| [`vercel-composition-patterns`](../.agents/skills/vercel-composition-patterns/SKILL.md) | Composición de componentes reutilizables y APIs en React 19. |
| [`typescript-advanced-types`](../.agents/skills/typescript-advanced-types/SKILL.md) | Tipos de dominio, discriminación de estados y utilidades TypeScript. |
| [`vite`](../.agents/skills/vite/SKILL.md) | Configuración de Vite 8, resolución y empaquetado con Rolldown. |
| [`tailwind-css`](../.agents/skills/tailwind-css/SKILL.md) | Configuración de Tailwind 4, detección de clases y utilidades. |
| [`tailwind-design-system`](../.agents/skills/tailwind-design-system/SKILL.md) | Jerarquía de tokens, variantes de componentes y diseño responsivo en Tailwind 4. |
| [`playwright-cli`](../.agents/skills/playwright-cli/SKILL.md) | Verificación en navegador, capturas locales y diagnóstico de recorridos. |
| [`playwright-generate-test`](../.agents/skills/playwright-generate-test/SKILL.md) | Diseño de pruebas a partir de un recorrido con Playwright MCP cuando esté disponible. |
| [`wcag-audit-patterns`](../.agents/skills/wcag-audit-patterns/SKILL.md) | Revisión de accesibilidad mediante comprobaciones manuales y automatizadas. |
| [`screen-reader-testing`](../.agents/skills/screen-reader-testing/SKILL.md) | Pruebas reales con lectores de pantalla y revisión de anuncios y foco. |
| [`pfc-diseno`](../.agents/skills/pfc-diseno/SKILL.md) | Referencias de interfaz y Figma, marca y precedencia de criterios aprobados. |
| [`pfc-frontend`](../.agents/skills/pfc-frontend/SKILL.md) | Convenciones del PFC, contrato web, ejemplos y verificación de vistas. |
| [`pfc-graficos`](../.agents/skills/pfc-graficos/SKILL.md) | Recharts 3, muestras densas, calidad, huecos y coherencia entre gráfico y tabla. |

Combina una skill de tecnología con la regla del proyecto que corresponda.
Para un selector usa `$shadcn` y `$pfc-diseno`. Para una vista con datos
usa `$pfc-frontend` y el contrato. Para un gráfico con huecos o intervalos
largos usa `$pfc-graficos`. Revisa la versión real de cada biblioteca en
[package.json](../frontend/package.json) y aplica las indicaciones de React
al entorno Vite. Los ejemplos de otras aplicaciones conservan su contexto
de origen.

Para Figma se usa `figma-design-to-code` del
[plugin oficial](https://developers.figma.com/docs/figma-mcp-server/create-skills/)
(versión 20.0.0 del entorno de referencia), junto con `pfc-diseno`.
Para Radix usa las reglas Radix de shadcn y la
[guía de accesibilidad](https://www.radix-ui.com/primitives/docs/overview/accessibility).
Para Recharts usa `pfc-graficos` y la
[API pública de Recharts](https://recharts.github.io/en-US/api/).
Las skills de los repositorios de React, TypeScript y Recharts relevadas
están orientadas a contribuir a sus propios proyectos.

## Orígenes y licencias

| Proveedor | Revisión | Licencia |
| --- | --- | --- |
| [shadcn/ui](https://github.com/shadcn-ui/ui/tree/055aa6b7837b1808456a24beb4c97a677864aa82/skills/shadcn) | `055aa6b7837b` | [MIT](licencias/skills-frontend/shadcn-ui-ui-LICENSE) |
| [Vercel Engineering](https://github.com/vercel-labs/agent-skills/tree/5a37a6329fbad3666c3207392a6a7c0110e92efb/skills) | `1.0.0` · `5a37a6329fba` | MIT declarado en las cabeceras de ambas skills |
| [Anthony Fu](https://github.com/antfu/skills/tree/4a6bb39e7b45e26402eb215393c51ccf5df1ca4c/skills/vite) | `2026.11.5` · `4a6bb39e7b45` | [MIT](licencias/skills-frontend/antfu-skills-LICENSE) |
| [Paul Razvan Berg](https://github.com/PaulRBerg/agent-skills/tree/3de4c43c07b14738e0cbf1a9d3ba12809d1758ab/skills/tailwind-css) | `3de4c43c07b1` | [MIT](licencias/skills-frontend/paulrberg-agent-skills-LICENSE) |
| [Microsoft Playwright](https://github.com/microsoft/playwright-cli/tree/c872a33cec77fa03c28fa7c6d1ad99d15d04ce42/skills/playwright-cli) | `c872a33cec77` | [Apache-2.0](licencias/skills-frontend/microsoft-playwright-cli-LICENSE) |
| [GitHub](https://github.com/github/awesome-copilot/tree/f10fdb8b2660ce85c98f220b2f1ca24653768a5c/skills/playwright-generate-test) | `f10fdb8b2660` | [MIT compartida](licencias/skills-backend/github-awesome-copilot-LICENSE) |
| [Seth Hobson](https://github.com/wshobson/agents/tree/34e50fee32114afd9475f25d81492712f08cb71c/plugins) | `34e50fee3211` | [MIT compartida](licencias/skills-backend/wshobson-agents-LICENSE) |

`shadcn` y `playwright-cli` conservan copias adaptadas en Git. La primera
consulta el contexto de proyecto explícitamente y usa la versión local
de la CLI. La segunda trabaja con el navegador autorizado y condiciona
la publicación de capturas, el acceso a perfiles y el borrado de datos a
la autorización correspondiente. En `vercel-react-best-practices` se
corrigieron tres enlaces del documento agregado para que apunten a
`rules/`. Las tres skills del PFC se mantienen en este repositorio.

Se evaluó
[`tailwind-v4-shadcn`](https://github.com/secondsky/claude-skills/tree/14966c3024f48eea0386eb970b230ea0f4bebd33/plugins/tailwind-v4-shadcn/skills/tailwind-v4-shadcn).
Esa revisión indica versiones anteriores de Vite y TypeScript y pide quitar
`tw-animate-css`, dependencia declarada por esta base. Para tokens y variantes
se usa `tailwind-design-system` sobre el tema Canavesio existente,
junto con `tailwind-css` y la skill oficial de shadcn.

## Restauración y actualización

Git conserva las copias del catálogo para todos los clones. El
[lock compartido](../skills-lock.json) fija los commits y hashes de las
ocho skills frontend distribuidas por la CLI sin cambios locales.
Para restaurar copias faltantes a partir del lock, ejecuta desde la raíz
con Node `>=22.20.0`:

```sh
npx --yes skills@1.9.0 experimental_install --agent codex --frozen-lockfile
```

Git restaura las tres adaptaciones y las tres skills propias. Para cambiar
una revisión, consulta el origen exacto y revisa sus instrucciones,
referencias y cambios en el lock. Repite el descubrimiento desde la raíz y
las tareas representativas afectadas. El listado del App Server se consulta
con `skills/list`, el directorio raíz en `cwds` y `forceReload: true`.
