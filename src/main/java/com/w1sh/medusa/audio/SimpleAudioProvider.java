package com.w1sh.medusa.audio;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import discord4j.voice.AudioProvider;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;

public final class SimpleAudioProvider extends AudioProvider {

    private final AudioPlayer player;
    private final MutableAudioFrame mutableAudioFrame;

    public SimpleAudioProvider(final AudioPlayer player) {
        // Allocate a ByteBuffer for Discord4J's AudioProvider to hold audio data for Discord
        super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));
        this.mutableAudioFrame = new MutableAudioFrame();
        mutableAudioFrame.setBuffer(getBuffer());
        this.player = player;
    }

    @PostConstruct
    public void init() {
        // Set LavaPlayer's MutableAudioFrame to use the same buffer as the one we just allocated
        mutableAudioFrame.setBuffer(getBuffer());
    }

    @Override
    public boolean provide() {
        // AudioPlayer writes audio data to its AudioFrame
        final boolean didProvide = player.provide(mutableAudioFrame);
        // If audio was provided, flip from write-mode to read-mode
        if (didProvide) {
            getBuffer().flip();
        }
        return didProvide;
    }


}
