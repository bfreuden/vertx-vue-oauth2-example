
# Start pre-configured Keycloak

```
cd keycloak
docker compose up -d
cd ..
```
Wait something like 10 seconds, or until `docker compose logs -f` is showing something like: 
```
2024-03-26 13:28:05,084 WARN  [org.keycloak.quarkus.runtime.KeycloakMain] (main) Running the server in development mode. DO NOT use this configuration in production.
```
Keycloak is running on port 8001

Keycloak admin user/password: admin/admin

Realm: demo-realm

Client: demo-client 

Demo user/password: demo-user/demo-user


# Start Vert.x backend
Tested with OpenJDK 11 and Maven 3.8.1

```
mvn compile exec:java
```

# Open the single page app

http://localhost:8000/

Login with user/password: demo-user/demo-user


# Problem I'm trying to solve

This is a single page application using axios to call the Vert.x API backend.

It is working like the vanilla Vert.x Oauth2 example: 
https://github.com/vert-x3/vertx-examples/tree/b6194db5b5e7eac3173f8c968c9ecac8e4a567f9/web-examples/src/main/java/io/vertx/example/web/oauth2

Except that HTML is not produced by the backend (with templates) but by Vue.js and it is calling the backend with axios.

The app has 2 "pages":
- `/login`  
- `/account`

The `/account` "page" is calling the API to get user data

```javascript
    async mounted() {
       try {
           const response = await api.get("/api/account")
           this.token = response.data.token
           this.email = response.data.email
       } catch (error) {
            this.error = "an error occurred while fetching your account from the server: " + error
       } finally {
            this.loading = false
       }
    }

```

The app is globally configured to go to the `/signin` page in case of HTTP 401 error:
```javascript
const vm = new Vue ({
    router,
    el: "#app",
    beforeMount() {
        const self = this;
        api.interceptors.response.use(function (response) {
            return response;
          }, function (error) {
            if (error.response && error.response.status === 401)
                self.$router.push({path: "/signin"});
            return Promise.reject(error);
          }
        );
    }
}).$mount("#app");
```

When no route is specified, the app will try to show the `/account` page:
```javascript
const routes = [
    { path: "/", redirect: { path: "/account" } },
    { path: "/signin", component: Signin },
    { path: "/account", component: Account }
];
```

So the idea is

1. you're landing on `/`
2. the vue router redirects you to the `/account` page
3. an API call is performed to get user data
4. you receive an HTTP 401 error
5. you're redirected to the `/signin` page


Problem is if you don't have this Vert.x modification:
https://github.com/bfreuden/vertx-vue-oauth2-example/commit/796c92fa18aa2162299d30bd6f0287e030d98b52

That's to say this...
```java
    case 302:
        ctx.response()
                .putHeader(HttpHeaders.LOCATION, payload)
                .setStatusCode(302)
                .end("Redirecting to " + payload + ".");
        return;
```
... becoming this:
```java
    case 302:
        if (!"XMLHttpRequest".equals(ctx.request().getHeader("X-Requested-With"))) {
            ctx.response()
                    .putHeader(HttpHeaders.LOCATION, payload)
                    .setStatusCode(302)
                    .end("Redirecting to " + payload + ".");
        } else {
            ctx.fail(401, exception);
        }
        return;
```

... then axios is not receiving a 401 error, it is receiving a generic `AxiosError: Network Error` because of the redirect.
So you can't distinguish between "I'm not authenticated" and "the server down". 
This seems to be by browser design and there is nothing you can do.

Since this kind of thing already exists in Vert.x codebase: https://github.com/bfreuden/vertx-vue-oauth2-example/blob/8d396813169ed50b0dfe8c66ee8552989e8abea5/src/main/java/io/vertx/ext/web/handler/impl/AuthenticationHandlerImpl.java#L132

```java
    case 401:
        if (!"XMLHttpRequest".equals(ctx.request().getHeader("X-Requested-With"))) { // <-- here
            setAuthenticateHeader(ctx);
        }
        ctx.fail(401, exception);
        return;
```

... then I'm wondering if my modification could make sense.

Spring seems to be doing something similar: https://github.com/candrews/spring-security/blob/09100daf0fd6cd3a89dded4c962191cff98bb031/config/src/test/java/org/springframework/security/config/annotation/web/configurers/oauth2/client/OAuth2LoginConfigurerTests.java#L391

```java
	@Test
	public void oauth2LoginWithOneClientConfiguredAndRequestXHRNotAuthenticatedThenDoesNotRedirectForAuthorization()
			throws Exception {
		loadConfig(OAuth2LoginConfig.class);
		String requestUri = "/";
		this.request = new MockHttpServletRequest("GET", requestUri);
		this.request.setServletPath(requestUri);
		this.request.addHeader("X-Requested-With", "XMLHttpRequest");
		this.springSecurityFilterChain.doFilter(this.request, this.response, this.filterChain);
		assertThat(this.response.getStatus()).isEqualTo(401); // <-- here
	}
```
