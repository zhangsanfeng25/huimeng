package com.huimeng.bookshop.core;

import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** An in-memory conversation associated with the current login. */
public final class AiConversation {
    public record Turn(boolean user, String text, JSONArray products, boolean failed) {}

    private static AiConversation current = new AiConversation();
    public static AiConversation current() { return current; }
    public static void clear() { current = new AiConversation(); }

    private final List<Turn> turns = new ArrayList<>();
    private final MutableLiveData<Integer> updates = new MutableLiveData<>(0);
    private int revision;
    private boolean waiting;
    private String error = "";
    private String draft = "";
    private boolean demo;

    public List<Turn> turns() { return Collections.unmodifiableList(turns); }
    public MutableLiveData<Integer> updates() { return updates; }
    public boolean waiting() { return waiting; }
    public String error() { return error; }
    public String draft() { return draft; }
    public void draft(String value) { draft = value; }
    public boolean demo() { return demo; }

    public void send(ApiClient api, String text) {
        if (this != current || waiting || text.isEmpty()) return;
        JSONObject body = new JSONObject();
        try {
            JSONArray history = new JSONArray();
            List<Turn> completed = new ArrayList<>();
            for (Turn turn : turns) if (!turn.failed()) completed.add(turn);
            for (int i = Math.max(0, completed.size() - 12); i < completed.size(); i++) {
                Turn turn = completed.get(i);
                history.put(new JSONObject().put("role", turn.user() ? "user" : "assistant")
                    .put("text", turn.text().substring(0, Math.min(2000, turn.text().length()))));
            }
            body.put("message", text).put("history", history);
        } catch (JSONException malformed) {
            error = "消息发送失败，请重试";
            notifyChange();
            return;
        }
        waiting = true;
        error = "";
        draft = "";
        turns.add(new Turn(true, text, null, false));
        notifyChange();
        api.chat(body, result -> {
            if (this != current) return;
            turns.add(new Turn(false, result.optString("text"), result.optJSONArray("products"), false));
            demo = "demo".equals(result.optString("mode"));
            waiting = false;
            notifyChange();
        }, message -> {
            if (this != current) return;
            turns.set(turns.size() - 1, new Turn(true, text, null, true));
            draft = text;
            error = message;
            waiting = false;
            notifyChange();
        });
    }

    private void notifyChange() { updates.setValue(++revision); }
}
