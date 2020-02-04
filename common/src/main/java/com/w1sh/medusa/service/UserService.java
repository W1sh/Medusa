package com.w1sh.medusa.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.w1sh.medusa.data.User;
import com.w1sh.medusa.repos.UserRepository;
import discord4j.core.object.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.cache.CacheMono;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final Cache<Long, User> usersCache;

    @Value("${points.reward.amount}")
    private String rewardAmount;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.usersCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(Duration.ofHours(1))
                .recordStats()
                .build();
    }

    public Mono<User> save(User user){
        return userRepository.save(user)
                .doOnNext(u -> usersCache.put(u.getUserId(), u));
    }

    public Mono<User> findByUserId(Long userId) {
        return CacheMono.lookup(key -> Mono.justOrEmpty(usersCache.getIfPresent(key))
                .map(Signal::next), userId)
                .onCacheMissResume(() -> userRepository.findByUserId(String.valueOf(userId))
                        .defaultIfEmpty(new User(userId))
                        .subscribeOn(Schedulers.elastic()))
                .andWriteWith((key, signal) -> Mono.fromRunnable(
                        () -> Optional.ofNullable(signal.get())
                                .ifPresent(value -> usersCache.put(key, value))))
                .onErrorResume(throwable -> {
                    logger.error("Failed to user with id \"{}\"", userId, throwable);
                    return Mono.just(new User(userId));
                });
    }

    public Mono<Void> distributePoints(Member member) {
        return findByUserId(member.getId().asLong())
                .doOnNext(user -> user.setPoints(user.getPoints() + Integer.parseInt(rewardAmount)))
                .flatMap(this::save)
                .then();
    }

}