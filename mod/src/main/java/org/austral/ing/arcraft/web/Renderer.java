package org.austral.ing.arcraft.web;

import io.javalin.http.Context;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf rendering for the embedded dashboard. Uses a real servlet {@link WebContext}
 * (built from Jetty's request/response via {@link JakartaServletWebApplication}), so the
 * templates' {@code @{...}} link expressions, {@code ${param.x}} and {@code ${session.x}}
 * all work exactly like they did under Spring — the templates carry over unchanged.
 */
public final class Renderer {

    private static final TemplateEngine ENGINE = buildEngine();
    private static volatile JakartaServletWebApplication webApp;

    private Renderer() {
    }

    private static TemplateEngine buildEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(Renderer.class.getClassLoader());
        resolver.setPrefix("web/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** Renders {@code template} (e.g. "dashboard") with the base model + {@code model}. */
    public static void render(Context ctx, String template, Map<String, Object> model) {
        if (webApp == null) {
            synchronized (Renderer.class) {
                if (webApp == null) {
                    webApp = JakartaServletWebApplication.buildApplication(ctx.req().getServletContext());
                }
            }
        }
        Map<String, Object> full = new HashMap<>(BaseModel.build(ctx));
        full.putAll(model);
        IServletWebExchange exchange = webApp.buildExchange(ctx.req(), ctx.res());
        WebContext wc = new WebContext(exchange, Locale.ENGLISH, full);
        ctx.contentType("text/html; charset=utf-8");
        ctx.result(ENGINE.process(template, wc));
    }

    /** Renders a template to a String with a plain (non-web) context — used for emails. */
    public static String renderToString(String template, Map<String, Object> model) {
        org.thymeleaf.context.Context c = new org.thymeleaf.context.Context(Locale.ENGLISH, model);
        return ENGINE.process(template, c);
    }
}
