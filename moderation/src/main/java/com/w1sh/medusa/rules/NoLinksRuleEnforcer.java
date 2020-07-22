package com.w1sh.medusa.rules;

import com.w1sh.medusa.data.responses.Response;
import com.w1sh.medusa.data.responses.TextMessage;
import com.w1sh.medusa.services.WarningService;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public final class NoLinksRuleEnforcer implements RuleEnforcer<String>{

    private final Pattern p = Pattern.compile("^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$");
    private final WarningService warningService;

    @Override
    public Mono<Boolean> validate(String value) {
        return Mono.justOrEmpty(value)
                .flatMapIterable(m -> Arrays.asList(m.split(" ")))
                .filter(s -> p.matcher(s).find())
                .hasElements();
    }

    @Override
    public Mono<Response> enforce(MessageCreateEvent event) {
        String channelId = event.getMessage().getChannelId().asString();
        String userId = event.getMember().map(member -> member.getId().asString()).orElse("");

        Mono<Response> warningMessage = event.getMessage().getChannel()
                .map(chan -> new TextMessage(chan, String.format("**%s**, no links are allowed on this channel",
                        event.getMember().map(Member::getDisplayName).orElse("")), false));

        return warningService.addWarning(userId, channelId)
                .doOnNext(ignored -> event.getMessage().delete().subscribe())
                .then(warningMessage);
    }
}