package com.w1sh.medusa.services;

import com.w1sh.medusa.rest.ScryfallClient;
import com.w1sh.medusa.rest.resources.Card;
import com.w1sh.medusa.rest.resources.ListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

@Component
public final class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);
    private final ScryfallClient scryfallClient;
    private final CardCacheService cardCacheService;

    public CardService(ScryfallClient scryfallClient, CardCacheService cardCacheService) {
        this.scryfallClient = scryfallClient;
        this.cardCacheService = cardCacheService;
    }

    public Mono<Card> getCardByName(String name) {
        return cardCacheService.getCardByName(name, () -> scryfallClient.getCardByName(name));
    }

    public Mono<List<Card>> getCardsByName(String name) {
        return scryfallClient.getCardsByName(name)
                .doOnNext(response -> log.info("Retrieved {} cards with name similar to \"{}\"", response.getTotalCards(), name))
                .flatMapIterable(ListResponse::getData)
                .doOnNext(card -> cardCacheService.put(card.getName(), card))
                .collectList();
    }

    public Mono<List<Card>> getUniquePrintsByName(String name) {
        final Supplier<Mono<List<Card>>> supplier = () -> scryfallClient.getCardByName(name)
                .filter(card -> StringUtils.hasText(card.getUniquePrintsUri()))
                .flatMap(card -> scryfallClient.getUniquePrints(card.getUniquePrintsUri()))
                .filter(response -> !response.getData().isEmpty())
                .doOnNext(response -> log.info("Retrieved {} unique prints for {}", response.getTotalCards(), response.getData().get(0).getName()))
                .map(ListResponse::getData);

        return cardCacheService.getUniquePrintsByName(name, supplier);
    }
}
