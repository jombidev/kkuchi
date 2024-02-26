@file:OptIn(ExperimentalSerializationApi::class)

package dev.jombi.kkuchi

import dev.jombi.kkuchi.config.Configuration
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.upsertCommand
import dev.minn.jda.ktx.jdabuilder.default
import dev.minn.jda.ktx.jdabuilder.light
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import kotlin.io.path.Path
import kotlin.io.path.inputStream

val config get() = Json.decodeFromStream<Configuration>(Path("config.json").inputStream())

suspend fun main() {
    val jda = light(config.token, enableCoroutines = true) {
        enableIntents(GatewayIntent.entries)
        setMemberCachePolicy(MemberCachePolicy.VOICE)
        enableCache(CacheFlag.getPrivileged() + CacheFlag.VOICE_STATE)
    }
    jda.upsertCommand("join", "join the bot for you") {
        isGuildOnly = true
    }.await()
    jda.onCommand("join") {
        val user = it.member!!
        val state = user.voiceState ?: return@onCommand it.replyEmbeds(Embed {
            title = "❌ 실패"
            description = "음성 권한이 없습니다."
        }).await().let{}
        if (!state.inAudioChannel())
            return@onCommand it.replyEmbeds(Embed {
                title = "❌ 실패"
                description = "통화방에 입장 후 이 명령어를 입력 해 주세요."
                field("inAudioChannel", "${state.inAudioChannel()}")
                field("channel", "${state.channel}")
            }).await().let{}
        val man = it.guild!!.audioManager
        if (man.isConnected) {
            if (man.connectedChannel == state.channel)
                return@onCommand it.replyEmbeds(Embed {
                    title = "❌ 실패"
                    description = "이미 현재 채널에 접속하고 있습니다."
                }).await().let{}
            it.replyEmbeds(Embed {
                title = "❌ 실패"
                description = "이미 다른 유저가 저를 사용중입니다."
            }).await()
            return@onCommand
        }
        man.openAudioConnection(state.channel!!)
        it.replyEmbeds(Embed {
            title = "✅ 성공!"
            description = "``${state.channel!!.name}`` 채널에 접속 했습니다!"
        }).await()
    }
    jda.upsertCommand("leave", "join the bot for you") {
        isGuildOnly = true
    }.await()
    jda.onCommand("leave") {
        val user = it.member!!
        val state = user.voiceState ?: return@onCommand it.replyEmbeds(Embed {
            title = "❌ 실패"
            description = "음성 권한이 없습니다."
        }).await().let{}
        val man = it.guild!!.audioManager
        if (!man.isConnected)
            return@onCommand it.replyEmbeds(Embed {
                title = "❌ 실패"
                description = "접속해 있는 음성 채널이 없습니다."
            }).await().let{}
        if (!state.inAudioChannel()) return@onCommand it.replyEmbeds(Embed {
            title = "❌ 실패"
            description = "음성 채널에 접속 후 사용 해주세요."
        }).await().let{}
        if (state.channel != man.connectedChannel) return@onCommand it.replyEmbeds(Embed {
            title = "❌ 실패"
            description = "동일한 음성 채널에 접속 후 사용 해주세요."
        }).await().let{}
        man.closeAudioConnection()
        it.replyEmbeds(Embed {
            title = "✅ 성공!"
            description = "``${state.channel!!.name}`` 채널을 나갔습니다."
        }).await()
    }
    jda.listener<GuildVoiceUpdateEvent> {
        if (it.channelLeft != null && it.channelLeft!!.members.any { it.user.id == jda.selfUser.id } && it.channelLeft!!.members.size == 1)
            it.guild.audioManager.closeAudioConnection() // no humans in voice channel
    }
}