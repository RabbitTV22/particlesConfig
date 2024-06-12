package net.rabbitnetwork.particles;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        particleConfig = config.getConfigurationSection("particles");
        messagesConfig = config.getConfigurationSection("messages");

        if (particleConfig == null || messagesConfig == null) {
            getLogger().warning("Configuration sections not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize rainbow colors
        rainbowColors = Arrays.asList(
                Color.RED,
                Color.ORANGE,
                Color.YELLOW,
                Color.GREEN,
                Color.BLUE,
                Color.fromRGB(255, 0, 153),  // Indigo
                Color.fromRGB(152, 3, 252)  // Violet
        );

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        particleTasks.values().forEach(BukkitRunnable::cancel);
        particleTasks.clear();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("particles.show") && particleConfig.getBoolean("enabled", true)) {
            UUID uuid = player.getUniqueId();
            if (!particleTasks.containsKey(uuid)) {
                ParticleTask task = new ParticleTask(player);
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
        private final Particle particle;
        private final int amount;
        private final double speed;

        public ParticleTask(Player player) {
            this.player = player;
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
                        Color color = rainbowColors.get(colorIndex);
                        player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed, new Particle.DustOptions(color, 1));
                    } else {
                        player.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, speed);
                    }
                }
                colorIndex = (colorIndex + 1) % rainbowColors.size();
            }
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("particles")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (player.hasPermission("particles.show")) {
                    toggleParticles(player);
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

    private void toggleParticles(Player player) {
        UUID uuid = player.getUniqueId();
        if (particleTasks.containsKey(uuid)) {
            particleTasks.get(uuid).cancel();
            particleTasks.remove(uuid);
            player.sendMessage(messagesConfig.getString("particles_disabled", "Particles disabled."));
        } else {
            ParticleTask task = new ParticleTask(player);
            task.runTaskTimer(this, 0, particleConfig.getInt("frequency", 1));
            particleTasks.put(uuid, task);
            player.sendMessage(messagesConfig.getString("particles_enabled", "Particles enabled."));
        }
    }
}
