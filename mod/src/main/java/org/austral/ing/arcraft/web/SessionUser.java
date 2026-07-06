package org.austral.ing.arcraft.web;

import io.javalin.http.Context;

import java.io.Serializable;
import java.util.UUID;

/** The logged-in player, stored in the (in-memory) Jetty session. */
public record SessionUser(UUID id, String username, boolean admin) implements Serializable {

    static final String ATTR = "arcraft-user";

    public static SessionUser current(Context ctx) {
        return ctx.sessionAttribute(ATTR);
    }

    public static void login(Context ctx, SessionUser user) {
        // Fresh session id on login = session-fixation protection (changeSessionId() would
        // throw "No session" when the visitor arrives without one).
        var old = ctx.req().getSession(false);
        if (old != null) old.invalidate();
        ctx.req().getSession(true).setAttribute(ATTR, user);
    }

    public static void logout(Context ctx) {
        ctx.req().getSession().invalidate();
    }
}
