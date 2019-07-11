package main;

import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.guild.MemberLeaveEvent;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import entity.entities.User;
import entity.repositories.impl.UserRepository;
import handlers.CommandHandler;
import handlers.DatabaseHandler;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import utils.Vault;

import java.util.Objects;

class DiscordBot {

    private final String token = Vault.fetch("discord_token");
    private final DiscordClient client = new DiscordClientBuilder(token).build();
    private final UserRepository userRepository = UserRepository.getInstance();

    public DiscordBot() {
        client.getEventDispatcher().on(ReadyEvent.class)
                .subscribe(ready -> {
                    // bad implementation
                    // should only be added to database after trying to betting
                    DatabaseHandler.initializeDatabase(client);
                    DatabaseHandler.initializeAutomaticPointIncrementation();
                    CommandHandler.setupCommands(client);
                    System.out.println("Logged in as " + ready.getSelf().getUsername());
                    System.out.println("Currently serving " + ready.getGuilds().size() + " servers");
                });

        client.getEventDispatcher().on(DisconnectEvent.class)
                //.doOnNext(disconnectEvent -> em.getTransaction().commit())
                .subscribe();

        client.getEventDispatcher().on(GuildCreateEvent.class)
                .map(event -> Tuples.of("guildId", event.getGuild().getId().asLong()))
                .filter(tuple -> !userRepository.isPresent(tuple))
                //.doOnNext(tuple -> )
                .subscribe();

        client.getEventDispatcher().on(GuildDeleteEvent.class)
                .filter(guildDeleteEvent -> !guildDeleteEvent.isUnavailable())
                .map(GuildDeleteEvent::getGuild)
                //.doOnNext(/*register guild*/)
                .subscribe();

        client.getEventDispatcher().on(MemberJoinEvent.class)
                .map(MemberJoinEvent::getMember)
                .map(User::new)
                .doOnNext(userRepository::persist)
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        client.getEventDispatcher().on(MemberLeaveEvent.class)
                .map(event -> {
                    if(event.getMember().isPresent()){
                        return Tuples.of(event.getGuildId().asLong(),
                                event.getMember().map(discord4j.core.object.entity.User::getId).get().asLong());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .doOnNext(tuple-> userRepository.delete(
                        Tuples.of("discordId", tuple.getT2()),
                        Tuples.of("guildId", tuple.getT1())))
                .onErrorResume(e -> Mono.empty())
                .subscribe();

        client.login().block();
    }
}
