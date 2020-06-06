package com.w1sh.medusa.services;

import com.w1sh.medusa.data.GuildUser;
import com.w1sh.medusa.mappers.Member2GuildUserMapper;
import com.w1sh.medusa.repos.GuildUserRepository;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.presence.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
@Slf4j
public class GuildUserService {

    private final GuildUserRepository repository;
    private final UserService userService;
    private final MemoryCache<String, List<GuildUser>> cache;
    private final Member2GuildUserMapper member2GuildUserMapper;

    @Value("${points.reward.amount}")
    private String rewardAmount;

    public GuildUserService(GuildUserRepository repository, UserService userService,
                            Member2GuildUserMapper member2GuildUserMapper) {
        this.repository = repository;
        this.userService = userService;
        this.member2GuildUserMapper = member2GuildUserMapper;
        this.cache = new MemoryCacheBuilder<String, List<GuildUser>>()
                .maximumSize(10000)
                .expireAfterAccess(Duration.ofHours(6))
                .defaultFetch(key -> repository.findAllByGuildId(key)
                        .collectList()
                        .filter(Predicate.not(List::isEmpty)))
                .build();
    }

    @Transactional
    public Mono<GuildUser> save(GuildUser guildUser){
        return fetchUserByUserId(guildUser)
                .flatMap(repository::save)
                .onErrorResume(t -> Mono.fromRunnable(() -> log.error("Failed to save guild user with id \"{}\"", guildUser.getId(), t)))
                .doOnNext(this::cache);
    }

    public Mono<GuildUser> findByUserIdAndGuildId(String userId, String guildId) {
        return fetchAllGuildUsersInGuild(guildId)
                .filterWhen(guildUser -> userService.findByUserId(userId)
                        .filter(user -> user.getId().equals(guildUser.getUser().getId()))
                        .doOnNext(guildUser::setUser)
                        .hasElement())
                .next()
                .switchIfEmpty(userService.findByUserId(userId)
                        .map(user -> new GuildUser(user, guildId)));
    }

    public Mono<Long> distributePointsInGuild(Guild guild) {
        return guild.getMembers()
                .filterWhen(this::isEligible)
                .map(member2GuildUserMapper::map)
                .flatMap(guildUser -> findByUserIdAndGuildId(guildUser.getUser().getUserId(), guildUser.getGuildId()))
                .doOnNext(u -> u.setPoints(u.getPoints() + Integer.parseInt(rewardAmount)))
                .concatMap(this::save)
                .count();
    }

    public Flux<GuildUser> findTop5PointsInGuild(String guildId){
        return repository.findAllByGuildIdOrderByPoints(guildId)
                .flatMap(this::fetchUserById)
                .collectList()
                .doOnNext(list -> cache.put(guildId, list))
                .flatMapIterable(Function.identity())
                .take(5);
    }

    private Mono<GuildUser> cache(GuildUser user) {
        return cache.get(user.getGuildId())
                .defaultIfEmpty(new ArrayList<>())
                .doOnNext(users -> {
                    users.remove(user);
                    users.add(user);
                    cache.put(user.getGuildId(), users);
                })
                .then(Mono.just(user));
    }

    private Mono<GuildUser> fetchUserById(GuildUser guildUser){
        if(guildUser.getUser().getUserId() != null) return Mono.just(guildUser);
        return userService.findById(guildUser.getUser().getId())
                .doOnNext(guildUser::setUser)
                .then(Mono.just(guildUser));
    }

    private Mono<GuildUser> fetchUserByUserId(GuildUser guildUser) {
        if(guildUser.getUser().getId() != null) return Mono.just(guildUser);
        return userService.findByUserId(guildUser.getUser().getUserId())
                .doOnNext(guildUser::setUser)
                .then(Mono.just(guildUser));
    }

    private Flux<GuildUser> fetchAllGuildUsersInGuild(String guildId) {
        return cache.get(guildId)
                .onErrorResume(t -> Mono.fromRunnable(() -> log.error("Failed to retrieve all guild users in guild with guild id \"{}\"", guildId, t)))
                .flatMapIterable(Function.identity());
    }

    private Mono<Boolean> isEligible(Member member) {
        return Mono.just(member)
                .filter(m -> !m.isBot())
                .flatMap(Member::getPresence)
                .map(Presence::getStatus)
                .filter(status -> status.equals(Status.ONLINE) || status.equals(Status.IDLE)
                        || status.equals(Status.DO_NOT_DISTURB))
                .hasElement();
    }
}
