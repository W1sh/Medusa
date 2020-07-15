package com.w1sh.medusa.api.misc.events;

import com.w1sh.medusa.data.events.Event;
import com.w1sh.medusa.data.events.Type;
import discord4j.core.event.domain.message.MessageCreateEvent;

@Type(prefix = "status")
public final class StatusEvent extends Event {

    public StatusEvent(MessageCreateEvent event) {
        super(event);
    }
}
