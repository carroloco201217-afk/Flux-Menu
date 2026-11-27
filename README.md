# SemianarquicoMenu 2.1.0

Menú principal para servidor Paper/Purpur con soporte **Java + Bedrock**.

- **Java** → diálogos nativos de Paper  
- **Bedrock** → menús de inventario (DeluxeMenus)  

Detecta jugadores Bedrock con Floodgate o por prefijo `BD_`.

## Compilar

El JAR se genera automáticamente con **GitHub Actions**:

1. Ve a la pestaña **Actions**
2. Cuando termine el build, descarga el artifact **SemianarquicoMenu**
3. Dentro del ZIP está el `.jar` listo para poner en `plugins/`

También puedes compilar localmente:

```bash
mvn clean package
```

## Instalación en el servidor

1. Pon `SemianarquicoMenu-2.1.0.jar` en `plugins/`
2. Copia los archivos de `DeluxeMenus/gui_menus/` a `plugins/DeluxeMenus/gui_menus/`
3. En `plugins/DeluxeMenus/config.yml` registra:

```yaml
gui_menus:
  flux_menu:
    file: flux_menu.yml
  kits:
    file: kits.yml
  warps:
    file: warps.yml
  rtp:
    file: rtp.yml
  stats:
    file: stats.yml
```

4. Reinicia o `/menu reload` + `/dm reload`

## Comandos

| Comando | Descripción |
|---------|-------------|
| `/menu` | Abre el menú (Java o Bedrock según el cliente) |
| `/menu reload` | Recarga la config |
| `/kit` | Menú de kits |
| `/kit <nombre>` | Reclama un kit |
| `/kit create <nombre> <precio> [categoria]` | Crea kit (admin) |
| `/kit delete <nombre>` | Borra kit (admin) |
| `/kit desc <nombre> <texto>` | Cambia descripción (admin) |
| `/kit list` | Lista kits |
| `/warp` | Menú de warps |
| `/setwarp <nombre>` | Crea warp (admin) |
| `/delwarp <nombre>` | Borra warp (admin) |
| `/warpdesc <nombre> <texto>` | Descripción del warp (admin) |

## Requisitos

- Paper / Purpur 1.21+
- DeluxeMenus (para Bedrock)
- Floodgate + Geyser (recomendado)
- Vault (opcional, precios de kits)
- BetterRTP (opcional)

Más detalles en `docs/INSTRUCCIONES.md`.
