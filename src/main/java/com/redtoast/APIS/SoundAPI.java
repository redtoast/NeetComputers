package com.redtoast.APIS;

import com.redtoast.blocks.Generics.SpeakerBlockEntity;
import com.redtoast.simulation.base.API;

public class SoundAPI implements API {
    SpeakerBlockEntity speakerBlockEntity;

    public SoundAPI(SpeakerBlockEntity speakerBlockEntity) {
        this.speakerBlockEntity = speakerBlockEntity;
    }

    @Override
    public String getLabel() {
        return "sound";
    }
}
