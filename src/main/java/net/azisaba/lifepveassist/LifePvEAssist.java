package net.azisaba.lifepveassist;

import com.github.retrooper.packetevents.PacketEvents;
import net.azisaba.lifepveassist.command.LifePvEAssistCommand;
import net.azisaba.lifepveassist.listener.CraftListener;
import net.azisaba.lifepveassist.listener.LifeCraftListener;
import net.azisaba.lifepveassist.listener.PvPCancelListener;
import net.azisaba.lifepveassist.minecraft.server.BlockingCommand;
import net.azisaba.lifepveassist.minecraft.server.PacketListenerImpl;
import net.azisaba.lifepveassist.minecraft.server.PlayerJoinListener;
import net.azisaba.lifepvelevel.SpigotPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifePvEAssist extends JavaPlugin {

    private static LifePvEAssist instance;
    public static SpigotPlugin pveLevel;

    public LifePvEAssist() {
        instance = this;
    }

    public static LifePvEAssist getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        this.getCommand("lifepveassist").setExecutor(new LifePvEAssistCommand());
        this.getCommand("blocking").setExecutor(new BlockingCommand());

        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(new LifeCraftListener(), this);
        pluginManager.registerEvents(new CraftListener(), this);
        pluginManager.registerEvents(new PvPCancelListener(this), this);
        pluginManager.registerEvents(new PlayerJoinListener(this), this);

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerImpl());

        this.getLogger().info("LifePvEAssist has been enabled.");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("LifePvEAssist has been disabled.");
    }

    public void setup() {
        if (getServer().getPluginManager().getPlugin("LifePvELevel") != null) {
            pveLevel = (SpigotPlugin) getServer().getPluginManager().getPlugin("LifePvELevel");
        }
    }
}
