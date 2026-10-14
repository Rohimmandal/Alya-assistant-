package com.alya.assistant;

import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public final class AlyaVoiceInteractionSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(android.os.Bundle arguments) {
        return new AlyaVoiceInteractionSession(this);
    }
}