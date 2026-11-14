package net.azisaba.lifepveassist.ffn.listener;

import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillExecutor;
import net.azisaba.lifepveassist.ffn.mechanic.Body;
import net.azisaba.lifepveassist.ffn.mechanic.CustomDamage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicLoadListener implements Listener {

    public MythicLoadListener() {}

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String mechanicName = event.getMechanicName();
        SkillExecutor manager = event.getContainer().getManager();
        if (mechanicName.equalsIgnoreCase("CustomDamage")) {
            event.register(new CustomDamage(manager, event.getConfig()));
        } else if (mechanicName.equalsIgnoreCase("body")) {
            event.register(new Body(manager, event.getConfig()));
        }
    }
}
