package br.com.luma.ai;

import java.util.Objects;

/** A request to the companion controller, never executable code. */
public record Intent(Action action, String reply) {
    public Intent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reply, "reply");
    }
}
