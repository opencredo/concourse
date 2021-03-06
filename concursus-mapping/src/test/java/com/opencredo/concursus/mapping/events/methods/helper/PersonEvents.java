package com.opencredo.concursus.mapping.events.methods.helper;

import com.opencredo.concursus.domain.time.StreamTimestamp;
import com.opencredo.concursus.mapping.annotations.HandlesEventsFor;
import com.opencredo.concursus.mapping.annotations.Initial;
import com.opencredo.concursus.mapping.annotations.Name;

@HandlesEventsFor("person")
public interface PersonEvents {

    @Initial
    @Name("created")
    void createdV1(StreamTimestamp timestamp, String aggregateId, String name);

    @Initial
    @Name(value="created", version="2")
    void createdV2(StreamTimestamp timestamp, String aggregateId, String name, int age);

    void nameUpdated(StreamTimestamp timestamp, String aggregateId, @Name("updatedName") String newName);
}
