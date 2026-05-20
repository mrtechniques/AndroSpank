package com.spankapp.android.modes;

/**
 * All available spank modes, mirroring SpankApp/taigrr/spank's mode system.
 * Each mode has a display name, description, emoji icon, and intensity behaviour.
 */
public enum SpankMode {

    PAIN(
        "Pain",
        "Ow! Classic pain reactions — your device yells back.",
        "😖",
        false
    ),
    SEXY(
        "Sexy",
        "Escalating responses. The more you spank, the more intense it gets.",
        "💋",
        true   // escalating intensity
    ),
    HALO(
        "Halo",
        "Random Halo death sounds on every hit.",
        "🎮",
        false
    ),
    FART(
        "Fart",
        "Toilet humor mode. Classic.",
        "💨",
        false
    ),
    CUSTOM(
        "Custom",
        "Use your own audio file from storage.",
        "📁",
        false
    );

    public final String displayName;
    public final String description;
    public final String emoji;
    public final boolean isEscalating;

    SpankMode(String displayName, String description, String emoji, boolean isEscalating) {
        this.displayName = displayName;
        this.description = description;
        this.emoji = emoji;
        this.isEscalating = isEscalating;
    }
}
