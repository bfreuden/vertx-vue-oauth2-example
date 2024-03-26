package org.bfreuden;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
public class Server extends AbstractVerticle {

    private OAuth2Auth oAuth2Auth;

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

    public Future<Void> startServer(OAuth2Auth oAuth2Auth) {
        this.oAuth2Auth = oAuth2Auth;
        final Router router = Router.router(vertx);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setSessionCookieName("demo-app"));

        final OAuth2AuthHandler oauth2Handler = OAuth2AuthHandler.create(vertx, oAuth2Auth, "http://localhost:8000/oauth2/callback")
                .setupCallback(router.route("/oauth2/callback"))
                .withScope("openid");

        // API protected with OAuth2
        router.route("/api/*")
                .handler(oauth2Handler);

        // API endpoint
        router.get("/api/account").handler(this::getAccount);

        // login protected with OAuth2
        router.get("/oauth2/login")
                .handler(oauth2Handler)
                .handler(this::login);

        // single logout
        router.get("/oauth2/keycloak-logout")
                .handler(this::keycloakLogout);

        // logout from the app only
        router.get("/oauth2/logout")
                .handler(this::logout);

        // serve single page application
        router.route("/").handler(ctx -> ctx.reroute("/index.html"));
        router.route().handler(StaticHandler.create("src/main/www"));

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8000)
                .map((Void) null);
    }

    private void getAccount(RoutingContext ctx) {
        ctx.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(new JsonObject()
                        .put("token", ctx.user().principal().getString("id_token"))
                        // we could get that from Keycloak but let's keep it simple...
                        .put("firstname", "demo")
                        .put("lastname", "user")
                        .put("email", "demo-user@nowhere.com")
                        .encodePrettily());
    }

    private void login(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(302)
                // redirect to account page (or whatever welcome page) using vue router
                .putHeader(HttpHeaders.LOCATION, "/#/account")
                .end();

    }

    private void logout(RoutingContext ctx) {
        clearSessionAndUser(ctx);
        ctx.response()
                .setStatusCode(302)
                // redirect to signin page using vue router
                .putHeader(HttpHeaders.LOCATION, "/#/signin")
                .end();
    }

    private void keycloakLogout(RoutingContext ctx) {
        String endSessionURL = oAuth2Auth.endSessionURL(ctx.user());
//        clearSessionAndUser(ctx);
        ctx.response()
                .setStatusCode(302)
                // redirect to signin page using vue router
                .putHeader(HttpHeaders.LOCATION, endSessionURL + "&post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8000%2F%23%2Fsignin")
                .end();
    }

    private static void clearSessionAndUser(RoutingContext ctx) {
        ctx.clearUser();
        Session session = ctx.session();
        if (session != null)
            session.destroy();
    }

}


//    private void getAccount(RoutingContext ctx) {
//        String idToken = ctx.user().principal().getString("id_token");
//        oAuth2Auth.authenticate(new TokenCredentials(idToken))
//                .map(this::toAccountResponse)
//                .onComplete(ar -> {
//                    JsonObject result = ar.succeeded() ? ar.result() : new JsonObject().put("error", ar.cause().getMessage());
//                    ctx.response()
//                            .setStatusCode(ar.succeeded() ? 200 : 500)
//                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
//                            .send(result.toBuffer());
//                });
//    }
//
//    private JsonObject toAccountResponse(User user) {
//
//    }
//
