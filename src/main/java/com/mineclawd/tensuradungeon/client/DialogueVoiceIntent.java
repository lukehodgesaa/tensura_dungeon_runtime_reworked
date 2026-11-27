package com.mineclawd.tensuradungeon.client;

import java.util.Locale;

final class DialogueVoiceIntent {
    enum Pool {
        CALM("calm"),
        SOFT("soft"),
        BRIGHT("bright"),
        UNCERTAIN("uncertain"),
        INTENSE("intense"),
        NEUTRAL("neutral");

        private final String id;

        Pool(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    private DialogueVoiceIntent() {
    }

    static Pool fromText(String rawText) {
        String text = rawText == null ? "" : rawText.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Pool.NEUTRAL;
        }

        if (containsAny(text,
            "rage", "furious", "angry", "attack", "fight", "kill", "die", "enemy",
            "traitor", "idiot", "fool", "shut up", "leave me alone", "stop right now",
            "怒", "戦", "殺", "死", "敵", "危"
        )) {
            return Pool.INTENSE;
        }

        if (text.contains("?") || text.contains("？") || containsAny(text,
            "what", "why", "how", "where", "who", "which", "can you", "could you",
            "is it", "are you", "do you", "did you", "maybe", "perhaps", "not sure",
            "huh", "hmm", "何", "なぜ", "どう", "わか"
        )) {
            return Pool.UNCERTAIN;
        }

        if (containsAny(text,
            "sorry", "apolog", "sad", "grief", "lost", "miss you", "hurt", "pain",
            "rest", "heal", "relief", "safe", "phew", "thank you", "thanks",
            "ごめん", "悲", "痛", "安心"
        )) {
            return Pool.SOFT;
        }

        if (containsAny(text,
            "hello", "hi", "welcome", "great", "good", "yes", "glad", "happy",
            "success", "congrat", "excellent", "wonderful", "ready", "nice", "hooray",
            "こんにちは", "歓迎", "嬉"
        ) || text.contains("!")) {
            return Pool.BRIGHT;
        }

        if (containsAny(text,
            "prepare", "daily", "weekly", "price", "shop", "trade", "information",
            "about", "here", "please", "choose", "option", "return", "back", "begin",
            "ready when", "準備", "毎日", "週間", "選", "戻"
        ) || text.endsWith("...")) {
            return Pool.CALM;
        }

        return Pool.NEUTRAL;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
