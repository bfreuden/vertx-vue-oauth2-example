package io.vertx.example.web.oauth2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;

/*
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class Server extends AbstractVerticle {

    public static void main(String[] args) {
        Launcher.executeCommand("run", Server.class.getName());
    }


    @Override
    public void start(Promise<Void> startComplete) {
        OAuth2Options config = new OAuth2Options()
                .setSite("http://localhost:8001/realms/{realm}")
                .setTenant("demo-realm")
                .setClientId("demo-app")
                .setClientSecret("zoNet42HbPa9E7wo09QYXHyHeA8Y7z3C");
        KeycloakAuth.discover(vertx, config)
                .compose(this::startServer)
                .onSuccess(r -> {
                    System.out.println("Server running on http://localhost:8000/");
                })
                .onComplete(startComplete);
    }


    public Future<Void> startServer(OAuth2Auth authProvider) {
        // In order to use a template we first need to create an engine
        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create(vertx);

        // To simplify the development of the web components we use a Router to route all HTTP requests
        // to organize our code in a reusable way.
        final Router router = Router.router(vertx);
        // We need cookies and sessions
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        // Simple auth service which uses a GitHub to authenticate the user
        // we now protect the resource under the path "/protected"
        router.route("/protected").handler(OAuth2AuthHandler.create(vertx, authProvider, "http://localhost:8000/oauth2/callback")
                // we now configure the oauth2 handler, it will setup the callback handler
                // as expected by your oauth2 provider.
                .setupCallback(router.route("/oauth2/callback"))
                // for this resource we require that users have the authority to retrieve the user emails
                .withScope("openid")
        );
        // Entry point to the application, this will render a custom template.
        router.get("/").handler(ctx -> {
            // we pass the client id to the template
            JsonObject data = new JsonObject();
            // and now delegate to the engine to render it.
            engine.render(data, "io/vertx/example/web/oauth2/views/index.hbs", res -> {
                if (res.succeeded()) {
                    ctx.response()
                            .putHeader("Content-Type", "text/html")
                            .end(res.result());
                } else {
                    ctx.fail(res.cause());
                }
            });
        });
        // The protected resource
        router.get("/protected").handler(ctx -> {
            User user = ctx.user();
            // retrieve the user profile, this is a common feature but not from the official OAuth2 spec
            authProvider.userInfo(user, res -> {
                if (res.failed()) {
                    // request didn't succeed because the token was revoked so we
                    // invalidate the token stored in the session and render the
                    // index page so that the user can start the OAuth flow again
                    ctx.session().destroy();
                    ctx.fail(res.cause());
                } else {
                    // the request succeeded, so we use the API to fetch the user's emails
                    final JsonObject userInfo = res.result();

                    JsonObject data = new JsonObject()
                            .put("userInfo", userInfo);
                    // and now delegate to the engine to render it.
                    engine.render(data, "io/vertx/example/web/oauth2/views/advanced.hbs", res3 -> {
                        if (res3.succeeded()) {
                            ctx.response()
                                    .putHeader("Content-Type", "text/html")
                                    .end(res3.result());
                        } else {
                            ctx.fail(res3.cause());
                        }
                    });
                }
            });
        });

        return vertx.createHttpServer().requestHandler(router).listen(8000).map((Void)null);
    }
}