(ns master-djinn.portal.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [master-djinn.util.core :refer [json->map map->json]]
            [master-djinn.util.types.core :refer [load-config uuid]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as id]
            [master-djinn.util.crypto :as crypt]
            [master-djinn.util.types.core :as types]
            [neo4j-clj.core :as neo4j])
  (:import java.util.Base64))

;; TODO rename "integrations" or somethig more general

(defonce oauth-providers {
  :Spotify {
    :id                 "Spotify"
    :auth-uri           "https://accounts.spotify.com/authorize"
    :token-uri          "https://accounts.spotify.com/api/token"
    :api-uri            "https://api.spotify.com/v1"
    :client-id          (:spotify-client-id (load-config))
    :client-secret      (:spotify-client-secret (load-config))
    ;; :scope           SEE FRONTEND
    :user-info-parser   #(-> % :body json->map :id)
    :user-info-uri      "https://api.spotify.com/v1/me"}
  :Github {
    :id                 "Github"
    :auth-uri           "https://github.com/login/oauth/authorize"
    :token-uri          "https://github.com/login/oauth/access_token"
    ;; :api-uri            "https://api.github.com"
    :graphql-uri            "https://api.github.com/graphql"
    :client-id          (:github-client-id (load-config))
    :client-secret      (:github-client-secret (load-config))
    ;; :scope           SEE FRONTEND
    :user-info-parser   #(-> % :body json->map :login)
    :user-info-uri      "https://api.github.com/user"} ; https://docs.github.com/en/rest/users/users?apiVersion=2022-11-28
})

(defn get-redirect-uri [provider]
  (str "https://"
        (or (:api-domain (load-config)) "scryer.jinni.health")
        "/oauth/callback"
        "?provider=" provider))

(defn base64-encode [to-encode]
  (String. (.encode (Base64/getEncoder) (.getBytes to-encode))))

(defn get-provider-auth-token
  [provider]
  (str "Basic " (base64-encode (str
    (:client-id ((keyword provider) oauth-providers)) ":"
    (:client-secret ((keyword provider) oauth-providers))))))

(defn get-oauth-login-request-config
  "@DEV: :body must be stringified before sent in request.
  We return as a map so can easily add new data to base config.

  returns - clj-http request map
  "
  [provider config]
  {:accept :json :async? false ;; TODO bottleneck but not important with minimal users
  :headers  {"Authorization" (get-provider-auth-token provider)
              "Content-Type" "application/x-www-form-urlencoded"}})

