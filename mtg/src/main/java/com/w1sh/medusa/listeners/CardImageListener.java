package com.w1sh.medusa.listeners;

import com.w1sh.medusa.core.dispatchers.CommandEventDispatcher;
import com.w1sh.medusa.core.events.EventFactory;
import com.w1sh.medusa.core.listeners.EventListener;
import com.w1sh.medusa.events.CardImageEvent;
import com.w1sh.medusa.services.CardService;
import com.w1sh.medusa.utils.Messenger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.awt.*;

@Component
public class CardImageListener implements EventListener<CardImageEvent> {

    private final CardService cardService;

    public CardImageListener(CommandEventDispatcher eventDispatcher, CardService cardService) {
        this.cardService = cardService;
        EventFactory.registerEvent(CardImageEvent.INLINE_PREFIX, CardImageEvent.class);
        eventDispatcher.registerListener(this);
    }

    @Override
    public Class<CardImageEvent> getEventType() {
        return CardImageEvent.class;
    }

    @Override
    public Mono<Void> execute(CardImageEvent event) {
        return Mono.just(event)
                .filterWhen(this::validate)
                .flatMap(ev -> Mono.justOrEmpty(ev.getInlineArgument()))
                .flatMap(cardService::getCardByName)
                .zipWith(event.getMessage().getChannel())
                .flatMap(tuple -> Messenger.send(tuple.getT2(), embedCreateSpec -> {
                    embedCreateSpec.setColor(Color.GREEN);
                    embedCreateSpec.setUrl(tuple.getT1().getUri());
                    embedCreateSpec.setTitle(tuple.getT1().getName());
                    embedCreateSpec.setImage(tuple.getT1().getImage().getNormal());
                }))
                .then();
    }

    private Mono<Boolean> validate(CardImageEvent event) {
        return Mono.just(event.getInlineArgument() != null && !event.getInlineArgument().isBlank());
    }
}