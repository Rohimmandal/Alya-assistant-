package com.alya.assistant;

import android.service.voice.VoiceInteractionSession;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

final class AlyaVoiceInteractionSession extends VoiceInteractionSession {
    AlyaVoiceInteractionSession(android.content.Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle arguments, int showFlags) {
        super.onShow(arguments, showFlags);
        Intent launch = new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startVoiceActivity(launch);
        hide();
    }

    @Override
    public View onCreateContentView() {
        TextView message = new TextView(getContext());
        message.setText("Alya Assistant\nOpen Alya to continue.");
        message.setTextSize(18);
        message.setPadding(32, 32, 32, 32);
        return message;
    }
}