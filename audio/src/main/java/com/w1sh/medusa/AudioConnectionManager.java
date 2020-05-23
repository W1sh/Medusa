package com.w1sh.medusa;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.w1sh.medusa.data.events.Event;
import com.w1sh.medusa.dispatchers.ResponseDispatcher;
import com.w1sh.medusa.listeners.TrackEventListener;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.voice.VoiceConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Component
public final class AudioConnectionManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final Logger logger = LoggerFactory.getLogger(AudioConnectionManager.class);

    private final AudioPlayerManager playerManager;
    private final Map<Long, AudioConnection> audioConnections;
    private final ResponseDispatcher responseDispatcher;

    public AudioConnectionManager(AudioPlayerManager playerManager, ResponseDispatcher responseDispatcher) {
        this.playerManager = playerManager;
        this.responseDispatcher = responseDispatcher;
        this.audioConnections = new HashMap<>();
    }

    public Mono<TrackScheduler> requestTrack(Long guildId, String trackLink){
        return Mono.justOrEmpty(audioConnections.get(guildId))
                .map(AudioConnection::getTrackScheduler)
                .doOnNext(trackScheduler -> playerManager.loadItem(trackLink, trackScheduler));
    }

    public Mono<MessageChannel> requestTrack(Event event){
        Long guildId = event.getGuildId().map(Snowflake::asLong).orElse(0L);

        return Mono.justOrEmpty(event)
                .map(ev -> ev.getArguments().get(0))
                .zipWith(Mono.justOrEmpty(audioConnections.get(guildId))
                        .switchIfEmpty(joinVoiceChannel(event))
                        .map(AudioConnection::getTrackScheduler))
                .flatMap(tuple -> event.getMessage().getChannel()
                        .doOnNext(messageChannel -> {
                            tuple.getT2().updateResponseChannel(messageChannel);
                            playerManager.loadItem(tuple.getT1(), tuple.getT2());
                        }))
                .doOnSuccess(tuple -> logger.info("Loaded song request to voice channel in guild <{}>", guildId))
                .onErrorResume(throwable -> Mono.fromRunnable(() -> logger.error("Failed to load requested track", throwable)));
    }

    public Mono<AudioConnection> joinVoiceChannel(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(chan -> {
                    final AudioPlayer audioPlayer = playerManager.createPlayer();
                    final SimpleAudioProvider audioProvider = new SimpleAudioProvider(audioPlayer);
                    return chan.join(spec1 -> spec1.setProvider(audioProvider))
                            .zipWith(event.getMessage().getChannel())
                            .flatMap(tuple -> createAudioConnection(audioPlayer, tuple.getT1(), tuple.getT2()));
                })
                .doOnSuccess(audioConnection -> logger.info("Client joined voice channel in guild <{}>", event.getGuildId().map(Snowflake::asLong).orElse(0L)))
                .onErrorResume(throwable -> Mono.fromRunnable(() -> logger.error("Failed to join voice channel", throwable)));
    }

    public Mono<Boolean> leaveVoiceChannel(Snowflake guildIdSnowflake) {
        return Mono.just(guildIdSnowflake)
                .doOnSuccess(snowflake -> logger.info("Client leaving voice channel in guild <{}>", snowflake.asLong()))
                .flatMap(this::getAudioConnection)
                .doOnNext(connection -> destroyAudioConnection(guildIdSnowflake.asLong(), connection))
                .onErrorResume(throwable -> Mono.fromRunnable(() -> logger.error("Failed to leave voice channel", throwable)))
                .hasElement();
    }

    public Mono<AudioConnection> scheduleLeave(Event event) {
        return Mono.justOrEmpty(event.getGuildId())
                .flatMap(this::getAudioConnection)
                .filter(Predicate.not(AudioConnection::isLeaving))
                .zipWhen(connection -> event.getMessage().getChannel(), this::scheduleLeave);
    }

    private AudioConnection scheduleLeave(AudioConnection audioConnection, MessageChannel messageChannel){
        audioConnection.getTrackScheduler().stopQueue(messageChannel);
        audioConnection.setLeaving(true);
        logger.info("Scheduling client to leave voice channel in guild <{}> after <{}> seconds",
                ((GuildChannel) messageChannel).getGuildId().asLong(), TIMEOUT.getSeconds());
        Schedulers.elastic().schedule(() -> leaveVoiceChannel(((GuildChannel) messageChannel).getGuildId()).subscribe(), 120, TimeUnit.SECONDS);
        return audioConnection;
    }

    public void shutdown(){
        logger.info("Starting shutdown of AudioConnectionManager");
        logger.info("Terminating <{}> audio connections", audioConnections.size());
        audioConnections.values().forEach(AudioConnection::destroy);
    }

    public Mono<AudioConnection> getAudioConnection(Snowflake guildIdSnowflake) {
        logger.info("Retrieving audio connection for guild with id <{}>", guildIdSnowflake.asLong());
        return Mono.justOrEmpty(audioConnections.get(guildIdSnowflake.asLong()));
    }

    private Mono<AudioConnection> createAudioConnection(AudioPlayer player, VoiceConnection voiceConnection, MessageChannel channel){
        final Long guildId = ((GuildChannel) channel).getGuildId().asLong();
        final TrackEventListener trackEventListener = new TrackEventListener(this, channel, responseDispatcher);

        Mono<Void> disconnect = voiceConnection.disconnect()
                .doOnSuccess(a -> logger.info("Destroying audio connection in guild <{}>", guildId))
                .then();

        final AudioConnection audioConnection = new AudioConnection(player, disconnect, trackEventListener);

        logger.info("Creating new audio connection in guild <{}>", guildId);
        audioConnections.put(guildId, audioConnection);
        return Mono.just(audioConnections.get(guildId));
    }

    private void destroyAudioConnection(Long guildId, AudioConnection connection){
        connection.destroy();
        audioConnections.remove(guildId);
    }
}
