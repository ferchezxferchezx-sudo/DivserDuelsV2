package dev.divsersmp.duels;

import java.util.UUID;

public class PartyInvite {
    private final UUID id = UUID.randomUUID();
    private final UUID partyId;
    private final UUID inviterId;
    private final UUID targetId;

    public PartyInvite(UUID partyId, UUID inviterId, UUID targetId) {
        this.partyId = partyId;
        this.inviterId = inviterId;
        this.targetId = targetId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public UUID getTargetId() {
        return targetId;
    }
}