(defn kebab->capital [s] ;; not needed anymore since i moved everything to capital case on back+frontend
  (->> (str/split s #"[ -]")
       (map str/capitalize)
       (str/join "")))

(defn decode-oauth-state
  "takes parameter generated by jinni client, extracts player id and verifies that player signed oauth request
  state has form `{address}.{nonce}.{signature('{address}.{provider}.{nonce}')}`"
  [provider state]
  (println "portal:decode-oauth-state:parts" provider (clojure.string/split state #"\."))
  (let [[pid nonce sig] (clojure.string/split state #"\.")
        signer (crypt/ecrecover sig (str pid"."provider"."nonce))]
        ;; (println "portal:decode-oauth-state:signer" pid signer)
        (if (= pid signer) pid nil)))


;;; Step #1 & #2 - Provider response handling
(defn request-access-token
  "Part #2 of OAuth2 flow

  1. use code to verify user authorization in our app
  2. receive access_token and refresh_token back from

  OAuth provider returns - { :body { :scope :access_token :refresh_token :expires_in }}

  returns - pedestal response map
  "
  [provider oauth-config code]
  (println "requesting " provider " server: " (:token-uri oauth-config) "to callback to : " (get-redirect-uri provider) )
  (let [base-config (get-oauth-login-request-config provider oauth-config)
        request-config (assoc base-config :form-params
                          {:redirect_uri (get-redirect-uri provider)
                            :grant_type "authorization_code"
                            :code code
                            :client_id (:client-id oauth-config)
                            ;; only needed on - github, 
                            :client_secret (:client-secret oauth-config)
                            })
        response (client/post (:token-uri oauth-config) request-config)]
      ;; (println "oauth token response" (json/read-str (:body response) :key-fn keyword))
      (if (get-in response [:body]) (json->map (:body response)) nil)))

(defn oauth-callback-handler
  "Part #1 of OAuth2 flow

  Hey its me. Yet another web2 authentication endpoint coded by hand.
  I'm sure you're wondering how we got here.
  1. player equip()s item requiring oauth
  2. player navigates to oauth page o phone
  3. player authorizes us inside 3rd party app
  4. player redirected back into our app
  5. spotify calls back to this endpoint with code
  6. we send code to spotify and request access_token
  7. 

  TODO still need to figure out how to associate access/refresh tokens with a specific player/identity
  Make call in our app to get a `state` var and map it to an avatar+identity and return to app to pass into oauth callback params
  TODO Should we also pass back the redirect url with all the scopes, etc. we want to move that from frontend??
  Want game to be independent from backend as much as possible so can decentralize frontends or have people build new games but thats in conflict with wanting to centralize data for analysis and social features

  Following guide here. implementation might need to change based on other providers setup but i think this is OAuth2 standard 
  https://developer.spotify.com/documentation/web-api/tutorials/code-flow

  returns - pedestal response map
  "
  [request]
  (let [qs (get-in request [:query-params])
        {:keys [provider code error state]} qs
        aaaa (println )
        config ((keyword provider) oauth-providers)
        pid (decode-oauth-state provider state)]
    (println "OAUTH callback" pid qs)
    (cond
      (nil? pid)                          {:status 401 :body "unverified player"}
      (clojure.string/blank? provider)    {:status 400 :body "must include oauth provider"}
      (nil? config)                       {:status 400 :body "oauth provider not supported"}
      (= error "access_denied")           {:status 400 :body "user rejected access"}
      (not (clojure.string/blank? error)) {:status 400 :body error} ; catch all error last
      (clojure.string/blank? code)        {:status 400 :body "no code provided for oauth flow"}
      ;; ensure player is registered and identity exists before setting tokens
      ;; set-tokens query wont duplicate avatar/identity anyway.
      ;; Last check to prevent unneccessary db calls
      (nil? (db/call id/init-player-identity {:pid pid :provider provider}))
                                          {:status 401 :body "player not registered"}
      :else                               (try (let
        [response (request-access-token provider config code)]
        (db/call id/set-identity-credentials {
          :pid pid
          :provider provider
          :access_token (:access_token response)
          :refresh_token (:refresh_token response)
          ;; TODO spotify returns string w/ space separated. idk if that is spec of not 
          ;; :scope (str/split (:scope body) #" ")
        })
        {:status 301
            ;; redirect with deeplink and verifcation
            ;; TODO universal links instead of direct deep links https://docs.expo.dev/guides/deep-linking/
            ;; https://stackoverflow.com/questions/77214219/expo-linking-with-custom-scheme-does-not-redirect-back-to-app
            :headers {"Location" (str "jinni-health://inventory/" provider "?state=" state)}
            :body (map->json {
              :id pid
              :provider provider
              :state state
              :msg (str provider "Item Successfully Equipped!")}
        )})
      (catch Exception e
        (println "portal:oauth-callback:request-token:ERROR on " provider " - " (ex-message e))
        {:status 400 :body "Error on OAuth provider issuing access token"})
    ))
))

(defn refresh-access-token
  "Part #3 of OAuth2 flow
  After access token expires, return a refresh token to get a new access token
  
  returns - pedestal response map
  "
  [player-id provider]
  (println "refreshing access token for "  player-id " on " provider)
  (let [id (id/getid player-id provider)
        provider-config ((keyword provider) oauth-providers)
        base-config (get-oauth-login-request-config provider provider-config)
        request-config (assoc base-config :form-params {
                        :grant_type "refresh_token"
                        :refresh_token (:refresh_token id)
                        :client_id (:client-id provider-config)})]
    (try (let [response (client/post (:token-uri provider-config) request-config)
              res (json->map (:body response))
              log (clojure.pprint/pprint res)
              new_token (:access_token res)
              refresh_token? (:refresh_token res)]
      (println "refresh token response " res (:error res) (not new_token))
      (if (or (:error res) (not new_token))
        ;; (throw (Exception. "bad_refrsh_token"))
        (throw (ex-info "bad_refresh_token" {:status 401}))
        (do   (db/call id/set-identity-credentials {
            :pid player-id ;; TODO player-id when fixed in init-oauth-handler
            :provider provider
            :access_token new_token
            :refresh_token (or refresh_token? (:refresh_token id)) ;; so we dont overwrite with null
          })
        new_token)))
    (catch Exception err
      ;; TODO if status-code :401 then need to reauthenticate. 
      ;; ¿redirect to app inventory item with "?action=equip" to restart authentication process?
      (println (str "Error refreshing token on *" provider "*: ") (ex-message err) (ex-data err))
      (throw err) ;; make sure call errors out so any function relying on success of refresh errors out as well preventing infinite loops
        
    ))
))

(defn oauthed-request-config
  "For sending requests on behalf of a user to an OAuth2 server
  User must have completed oauth flow and have an :Identity in db already for service being called"
  [access-token]
  {:accept :json :async? false ;; TODO bottleneck but not important with minimal users
  :headers  {"Authorization" (str "Bearer " access-token)
              "Content-Type" "application/json"}})