package net.semianarquico.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SemianarquicoMenu extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private NamespacedKey tinteKey;
    private File kitsFile;
    private File warpsFile;
    private YamlConfiguration kitsCfg;
    private YamlConfiguration warpsCfg;

    private boolean floodgatePresent;
    private Object floodgateApi; // org.geysermc.floodgate.api.FloodgateApi

    // ================================================================ arranque

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tinteKey = new NamespacedKey(this, "tinte_menu");
        cargarDatos();
        detectarFloodgate();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SemianarquicoMenu 2.1.0 activo. Floodgate: " + (floodgatePresent ? "SI" : "NO (solo prefijo)"));
    }

    private void detectarFloodgate() {
        floodgatePresent = false;
        floodgateApi = null;
        if (!getConfig().getBoolean("bedrock.usar-floodgate", true)) return;
        if (getServer().getPluginManager().getPlugin("floodgate") == null) return;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApi = getInstance.invoke(null);
            floodgatePresent = true;
        } catch (Throwable t) {
            getLogger().warning("Floodgate detectado pero no se pudo cargar la API: " + t.getMessage());
        }
    }

    private void cargarDatos() {
        kitsFile = new File(getDataFolder(), "kits.yml");
        warpsFile = new File(getDataFolder(), "warps.yml");
        kitsCfg = YamlConfiguration.loadConfiguration(kitsFile);
        warpsCfg = YamlConfiguration.loadConfiguration(warpsFile);
    }

    private void guardar(YamlConfiguration cfg, File archivo) {
        try {
            cfg.save(archivo);
        } catch (IOException e) {
            getLogger().severe("No se pudo guardar " + archivo.getName() + ": " + e.getMessage());
        }
    }

    // ================================================================ deteccion Bedrock

    /**
     * True si el jugador es Bedrock (Floodgate o prefijo configurado).
     */
    public boolean esBedrock(Player p) {
        if (floodgatePresent && floodgateApi != null) {
            try {
                Method isFloodgatePlayer = floodgateApi.getClass().getMethod("isFloodgatePlayer", java.util.UUID.class);
                Object result = isFloodgatePlayer.invoke(floodgateApi, p.getUniqueId());
                if (result instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {
            }
        }
        String name = p.getName();
        List<String> prefijos = getConfig().getStringList("bedrock.prefijos");
        if (prefijos == null || prefijos.isEmpty()) {
            prefijos = List.of("BD_", "BD.");
        }
        for (String pref : prefijos) {
            if (name.regionMatches(true, 0, pref, 0, pref.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Abre un menu de DeluxeMenus para un jugador Bedrock.
     * Si DeluxeMenus no esta o falla, hace fallback al dialogo Java.
     */
    private void abrirMenuBedrock(Player p, String tipo) {
        String cmd = getConfig().getString("bedrock.menus." + tipo, "");
        if (cmd == null || cmd.isBlank()) {
            // fallback
            switch (tipo) {
                case "kits" -> abrirMenuKitsJava(p);
                case "warps" -> abrirMenuWarpsJava(p);
                default -> abrirMenuPrincipalJava(p);
            }
            return;
        }
        cmd = cmd.replace("{jugador}", p.getName());
        // Ejecutar como consola para que dm open funcione bien
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    // ================================================================ utilidades

    private static Component c(String texto) {
        return LEGACY.deserialize(texto == null ? "" : texto)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Component msg(String clave, String... pares) {
        String t = getConfig().getString("mensajes." + clave, "&c[falta el mensaje " + clave + "]");
        for (int i = 0; i + 1 < pares.length; i += 2) {
            t = t.replace("{" + pares[i] + "}", pares[i + 1]);
        }
        return c(t);
    }

    private static String bonito(String material) {
        return material.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String precioTxt(double precio) {
        if (precio <= 0) return "&aGratis";
        return "&e" + getConfig().getString("moneda", "$") + String.format(Locale.US, "%,.0f", precio);
    }

    private Economy economia() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    private Player soloJugador(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(getConfig().getString("mensajes.solo-jugadores", "Solo jugadores."));
        return null;
    }

    private static String unir(String[] args, int desde) {
        StringBuilder sb = new StringBuilder();
        for (int i = desde; i < args.length; i++) {
            if (i > desde) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private List<String> categorias() {
        ConfigurationSection s = getConfig().getConfigurationSection("kits.categorias");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    // ================================================================ tinte

    private ItemStack crearTinte() {
        ConfigurationSection s = getConfig().getConfigurationSection("tinte");
        Material mat = Material.matchMaterial(s.getString("material", "YELLOW_DYE"));
        if (mat == null) mat = Material.YELLOW_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(c(s.getString("nombre")));
        List<Component> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) lore.add(c(l));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(tinteKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean esTinte(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(tinteKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPlayedBefore()) return;
        int slot = Math.max(0, Math.min(8, getConfig().getInt("tinte.slot", 8)));
        ItemStack tinte = crearTinte();
        if (p.getInventory().getItem(slot) == null) {
            p.getInventory().setItem(slot, tinte);
        } else {
            p.getInventory().addItem(tinte).values()
                    .forEach(resto -> p.getWorld().dropItemNaturally(p.getLocation(), resto));
        }
    }

    @EventHandler
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!esTinte(e.getItem())) return;
        e.setCancelled(true);
        abrirMenuPrincipal(e.getPlayer());
    }

    // ================================================================ dialogos (base) - solo Java

    private ActionButton boton(Component etiqueta, Component tooltip, Runnable accion) {
        int ancho = Math.max(100, Math.min(1024, getConfig().getInt("ancho-botones", 320)));
        return ActionButton.create(
                etiqueta,
                tooltip,
                ancho,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player) {
                        Bukkit.getScheduler().runTask(this, accion);
                    }
                }, ClickCallback.Options.builder().build())
        );
    }

    private Component etiqueta(String nombre, String descripcion) {
        String t = nombre == null ? "" : nombre;
        if (descripcion != null && !descripcion.isBlank()) t = t + " &8- " + descripcion;
        return c(t);
    }

    private Component tooltipDe(String descripcion) {
        return (descripcion == null || descripcion.isBlank()) ? null : c(descripcion);
    }

    private void mostrar(Player p, String titulo, String descripcion, List<ActionButton> botones) {
        Dialog dialogo = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(c(titulo))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(c(descripcion))))
                        .build())
                .type(DialogType.multiAction(botones, null, 1)));
        p.showDialog(dialogo);
    }

    private void ejecutar(Player p, String comando) {
        if (comando == null || comando.isBlank()) return;
        String cmd = comando
                .replace("{mundo}", getConfig().getString("rtp.mundo", "survival"))
                .replace("{jugador}", p.getName());
        p.performCommand(cmd);
    }

    // ================================================================ menu principal (punto de entrada)

    public void abrirMenuPrincipal(Player p) {
        if (esBedrock(p)) {
            abrirMenuBedrock(p, "principal");
        } else {
            abrirMenuPrincipalJava(p);
        }
    }

    private void abrirMenuPrincipalJava(Player p) {
        ConfigurationSection m = getConfig().getConfigurationSection("menu");
        ConfigurationSection botones = m.getConfigurationSection("botones");

        List<ActionButton> acciones = new ArrayList<>();
        for (String id : botones.getKeys(false)) {
            ConfigurationSection b = botones.getConfigurationSection(id);
            String abre = b.getString("abre", "").toLowerCase(Locale.ROOT);
            acciones.add(boton(
                    etiqueta(b.getString("nombre"), b.getString("descripcion")),
                    tooltipDe(b.getString("descripcion")),
                    () -> {
                        if (abre.equals("kits")) abrirMenuKits(p);
                        else if (abre.equals("warps")) abrirMenuWarps(p);
                        else ejecutar(p, b.getString("comando"));
                    }));
        }
        mostrar(p, m.getString("titulo"), m.getString("descripcion"), acciones);
    }

    // ================================================================ menu de kits

    public void abrirMenuKits(Player p) {
        if (esBedrock(p)) {
            abrirMenuBedrock(p, "kits");
        } else {
            abrirMenuKitsJava(p);
        }
    }

    private void abrirMenuKitsJava(Player p) {
        ConfigurationSection k = getConfig().getConfigurationSection("kits");
        List<ActionButton> acciones = new ArrayList<>();
        for (String cat : categorias()) {
            ConfigurationSection cs = k.getConfigurationSection("categorias." + cat);
            acciones.add(boton(
                    etiqueta(cs.getString("nombre"), cs.getString("descripcion")),
                    tooltipDe(cs.getString("descripcion")),
                    () -> abrirCategoriaKits(p, cat)));
        }
        acciones.add(boton(c(k.getString("volver")), null, () -> abrirMenuPrincipal(p)));
        mostrar(p, k.getString("titulo"), k.getString("descripcion"), acciones);
    }

    public void abrirCategoriaKits(Player p, String cat) {
        ConfigurationSection cs = getConfig().getConfigurationSection("kits.categorias." + cat);
        if (cs == null) return;

        List<String> kits = nombresKits(cat);
        List<ActionButton> acciones = new ArrayList<>();
        for (String nombre : kits) {
            String base = "kits." + nombre;
            String precio = precioTxt(kitsCfg.getDouble(base + ".precio", 0));
            String desc = kitsCfg.getString(base + ".descripcion", "");
            String etiqueta = "&f&l" + nombre.toUpperCase(Locale.ROOT) + " &8- " + precio
                    + (desc.isBlank() ? "" : " &8- &7" + desc);
            acciones.add(boton(c(etiqueta), tooltipKit(nombre), () -> reclamarKit(p, nombre)));
        }
        acciones.add(boton(c(getConfig().getString("kits.volver")), null, () -> abrirMenuKits(p)));

        String texto = kits.isEmpty()
                ? "&7Todavia no hay kits en esta categoria."
                : "&fHaz click en un kit para obtenerlo:";
        mostrar(p, cs.getString("titulo", cs.getString("nombre")), texto, acciones);
    }

    private List<String> nombresKits(String categoria) {
        List<String> r = new ArrayList<>();
        ConfigurationSection s = kitsCfg.getConfigurationSection("kits");
        if (s == null) return r;
        for (String n : s.getKeys(false)) {
            String cat = s.getString(n + ".categoria", "dinero");
            if (categoria == null || categoria.equalsIgnoreCase(cat)) r.add(n);
        }
        Collections.sort(r);
        return r;
    }

    private Component tooltipKit(String nombre) {
        String base = "kits." + nombre;
        Component t = Component.empty();
        String desc = kitsCfg.getString(base + ".descripcion", "");
        if (!desc.isBlank()) t = t.append(c(desc)).append(Component.newline());
        t = t.append(c("&7Contiene:"));
        List<?> lista = kitsCfg.getList(base + ".items");
        int n = 0;
        if (lista != null) {
            for (Object o : lista) {
                if (!(o instanceof ItemStack it)) continue;
                if (n++ >= 8) {
                    t = t.append(Component.newline()).append(c("&8..."));
                    break;
                }
                t = t.append(Component.newline())
                        .append(c("&f" + it.getAmount() + "x &7" + bonito(it.getType().name())));
            }
        }
        return t;
    }

    // ================================================================ logica de kits

    private void reclamarKit(Player p, String nombre) {
        String key = nombre.toLowerCase(Locale.ROOT);
        String base = "kits." + key;
        if (!kitsCfg.contains(base)) {
            p.sendMessage(msg("kit-no-existe", "kit", nombre));
            return;
        }

        String cat = kitsCfg.getString(base + ".categoria", "dinero");
        String permiso = getConfig().getString("kits.categorias." + cat + ".permiso", "");
        if (!permiso.isEmpty()
                && !p.hasPermission(permiso)
                && !p.hasPermission("semianarquico.kit." + key)
                && !p.hasPermission("semianarquico.kits.admin")) {
            p.sendMessage(msg("kit-sin-permiso", "kit", key));
            return;
        }

        double precio = kitsCfg.getDouble(base + ".precio", 0);
        if (precio > 0) {
            Economy eco = economia();
            if (eco == null) {
                p.sendMessage(msg("sin-economia"));
                return;
            }
            if (!eco.has(p, precio) || !eco.withdrawPlayer(p, precio).transactionSuccess()) {
                p.sendMessage(msg("sin-dinero", "precio", precioTxt(precio)));
                return;
            }
        }

        List<?> lista = kitsCfg.getList(base + ".items");
        if (lista != null) {
            for (Object o : lista) {
                if (!(o instanceof ItemStack it)) continue;
                p.getInventory().addItem(it.clone()).values()
                        .forEach(resto -> p.getWorld().dropItemNaturally(p.getLocation(), resto));
            }
        }
        p.sendMessage(msg("kit-recibido", "kit", key));
    }

    private void crearKit(Player p, String nombre, double precio, String categoria) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        if (items.isEmpty()) {
            p.sendMessage(msg("kit-vacio"));
            return;
        }
        String base = "kits." + nombre;
        kitsCfg.set(base + ".precio", precio);
        kitsCfg.set(base + ".categoria", categoria);
        kitsCfg.set(base + ".items", items);
        guardar(kitsCfg, kitsFile);
        p.sendMessage(msg("kit-creado", "kit", nombre, "precio", precioTxt(precio), "categoria", categoria));
    }

    private boolean cmdKit(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Player p = soloJugador(sender);
            if (p != null) abrirMenuKits(p);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("semianarquico.kits.admin");

        if (sub.equals("create") || sub.equals("crear")) {
            if (!admin) {
                sender.sendMessage(msg("sin-permiso"));
                return true;
            }
            Player p = soloJugador(sender);
            if (p == null) return true;
            if (args.length < 3) {
                p.sendMessage(msg("uso-kit-create"));
                return true;
            }
            String nombre = args[1].toLowerCase(Locale.ROOT);
            if (!nombre.matches("[a-z0-9_-]{1,32}")) {
                p.sendMessage(msg("nombre-invalido"));
                return true;
            }
            double precio;
            try {
                precio = Double.parseDouble(args[2]);
            } catch (NumberFormatException ex) {
                p.sendMessage(msg("precio-invalido"));
                return true;
            }
            if (precio < 0) {
                p.sendMessage(msg("precio-invalido"));
                return true;
            }
            String cat = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "dinero";
            if (!categorias().contains(cat)) {
                p.sendMessage(msg("categoria-invalida", "lista", String.join(", ", categorias())));
                return true;
            }
            crearKit(p, nombre, precio, cat);
            return true;
        }

        if (sub.equals("delete") || sub.equals("borrar") || sub.equals("eliminar")) {
            if (!admin) {
                sender.sendMessage(msg("sin-permiso"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(msg("uso-kit-delete"));
                return true;
            }
            String nombre = args[1].toLowerCase(Locale.ROOT);
            if (!kitsCfg.contains("kits." + nombre)) {
                sender.sendMessage(msg("kit-no-existe", "kit", nombre));
                return true;
            }
            kitsCfg.set("kits." + nombre, null);
            guardar(kitsCfg, kitsFile);
            sender.sendMessage(msg("kit-borrado", "kit", nombre));
            return true;
        }

        if (sub.equals("desc")) {
            if (!admin) {
                sender.sendMessage(msg("sin-permiso"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(msg("uso-kit-desc"));
                return true;
            }
            String nombre = args[1].toLowerCase(Locale.ROOT);
            if (!kitsCfg.contains("kits." + nombre)) {
                sender.sendMessage(msg("kit-no-existe", "kit", nombre));
                return true;
            }
            kitsCfg.set("kits." + nombre + ".descripcion", unir(args, 2));
            guardar(kitsCfg, kitsFile);
            sender.sendMessage(msg("kit-desc", "kit", nombre));
            return true;
        }

        if (sub.equals("list") || sub.equals("lista")) {
            sender.sendMessage(msg("kit-lista", "lista", String.join(", ", nombresKits(null))));
            return true;
        }

        Player p = soloJugador(sender);
        if (p != null) reclamarKit(p, args[0]);
        return true;
    }

    // ================================================================ warps

    private List<String> nombresWarps() {
        List<String> r = new ArrayList<>();
        ConfigurationSection s = warpsCfg.getConfigurationSection("warps");
        if (s != null) r.addAll(s.getKeys(false));
        Collections.sort(r);
        return r;
    }

    public void abrirMenuWarps(Player p) {
        if (esBedrock(p)) {
            abrirMenuBedrock(p, "warps");
        } else {
            abrirMenuWarpsJava(p);
        }
    }

    private void abrirMenuWarpsJava(Player p) {
        ConfigurationSection w = getConfig().getConfigurationSection("warps");
        List<String> nombres = nombresWarps();
        List<ActionButton> acciones = new ArrayList<>();
        for (String nombre : nombres) {
            String desc = warpsCfg.getString("warps." + nombre + ".descripcion", "");
            if (desc.isBlank()) desc = w.getString("descripcion-por-defecto", "");
            acciones.add(boton(
                    etiqueta("&b&l" + nombre.toUpperCase(Locale.ROOT), desc),
                    tooltipDe(desc),
                    () -> irAWarp(p, nombre)));
        }
        acciones.add(boton(c(w.getString("volver")), null, () -> abrirMenuPrincipal(p)));
        String texto = nombres.isEmpty() ? w.getString("sin-warps") : w.getString("descripcion");
        mostrar(p, w.getString("titulo"), texto, acciones);
    }

    private void irAWarp(Player p, String nombre) {
        String key = nombre.toLowerCase(Locale.ROOT);
        String base = "warps." + key;
        if (!warpsCfg.contains(base)) {
            p.sendMessage(msg("warp-no-existe", "warp", nombre));
            return;
        }
        Location loc = warpsCfg.getLocation(base + ".ubicacion");
        if (loc == null || loc.getWorld() == null) {
            p.sendMessage(msg("mundo-no-cargado"));
            return;
        }
        p.teleport(loc);
        p.sendMessage(msg("warp-tp", "warp", key));
    }

    private boolean cmdWarp(CommandSender sender, String[] args) {
        Player p = soloJugador(sender);
        if (p == null) return true;
        if (args.length == 0) {
            abrirMenuWarps(p);
        } else {
            irAWarp(p, args[0]);
        }
        return true;
    }

    private boolean cmdSetWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("semianarquico.warp.admin")) {
            sender.sendMessage(msg("sin-permiso"));
            return true;
        }
        Player p = soloJugador(sender);
        if (p == null) return true;
        if (args.length < 1) {
            p.sendMessage(msg("uso-setwarp"));
            return true;
        }
        String nombre = args[0].toLowerCase(Locale.ROOT);
        if (!nombre.matches("[a-z0-9_-]{1,32}")) {
            p.sendMessage(msg("nombre-invalido"));
            return true;
        }
        String base = "warps." + nombre;
        warpsCfg.set(base + ".ubicacion", p.getLocation());
        if (args.length >= 2) warpsCfg.set(base + ".descripcion", unir(args, 1));
        guardar(warpsCfg, warpsFile);
        p.sendMessage(msg("warp-creado", "warp", nombre));
        return true;
    }

    private boolean cmdDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("semianarquico.warp.admin")) {
            sender.sendMessage(msg("sin-permiso"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(msg("uso-delwarp"));
            return true;
        }
        String nombre = args[0].toLowerCase(Locale.ROOT);
        if (!warpsCfg.contains("warps." + nombre)) {
            sender.sendMessage(msg("warp-no-existe", "warp", nombre));
            return true;
        }
        warpsCfg.set("warps." + nombre, null);
        guardar(warpsCfg, warpsFile);
        sender.sendMessage(msg("warp-borrado", "warp", nombre));
        return true;
    }

    private boolean cmdWarpDesc(CommandSender sender, String[] args) {
        if (!sender.hasPermission("semianarquico.warp.admin")) {
            sender.sendMessage(msg("sin-permiso"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg("uso-warpdesc"));
            return true;
        }
        String nombre = args[0].toLowerCase(Locale.ROOT);
        if (!warpsCfg.contains("warps." + nombre)) {
            sender.sendMessage(msg("warp-no-existe", "warp", nombre));
            return true;
        }
        warpsCfg.set("warps." + nombre + ".descripcion", unir(args, 1));
        guardar(warpsCfg, warpsFile);
        sender.sendMessage(msg("warp-desc", "warp", nombre));
        return true;
    }

    // ================================================================ /menu

    private boolean cmdMenu(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("semianarquico.menu.reload")) {
                sender.sendMessage(msg("sin-permiso"));
                return true;
            }
            reloadConfig();
            cargarDatos();
            detectarFloodgate();
            sender.sendMessage(msg("recargado"));
            return true;
        }
        Player p = soloJugador(sender);
        if (p == null) return true;
        if (!p.hasPermission("semianarquico.menu")) {
            p.sendMessage(msg("sin-permiso"));
            return true;
        }
        abrirMenuPrincipal(p);
        return true;
    }

    // ================================================================ comandos

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "menu":
                return cmdMenu(sender, args);
            case "kit":
                return cmdKit(sender, args);
            case "warp":
                return cmdWarp(sender, args);
            case "setwarp":
                return cmdSetWarp(sender, args);
            case "delwarp":
                return cmdDelWarp(sender, args);
            case "warpdesc":
                return cmdWarpDesc(sender, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        List<String> opciones = new ArrayList<>();
        boolean admin = sender.hasPermission("semianarquico.kits.admin");

        if (cmd.equals("kit")) {
            if (args.length == 1) {
                if (admin) opciones.addAll(List.of("create", "delete", "desc"));
                opciones.add("list");
                opciones.addAll(nombresKits(null));
            } else if (args.length == 2 && admin
                    && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("desc"))) {
                opciones.addAll(nombresKits(null));
            } else if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
                opciones.addAll(categorias());
            }
        } else if (cmd.equals("warp") || cmd.equals("delwarp") || cmd.equals("warpdesc")) {
            if (args.length == 1) opciones.addAll(nombresWarps());
        } else if (cmd.equals("menu")) {
            if (args.length == 1) opciones.add("reload");
        }

        String actual = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        opciones.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(actual));
        return opciones;
    }
}
