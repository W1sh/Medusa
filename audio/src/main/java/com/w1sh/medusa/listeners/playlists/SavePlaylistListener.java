package com.w1sh.medusa.listeners.playlists;

import com.w1sh.medusa.AudioConnection;
import com.w1sh.medusa.AudioConnectionManager;
import com.w1sh.medusa.data.Playlist;
import com.w1sh.medusa.data.Track;
import com.w1sh.medusa.data.responses.TextMessage;
import com.w1sh.medusa.dispatchers.ResponseDispatcher;
import com.w1sh.medusa.events.playlists.SavePlaylistEvent;
import com.w1sh.medusa.listeners.EventListener;
import com.w1sh.medusa.mappers.AudioTrack2TrackMapper;
import com.w1sh.medusa.services.PlaylistService;
import discord4j.core.object.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public final class SavePlaylistListener implements EventListener<SavePlaylistEvent> {

    private final PlaylistService playlistService;
    private final ResponseDispatcher responseDispatcher;
    private final AudioConnectionManager audioConnectionManager;
    private final AudioTrack2TrackMapper audioTrack2TrackMapper;

    @Override
    public Class<SavePlaylistEvent> getEventType() {
        return SavePlaylistEvent.class;
    }

    @Override
    public Mono<Void> execute(SavePlaylistEvent event) {
        Mono<List<Track>> tracks = Mono.justOrEmpty(event.getGuildId())
                .flatMap(audioConnectionManager::getAudioConnection)
                .flatMap(this::getAllTracks);

        return tracks.map(t -> createPlaylist(event, t))
                .flatMap(playlistService::save)
                .flatMap(playlist -> createSavePlaylistSuccessMessage(event, playlist))
                .switchIfEmpty(createFailedSaveErrorMessage(event))
                .onErrorResume(throwable -> Mono.fromRunnable(() -> log.error("Failed to save playlist", throwable)))
                .doOnNext(responseDispatcher::queue)
                .doAfterTerminate(responseDispatcher::flush)
                .then();
    }

    private Playlist createPlaylist(SavePlaylistEvent event, List<Track> tracks){
        String userId = event.getMember().map(member -> member.getId().asString()).orElseThrow();
        String name = event.getArguments().getOrDefault(0, "Playlist");
        return new Playlist(userId, name, tracks);
    }

    private Mono<TextMessage> createFailedSaveErrorMessage(SavePlaylistEvent event){
        return event.getMessage().getChannel()
                .map(channel -> new TextMessage(channel, String.format("**%s**, could not save your playlist, try again later!",
                        event.getMember().flatMap(Member::getNickname).orElse("")), false));
    }

    private Mono<TextMessage> createSavePlaylistSuccessMessage(SavePlaylistEvent event, Playlist playlist){
        return event.getMessage().getChannel()
                .map(channel -> new TextMessage(channel, String.format("**%s**, saved your playlist with %d tracks!",
                        event.getMember().flatMap(Member::getNickname).orElse(""),
                        playlist.getTracks().size()), false));
    }

    private Mono<List<Track>> getAllTracks(AudioConnection audioConnection){
        return Flux.fromIterable(audioConnection.getTrackScheduler().getFullQueue())
                .map(audioTrack2TrackMapper::map)
                .collectList();
    }
}
