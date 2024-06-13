package net.rabbitnetwork.particles;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;

public class Particles extends JavaPlugin implements Listener {

    private Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();
    private ConfigurationSection particleConfig;
    private ConfigurationSection messagesConfig;
    private List<Color> rainbowColors;
    private int colorIndex = 0;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        particleConfig = config.getConfigurationSection("particles");
        messagesConfig = config.getConfigurationSection("messages");
        getCommand("particles").setTabCompleter(new tabComplete());
        if (particleConfig == null || messagesConfig == null) {
            getLogger().warning("Configuration sections not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize rainbow colors
        rainbowColors = Arrays.asList(
                Color.fromRGB(255, 0, 75),
                Color.fromRGB(255, 111, 0),
                Color.fromRGB(255, 255, 0),
                Color.fromRGB(0, 224, 4),
                Color.fromRGB(0, 17, 255),
                Color.fromRGB(255, 0, 153),  // Magenta
                Color.fromRGB(152, 3, 252)   // Violet
        );

        // Load player data
        playerDataFile = new File(getDataFolder(), "players.yml");
        if (!playerDataFile.exists()) {
            playerDataFile.getParentFile().mkdirs();
            saveResource("players.yml", false);
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        particleTasks.values().forEach(BukkitRunnable::cancel);
        particleTasks.clear();
        savePlayerData();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.hasPermission("particles.show") && particleConfig.getBoolean("enabled", true)) {
            if (!particleTasks.containsKey(uuid) && playerDataConfig.getBoolean(uuid.toString() + ".enabled", false)) {
                ParticleTask task = new ParticleTask(player, playerDataConfig.getString(uuid.toString() + ".type", "rainbow"));
                task.runTaskTimer(this, 0, particleConfig.getInt("frequency", 1));
                particleTasks.put(uuid, task);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BukkitRunnable task = particleTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private class ParticleTask extends BukkitRunnable {
        private final Player player;
        private final String particleType;
        private final Particle particle;
        private final int amount;
        private final double speed;

        public ParticleTask(Player player, String particleType) {
            this.player = player;
            this.particleType = particleType != null ? particleType : "rainbow"; // Default to "rainbow" if particleType is null
            this.particle = Particle.valueOf(particleConfig.getString("type", "REDSTONE"));
            this.amount = particleConfig.getInt("amount", 10);
            this.speed = particleConfig.getDouble("speed", 1.0);
        }


        @Override
        public void run() {
            boolean displayWhenFlying = particleConfig.getConfigurationSection("display").getBoolean("when_flying", true);
            if (displayWhenFlying && player.isFlying()) {
                for (int i = 0; i < amount; i++) {
                    double x = player.getLocation().getX() + (Math.random() - 0.5);
                    double y = player.getLocation().getY() + 0.1;
                    double z = player.getLocation().getZ() + (Math.random() - 0.5);
                    if (particle == Particle.REDSTONE) {
                        if (particleType.equals("rainbow")) {
                            Color color = rainbowColors.get(colorIndex);
                            player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed, new Particle.DustOptions(color, 1));
                            colorIndex = (colorIndex + 1) % rainbowColors.size();
                        } else if (particleType.equals("cloud")) {
                            Color color = Color.fromRGB(255, 255, 255); // White
                            player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed, new Particle.DustOptions(color, 1));
                            color = Color.fromRGB(128, 128, 128); // Gray
                            player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed, new Particle.DustOptions(color, 1));
                        }
                    } else {
                        player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed);
                    }
                }
            }
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("particles")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (player.hasPermission("particles.show")) {
                    if (args.length == 0) {
                        toggleParticles(player, null); // Toggle particles without specifying a type
                    } else if (args.length == 1 && (args[0].equalsIgnoreCase("rainbow") || args[0].equalsIgnoreCase("cloud"))) {
                        toggleParticles(player, args[0]); // Toggle particles with specified type
                    } else {
                        player.sendMessage(messagesConfig.getString("invalid_type", "Invalid particle type! Use 'rainbow' or 'cloud'."));
                    }
                } else {
                    player.sendMessage(messagesConfig.getString("no_permission", "You don't have permission to use this command!"));
                }
            } else {
                sender.sendMessage(messagesConfig.getString("command_only_for_players", "This command can only be used by players!"));
            }
            return true;
        }
        return false;
    }


    private void toggleParticles(Player player, String type) {
        UUID uuid = player.getUniqueId();
        boolean enabled = playerDataConfig.getBoolean(uuid.toString() + ".enabled", false);
        String currentType = playerDataConfig.getString(uuid.toString() + ".type");

        if (currentType == null || !currentType.equals(type)) { // Check if currentType is null or different from the new type
            if (enabled) {
                if (particleTasks.containsKey(uuid)) {
                    particleTasks.get(uuid).cancel();
                    particleTasks.remove(uuid);
                }
                playerDataConfig.set(uuid.toString() + ".enabled", false);
                player.sendMessage(messagesConfig.getString("particles_disabled", "Particles disabled."));
            } else {
                ParticleTask task = new ParticleTask(player, type);
                task.runTaskTimer(this, 0, particleConfig.getInt("frequency", 1));
                particleTasks.put(uuid, task);
                playerDataConfig.set(uuid.toString() + ".enabled", true);
                playerDataConfig.set(uuid.toString() + ".type", type);
                player.sendMessage(messagesConfig.getString("particles_enabled", "Particles enabled."));
            }

            savePlayerData();
        } else {
            player.sendMessage(messagesConfig.getString("particles_already_enabled", "Particles are already enabled with this type!"));
        }
    }



    private void savePlayerData() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save players.yml!");
            e.printStackTrace();
        }
    }
}
