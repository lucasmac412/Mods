package br.com.luma.ai;

import java.util.concurrent.CompletableFuture;

/** Session-scoped conversation; callers apply results only on the game thread. */
public interface ConversationBrain extends AutoCloseable {
    CompletableFuture<Intent> respond(String text, String worldContext);
    void cancelPending();
    @Override void close();
}
