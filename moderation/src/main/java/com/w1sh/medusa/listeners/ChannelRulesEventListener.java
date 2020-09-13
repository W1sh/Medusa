package com.w1sh.medusa.listeners;

import com.w1sh.medusa.actions.ChannelRulesActivateAction;
import com.w1sh.medusa.actions.ChannelRulesDeactivateAction;
import com.w1sh.medusa.actions.ChannelRulesShowAction;
import com.w1sh.medusa.data.responses.MessageEnum;
import com.w1sh.medusa.events.ChannelRulesEvent;
import com.w1sh.medusa.services.MessageService;
import discord4j.core.object.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public final class ChannelRulesEventListener implements CustomEventListener<ChannelRulesEvent> {

    private final ChannelRulesShowAction channelRulesShowAction;
    private final ChannelRulesActivateAction channelRulesActivateAction;
    private final ChannelRulesDeactivateAction channelRulesDeactivateAction;
    private final MessageService messageService;

    @Override
    public Mono<Void> execute(ChannelRulesEvent event) {
        return applyAction(event).then();
    }

    private Mono<Message> applyAction(ChannelRulesEvent event) {
        if(event.getArguments().isEmpty()) return channelRulesShowAction.apply(event);
        if(event.getArguments().size() < 2) return messageService.send(event.getChannel(), MessageEnum.RULES_ERROR);

        RulesAction playlistAction = RulesAction.of(event.getArguments().get(1));
        switch (playlistAction) {
            case ON: return channelRulesActivateAction.apply(event);
            case OFF: return channelRulesDeactivateAction.apply(event);
            case SHOW: return channelRulesShowAction.apply(event);
            default: return messageService.send(event.getChannel(), MessageEnum.RULES_ERROR);
        }
    }

    private enum RulesAction {
        ON, OFF, SHOW, UNKNOWN;

        public static RulesAction of(String string){
            for (RulesAction value : values()) {
                if(value.name().equalsIgnoreCase(string)) return value;
            }
            return UNKNOWN;
        }
    }
}
