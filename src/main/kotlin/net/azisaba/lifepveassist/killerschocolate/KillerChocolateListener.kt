package net.azisaba.lifepveassist.killerschocolate

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent

class KillerChocolateListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val meta = event.item.itemMeta ?: return
        if (!meta.hasDisplayName()) return
        if (meta.displayName != CHOCOLATE_NAME) return

        val player = event.player
        when (player.displayName) {
            "Liaco14" -> {
                player.sendMessage("§d§lHappy §5§lValentines §d§lday!")
                player.sendMessage("§d§lりあこのために作ったカナダから直送の手作りチョコだぞ。")
            }
            "Y_RyuZU" -> player.sendMessage("§d§l合格おめでとう。彼女できるといいね。")
        }

        when {
            player.hasPermission("aziswitch.ismoderator") -> {
                player.sendMessage("§d貴様は殺さないでやろう。")
                return
            }
            player.hasPermission("aziswitch.isbuilder") -> {
                player.sendMessage("§dふむ、まぁ頑張っているんじゃないか？チョコをやる。引き続き建築を頼むぞ。")
                return
            }
            player.hasPermission("aziswitch.isadmin") -> {
                player.sendMessage("§dふむ、まぁ頑張っているんじゃないか？チョコをやる。")
                return
            }
            player.hasPermission("aziswitch.isdeveloper") -> {
                player.sendMessage("§5いつもお疲れ。ほら、チョコだ。食べろ。")
                return
            }
            player.hasPermission("aziswitch.isalladmin") -> {
                player.sendMessage("§dいつもお疲れ。ほら、チョコだ。食べろ。")
                return
            }
            player.hasPermission("aziswitch.isowner") -> {
                player.sendMessage("§4食ったな？5998円だ。今すぐ払え。")
                return
            }
        }

        val (message, effect) = when {
            player.hasPermission("lifecore.rank10") ->
                "§a君はLife鯖を支える立派な鯖民だ。チョコレートを楽しむといい。" to "minecraft:effect give ${player.name} night_vision 10 0"
            player.hasPermission("lifecore.rank9") ->
                "§2あと一歩で頂点に手が届くところまできた君の努力を認めよう。" to "minecraft:effect give ${player.name} night_vision 10 0"
            player.hasPermission("lifecore.rank8") ->
                "§b頑張っているようだな。そんな君には甘めのチョコを渡そう。" to "minecraft:effect give ${player.name} night_vision 10 0"
            player.hasPermission("lifecore.rank7") ->
                "§e君はこのチョコを食べるにふさわしいレベルに到達しているようだ。食べることを許そう。" to "minecraft:effect give ${player.name} night_vision 10 0"
            player.hasPermission("lifecore.rank6") ->
                "§6君は認めようかとも思ったが、まだ経験が浅いようだな。" to "minecraft:effect give ${player.name} hunger 10 0"
            player.hasPermission("lifecore.rank5") ->
                "§c努力はしているようだがまだ弱点もあるようだ。精進するように。" to "minecraft:effect give ${player.name} blindness 10 0"
            player.hasPermission("lifecore.rank4") ->
                "§c努力はしているようだがまだ早い。精進するように。" to "minecraft:effect give ${player.name} nausea 10 0"
            player.hasPermission("lifecore.rank3") ->
                "§cこのチョコは君にはまだ劇薬だ。ランクを上げて再挑戦するといい。" to "kill ${player.name}"
            player.hasPermission("lifecore.rank2") ->
                "§cこのチョコは君にはまだ早いな。ランクを上げて再挑戦するといい。" to "kill ${player.name}"
            player.hasPermission("lifecore.rank1") ->
                "§cこのチョコは上級者のためのものだ。ランクを上げて再挑戦するといい。" to "kill ${player.name}"
            else ->
                "§4お前にチョコを食う資格はない。資格を得てから食べるんだな。" to "kill ${player.name}"
        }
        player.sendMessage(message)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), effect)
    }

    companion object {
        private const val CHOCOLATE_NAME = "§a§l森§aの手作りチョコ"
    }
}
