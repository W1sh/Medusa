package com.w1sh.medusa.api.audio.listeners;

import com.w1sh.medusa.api.audio.events.PlayTrackEvent;
import com.w1sh.medusa.core.dispatchers.CommandEventDispatcher;
import com.w1sh.medusa.core.listeners.MultipleArgsEventListener;
import com.w1sh.medusa.core.managers.AudioConnectionManager;
import com.w1sh.medusa.core.managers.PermissionManager;
import com.w1sh.medusa.utils.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class PlayTrackListener implements MultipleArgsEventListener<PlayTrackEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PlayTrackListener.class);

    @Value("${event.voice.play}")
    private String voicePlay;
    @Value("${event.unsupported}")
    private String unsupported;

    public PlayTrackListener(CommandEventDispatcher eventDispatcher) {
        eventDispatcher.registerListener(this);
    }

    @Override
    public Class<PlayTrackEvent> getEventType() {
        return PlayTrackEvent.class;
    }

    @Override
    public Mono<Void> execute(PlayTrackEvent event) {
        return Mono.justOrEmpty(event)
                .filterWhen(this::validate)
                .filterWhen(ev -> PermissionManager.getInstance().hasPermissions(ev, ev.getPermissions()))
                .flatMap(ev -> Mono.justOrEmpty(ev.getMessage().getContent()))
                .zipWith(Mono.justOrEmpty(event.getGuildId()))
                .flatMap(tuple -> AudioConnectionManager.getInstance().requestTrack(tuple.getT1(), tuple.getT2()))
                .flatMap(t -> Messenger.delete(event.getMessage()))
                .doOnError(throwable -> logger.error("Failed to play track", throwable))
                .then();
    }

    @Override
    public Mono<Boolean> validate(PlayTrackEvent event) {
        return Mono.justOrEmpty(event.getMessage().getContent())
                .map(content -> content.split(" "))
                .filter(split -> {
                    if(split.length != 2){
                        Messenger.send(event, unsupported).subscribe();
                        return false;
                    }
                    return true;
                })
                .hasElement();
    }
}
