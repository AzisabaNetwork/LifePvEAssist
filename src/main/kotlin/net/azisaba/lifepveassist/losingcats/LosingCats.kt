package net.azisaba.lifepveassist.losingcats

import io.lumine.mythic.bukkit.MythicBukkit
import net.azisaba.lifepveassist.LifePvEAssist
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.RegisteredServiceProvider
import java.util.UUID
import kotlin.random.Random

class LosingCats(private val plugin: LifePvEAssist) : Listener {

    private val cooldowns = LinkedHashMap<UUID, Long>()

    private fun runLater(delayTicks: Long, task: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, delayTicks)
    }

    private fun dispatchConsole(command: String) {
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command)
    }

    @EventHandler
    fun useStand(event: PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
        ) {
            return
        }
        val player = event.player
        val item: ItemStack = player.inventory.itemInMainHand
        val mmid = MythicUtil.getMythicType(item) ?: return
        if (mmid != "96neko96_LosingCats") return

        val playerId = player.uniqueId
        val currentTime = System.currentTimeMillis()
        val expireTime = cooldowns[playerId]
        if (expireTime != null && expireTime > currentTime) return

        losingCats1(player)
        cooldowns[playerId] = currentTime + 20000
        runLater(400) { cooldowns.remove(playerId) }
    }

    fun losingCats1(player: Player) {
        runLater(20) { player.sendMessage("§f96ne§7ko_96§f:何？") }
        runLater(40) { player.sendMessage("§f96ne§7ko_96§f:ギャンブルがしたい？") }
        val rsp: RegisteredServiceProvider<Economy>? =
            Bukkit.getServicesManager().getRegistration(Economy::class.java)
        val economy = rsp?.provider
        val balance: Double = economy?.getBalance(player) ?: 0.0
        if (balance < 1000000.0) {
            runLater(60) { player.sendMessage("§f96ne§7ko_96§f:金ねえやつはけえんなあ！！！！！！！！") }
            return
        }
        runLater(60) { dispatchConsole("eco take ${player.name} 1000000") }
        runLater(80) { player.sendMessage("§f96ne§7ko_96§f:-式で良いっすか？") }
        runLater(100) { player.sendMessage("§f96ne§7ko_96§f:良いっすよねありがとうございます！") }
        runLater(120) { player.sendMessage("§f96ne§7ko_96§f:(え～～～っと…ここらへんに確か…不正ダイスが…おっ！あったあった！！）") }
        runLater(140) { player.sendMessage("§f96ne§7ko_96§f:じゃあ先行どうぞ！！") }
        if (Random.nextInt(100) <= 44) {
            losingCatsIllegal(player)
        } else {
            losingCatsNormal(player)
        }
    }

    fun losingCatsIllegal(player: Player) {
        val playerDice = Random.nextInt(2, 101)
        runLater(160) { player.sendMessage("§f§l${player.name}§c§lさんの100ダイスの出目は§e§l${playerDice}§c§lです！") }
        if (playerDice <= 50) {
            runLater(180) { player.sendMessage("§f96ne§7ko_96§f:まぁぁぁああっっっずい！！") }
        } else {
            runLater(180) { player.sendMessage("§f96ne§7ko_96§f:ふんっ余裕やね") }
        }
        val nekoDice = Random.nextInt(1, playerDice)
        runLater(220) { player.sendMessage("§f§l96neko_96§c§lさんの100ダイスの出目は§e§l${nekoDice}§c§lです！") }
        if (nekoDice == playerDice) {
            losingCatsDraw(player)
        } else {
            losingCatsWin(player)
        }
    }

    fun losingCatsNormal(player: Player) {
        val playerDice = Random.nextInt(1, 101)
        runLater(160) { player.sendMessage("§f§l${player.name}§c§lさんの100ダイスの出目は§e§l${playerDice}§c§lです！") }
        if (playerDice <= 50) {
            runLater(180) { player.sendMessage("§f96ne§7ko_96§f:まぁぁぁああっっっずい！！") }
        } else {
            runLater(180) { player.sendMessage("§f96ne§7ko_96§f:ふんっ余裕やね") }
        }
        val nekoDice = Random.nextInt(1, 101)
        runLater(220) { player.sendMessage("§f§l96neko_96§c§lさんの100ダイスの出目は§e§l${nekoDice}§c§lです！") }
        when {
            nekoDice == playerDice -> losingCatsDraw(player)
            nekoDice < playerDice -> losingCatsWin(player)
            else -> losingCatsLose(player)
        }
    }

    fun losingCatsWin(player: Player) {
        val root = Random.nextInt(4)
        val msg = when (root) {
            0 -> "§f96ne§7ko_96§f:なかなかやるやん？でもまあまあまあｗ自分の勝ちっすｗお疲れっしたｗ"
            1 -> "§f96ne§7ko_96§f:ふんっっっ！！ 俺の勝ち！！なんで負けたか！明日まｄ（ｒｙ"
            2 -> "§f96ne§7ko_96§f:余裕すぎワロタ～＾"
            else -> "§f96ne§7ko_96§f:いよおおおおおおし！！！！！！！"
        }
        runLater(240) { player.sendMessage(msg) }
        runLater(260) { player.sendMessage("§f96ne§7ko_96§f:（やっぱ歪ダイス様々っすわぁ！！！！！）") }
        runLater(280) { player.sendMessage("§f96ne§7ko_96§f:対戦あざした！") }
    }

    fun losingCatsLose(player: Player) {
        when (Random.nextInt(4)) {
            0 -> {
                runLater(240) { player.sendMessage("§f96ne§7ko_96§f:………負け………ちゃっ………た………") }
                runLater(260) { player.sendMessage("§f96ne§7ko_96§f:ﾜ………ﾜｧ…………") }
            }
            1 -> {
                runLater(240) { player.sendMessage("§f96ne§7ko_96§f:なんだよもおおおおおお！！！！！") }
                runLater(260) { player.sendMessage("§f96ne§7ko_96§f:またかよおおおおおおおおお！！！！") }
            }
            2 -> {
                runLater(240) { player.sendMessage("§f96ne§7ko_96§f:無くなったら足せばいいだけやから………") }
                runLater(260) { player.sendMessage("§f96ne§7ko_96§f:借金！！！！追加ァァァァァァ！！！！！") }
            }
            else -> {
                runLater(250) { player.sendMessage("§f96ne§7ko_96§f:1050年…地下行きっ………！！") }
            }
        }
        runLater(280) { player.sendMessage("§f96ne§7ko_96§f:（これ+式用の歪ダイスじゃんけ………）") }
        runLater(300) { player.sendMessage("§f96ne§7ko_96§f:200万…っすね…はい………") }
        runLater(320) { dispatchConsole("eco give ${player.name} 2000000") }
    }

    fun losingCatsDraw(player: Player) {
        runLater(240) { player.sendMessage("§f96ne§7ko_96§f:まあまあまあまあｗ 仲良くいきましょうよｗ") }
        runLater(260) { dispatchConsole("eco give ${player.name} 1000000") }
    }

    private object MythicUtil {
        fun getMythicType(item: ItemStack?): String? {
            if (item == null || item.type == Material.AIR) return null
            return MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)
        }
    }
}
