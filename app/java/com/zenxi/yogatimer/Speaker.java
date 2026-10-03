package com.zenxi.yogatimer;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/** 系统语音播报（语音报时）。 */
public final class Speaker {

    private static final String TAG = "ZenXi/TTS";
    private static final Speaker INSTANCE = new Speaker();

    public static Speaker get() {
        return INSTANCE;
    }

    private TextToSpeech tts;
    private boolean ready;
    private boolean failed;
    private String pending;

    private Speaker() {
    }

    private void ensure(Context c) {
        if (tts != null || failed) {
            return;
        }
        try {
            tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        try {
                            int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                            if (r == TextToSpeech.LANG_MISSING_DATA
                                    || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts.setLanguage(Locale.getDefault());
                            }
                            tts.setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build());
                            tts.setSpeechRate(0.98f);
                            tts.setPitch(1.0f);
                        } catch (Throwable t) {
                            Log.w(TAG, "tts config", t);
                        }
                        ready = true;
                        if (pending != null) {
                            String s = pending;
                            pending = null;
                            say(s);
                        }
                    } else {
                        failed = true;
                    }
                }
            });
        } catch (Throwable t) {
            failed = true;
            Log.w(TAG, "tts init", t);
        }
    }

    public void speak(Context c, String text) {
        ensure(c);
        if (ready) {
            say(text);
        } else if (!failed) {
            pending = text;
        }
    }

    private void say(String text) {
        try {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "zenxi-" + System.nanoTime());
        } catch (Throwable t) {
            Log.w(TAG, "speak", t);
        }
    }

    public void stop() {
        try {
            if (tts != null) {
                tts.stop();
            }
        } catch (Throwable ignored) {
        }
    }
}
