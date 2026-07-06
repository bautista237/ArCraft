package org.austral.ing.arcraft.web;

import io.javalin.http.Context;

/**
 * One-shot messages across a redirect (the old Spring {@code RedirectAttributes} flash):
 * stored in the session, moved into the model (as {@code error} / {@code success}) by
 * {@link BaseModel} on the next rendered page.
 */
public final class Flash {

    static final String ERROR = "flash-error";
    static final String SUCCESS = "flash-success";

    private Flash() {
    }

    public static void error(Context ctx, String message) {
        ctx.sessionAttribute(ERROR, message);
    }

    public static void success(Context ctx, String message) {
        ctx.sessionAttribute(SUCCESS, message);
    }

    /** Removes and returns the given flash attribute (or null). */
    static String pop(Context ctx, String key) {
        String v = ctx.sessionAttribute(key);
        if (v != null) ctx.sessionAttribute(key, null);
        return v;
    }
}
