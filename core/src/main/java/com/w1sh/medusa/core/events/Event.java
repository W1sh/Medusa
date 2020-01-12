package com.w1sh.medusa.core.events;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.util.Permission;
import discord4j.core.object.util.Snowflake;

import java.util.ArrayList;
import java.util.List;

public abstract class Event extends MessageCreateEvent {

    private Integer numAllowedArguments;

    private List<Permission> permissions;

    public Event(MessageCreateEvent event){
        super(event.getClient(), event.getMessage(), event.getGuildId().map(Snowflake::asLong).orElse(null),
                event.getMember().orElse(null));
        this.permissions = new ArrayList<>();
        this.permissions.add(Permission.SEND_MESSAGES);
    }

    public Event(MessageCreateEvent event, Integer numAllowedArguments) {
        this(event);
        this.numAllowedArguments = numAllowedArguments;
    }

    public Event(MessageCreateEvent event, List<Permission> permissions){
        this(event);
        this.permissions.addAll(permissions);
    }

    public Integer getNumAllowedArguments() {
        return numAllowedArguments;
    }

    public void setNumAllowedArguments(Integer numAllowedArguments) {
        this.numAllowedArguments = numAllowedArguments;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

}
