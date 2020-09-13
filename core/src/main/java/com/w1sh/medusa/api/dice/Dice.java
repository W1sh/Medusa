package com.w1sh.medusa.api.dice;

import com.w1sh.medusa.data.Event;
import com.w1sh.medusa.data.responses.TextMessage;
import com.w1sh.medusa.dispatchers.ResponseDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class Dice {

    public static final String ROLL_ARGUMENT_DELIMITER = "-";

    private final Random random;
    private final ResponseDispatcher responseDispatcher;

    public Mono<Integer> roll(Integer min, Integer max){
        return Mono.just(random.nextInt(min + max + 1) + min);
    }

    public Mono<Boolean> validateRollArgument(Event event){
        return Mono.justOrEmpty(event)
                .map(limits -> limits.getArguments().get(0).split(ROLL_ARGUMENT_DELIMITER))
                .filter(limits -> limits.length == 2)
                .map(limits -> new int[]{Integer.parseInt(limits[0]), Integer.parseInt(limits[1])})
                .filter(limits -> limits[1] > limits[0])
                .onErrorResume(throwable -> Mono.fromRunnable(() -> log.error("Failed to parse arguments", throwable)))
                .hasElement()
                .flatMap(bool -> {
                    if(Boolean.FALSE.equals(bool)){
                        return createRollErrorMessage(event);
                    }else return Mono.empty();
                })
                .hasElement()
                .map(b -> !b);
    }

    private Mono<TextMessage> createRollErrorMessage(Event event){
        return event.getMessage().getChannel()
                .map(channel -> new TextMessage(channel,
                        ":x: Invalid argument received, the argument must be of type **X-Y** where **Y** must be greater than **X**",
                        false))
                .doOnNext(responseDispatcher::queue)
                .doAfterTerminate(responseDispatcher::flush);
    }
}
