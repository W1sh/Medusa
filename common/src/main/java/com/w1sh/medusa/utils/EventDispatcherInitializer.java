package com.w1sh.medusa.utils;

import com.w1sh.medusa.data.events.Event;
import com.w1sh.medusa.data.events.EventFactory;
import com.w1sh.medusa.data.events.Registered;
import com.w1sh.medusa.dispatchers.MedusaEventDispatcher;
import com.w1sh.medusa.listeners.EventListener;
import com.w1sh.medusa.validators.Validator;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public final class EventDispatcherInitializer {

    private final MedusaEventDispatcher medusaEventDispatcher;
    private final Reflections reflections;
    private final EventFactory eventFactory;

    private final List<Validator> validators;
    private final List<EventListener<?>> listeners;
    private Set<Class<? extends Event>> events;

    @PostConstruct
    public void init(){
        events = findAllEvents();
    }

    public void setupDispatcher(final GatewayDiscordClient gateway){
        gateway.on(MessageCreateEvent.class)
                .filter(event -> event.getClass().equals(MessageCreateEvent.class) &&
                        event.getMember().isPresent() && event.getMember().map(user -> !user.isBot()).orElse(false))
                .flatMap(eventFactory::extractEvents)
                .filterWhen(ev -> Flux.fromIterable(validators)
                        .flatMap(validator -> validator.validate(ev))
                        .all(Boolean::booleanValue))
                .doOnSubscribe(ev -> log.info("Received new event of type <{}>", ev.getClass().getSimpleName()))
                .subscribe(medusaEventDispatcher::publish);
    }

    public void registerListeners() {
        listeners.forEach(medusaEventDispatcher::registerListener);
        log.info("Found and registered {} event listeners", listeners.size());
    }

    public void registerEvents() {
        var candidates = events.stream()
                .filter(event -> !Modifier.isAbstract(event.getModifiers()))
                .collect(Collectors.toList());
        for (Class<? extends Event> clazz : candidates) {
            Registered registered = clazz.getAnnotation(Registered.class);
            if(registered != null){
                eventFactory.registerEvent(registered.prefix(), clazz);
                log.info("Registering new event of type <{}>", clazz.getSimpleName());
            }
            if (!hasListenerRegistered(clazz)) {
                log.warn("Event of type <{}> has no listener registered!", clazz.getSimpleName());
            }
        }
        log.info("Found and registered {} event listeners", events.size());
    }

    private Set<Class<? extends Event>> findAllEvents(){
        return reflections.getSubTypesOf(Event.class);
    }

    private boolean hasListenerRegistered(final Class<? extends Event> clazz){
        return listeners.stream()
                .map(eventListener -> eventListener.getEventType().getSimpleName())
                .anyMatch(eventName -> eventName.equalsIgnoreCase(clazz.getSimpleName()));
    }
}
