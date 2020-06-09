package com.w1sh.medusa.player;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.w1sh.medusa.data.LoopAction;
import com.w1sh.medusa.player.listeners.AudioLoadResultListener;
import com.w1sh.medusa.player.listeners.TrackEventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Slf4j
public class AudioTrackSchedulerImpl implements AudioTrackScheduler {

    private static final Integer MAX_QUEUE_SIZE = 250;

    private final AudioPlayer player;
    private final TrackEventListener trackEventListener;
    private final AudioLoadResultHandler audioLoadResultListener;
    private final BlockingDeque<AudioTrack> queue;

    private LoopAction loopAction;

    AudioTrackSchedulerImpl(final AudioPlayer player, final TrackEventListener trackEventListener) {
        this.player = player;
        this.trackEventListener = trackEventListener;
        this.player.addListener(trackEventListener);
        this.audioLoadResultListener = new AudioLoadResultListener(player);
        this.queue = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);
        this.loopAction = LoopAction.OFF;
    }

    @Override
    public AudioTrack queue() {
        AudioTrack nextTrack = this.queue.poll();
        if (nextTrack == null) return null;
        switch (loopAction) {
            case TRACK: queue.offerFirst(this.player.getPlayingTrack().makeClone());
                break;
            case QUEUE: queue.offerLast(this.player.getPlayingTrack().makeClone());
                break;
            default: break;
        }
        player.playTrack(nextTrack);
        return nextTrack;
    }

    @Override
    public AudioTrack skip() {
        player.stopTrack();
        AudioTrack nextTrack = this.queue.poll();
        if(nextTrack != null){
            player.playTrack(nextTrack);
            return nextTrack;
        } else return null;
    }

    @Override
    public void stop() {
        player.stopTrack();
        queue.clear();
    }

    @Override
    public AudioTrack replay() {
        player.getPlayingTrack().setPosition(Math.negateExact(player.getPlayingTrack().getPosition()));
        return player.getPlayingTrack();
    }

    @Override
    public Queue<AudioTrack> shuffle() {
        final var list = new ArrayList<>(queue);
        Collections.shuffle(list);
        queue.clear();
        queue.addAll(list);
        return queue;
    }

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public void forward(long milliseconds) {
        final AudioTrack playingTrack = player.getPlayingTrack();
        final long newTrackPosition = (playingTrack.getPosition() - milliseconds) < 0 ? 0 : milliseconds;
        playingTrack.setPosition(newTrackPosition);
    }

    @Override
    public void rewind(long milliseconds) {
        player.getPlayingTrack().setPosition(milliseconds);
    }

    @Override
    public boolean pause() {
        if(!player.isPaused()) player.setPaused(true);
        return player.isPaused();
    }

    @Override
    public boolean resume() {
        if(player.isPaused()) player.setPaused(false);
        return player.isPaused();
    }

    @Override
    public LoopAction loop(String loop) {
        this.loopAction = LoopAction.of(loop);
        return this.loopAction;
    }

    @Override
    public Queue<AudioTrack> getFullQueue() {
        Queue<AudioTrack> fullQueue = new LinkedBlockingDeque<>();
        getPlayingTrack().ifPresent(fullQueue::add);
        fullQueue.addAll(queue);
        return fullQueue;
    }

    @Override
    public long getQueueDuration() {
        return getFullQueue().stream()
                .map(AudioTrack::getDuration)
                .reduce(Long::sum)
                .orElse(0L);
    }

    public AudioLoadResultHandler getAudioLoadResultListener() {
        return audioLoadResultListener;
    }

    public Optional<AudioTrack> getPlayingTrack() {
        return Optional.ofNullable(player.getPlayingTrack());
    }

    public BlockingDeque<AudioTrack> getQueue() {
        return queue;
    }
}
