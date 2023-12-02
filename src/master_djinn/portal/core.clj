(ns master-djinn.portal.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [master-djinn.util.types.core :refer [load-config uuid json->map]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as id]
            [master-djinn.util.crypto :as crypt]
            [master-djinn.util.types.core :as types]
            [neo4j-clj.core :as neo4j])
  (:import java.util.Base64))

;; TODO rename "integrations" or somethig more general

(defonce oauth-providers {
  :spotify {
    :id "spotify"
    :auth-uri "https://accounts.spotify.com/authorize"
    :token-uri "https://accounts.spotify.com/api/token"
    :api-uri "https://api.spotify.com/v1"
    :client-id (:spotify-client-id (load-config))
    :client-secret (:spotify-client-secret (load-config))
    ;; :scope SEE FRONTEND
    :user-info-parser #(-> % :body json->map :id)
    :user-info-uri      "https://api.spotify.com/v1/me"}
  :github {
    :id "github"
    :auth-uri           "https://github.com/login/oauth/authorize"
    :token-uri          "https://github.com/login/oauth/access_token"
    :client-id          (:github-client-id (load-config))
    :client-secret      (:github-client-secret (load-config))
    ;; :scope             SEE FRONTEND
    :user-info-parser #(-> % :body json->map :login)
    :user-info-uri      "https://api.github.com/user"} ; https://docs.github.com/en/rest/users/users?apiVersion=2022-11-28
})

(defn get-redirect-uri [provider]
  (str "https://"
        (or (:api-domain (load-config)) "scry.cryptonative.ai")
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

;;; Step #0 - Initiating provider authentication & authorization

(defn init-oauth-identity
  "Creates an identity on players avatar for the oauth provider.
  returns - nonce string or Exception"
  [pid provider]
  (let [nonce (str (java.util.UUID/randomUUID))]
      (println "generating player oauth: " pid provider nonce)
      (neo4j/with-transaction db/connection tx
        (->> {:pid pid
            :provider (kebab->capital provider)
            :nonce nonce}
          (id/init-player-identity tx)
          doall ;; this returns null list if player does not exist. Does not throw/revert
          ((fn [result] (if (empty? result)
            (throw (Exception. "Player does not exist in realm of jinn"))
            ;; only return nonce. dont want to leak data by returning identity node id
            nonce)))))))

(defn oauth-init-handler
  "Part #0 of OAuth2 flow

  1. player requests nonce to increase security and identify login to player in oauth callback
  2. create Identity in ID DB with nonce and attach to player Avatar
  3. return nonce to player to complete oauth flow

  returns - pedestal response map
  "
  [request]
    (let [qs (get-in request [:query-params])
          {:keys [provider player_id]} qs
          config ((keyword provider) oauth-providers)]
          ;; TODO get player_id from request signature not query params
          ;; check if player_id exists in ID DB. return 401 if not
   (cond
      (clojure.string/blank? provider)    {:status 400 :body "must include oauth provider"}
      (nil? config)                       {:status 400 :body "oauth provider not supported"}
      :else (try
        {:status 200 :body (json/write-str {:state (init-oauth-identity player_id provider)})}
        (catch Exception e {:status 400 :body (json/write-str {:error (ex-message e)})}))
    )))

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
                            ;; only needed on - github, 
                            :client_id (:client-id oauth-config)
                            :client_secret (:client-secret oauth-config)
                            })
        response (client/post (:token-uri oauth-config) request-config)]
      ;; (println "oauth token response" (json/read-str (:body response) :key-fn keyword))
    (if (get-in response [:body])
      (let [body (json->map (:body response))
            ;; ensure identity exists for player before setting tokens
            ;; cypher query wont duplicate if id already exists 
            id (db/call id/init-player-identity {
                :pid crypt/TEST_SIGNER
                :provider provider 
                :label (clojure.string/capitalize provider)})
            creds (:id (db/call id/set-identity-credentials {
              :pid crypt/TEST_SIGNER ;; TODO pass in pid as func param after using OAuth state param to verify user in handler
              :provider provider
              :access_token (:access_token body)
              :refresh_token (:refresh_token body)
              ;; TODO spotify returns string w/ space separated. idk if that is spec of not 
              ;; TODO necessary? If so move to another query separate from token updates
              ;; :scope (str/split (:scope body) #" ")
            }))]
          
          (println "oauth token response" id body)
          ;; (println "oauth token" (str/split (:scope body) #" ") (:access_token body))
          {:status 301
            ;; TODO redirect not working. AI generated mf
            :headers {"Location" (str "jinnihealth://inventory/" provider)} ;; redirect with deeplink 
            :body (json/write-str {
              :id creds
              :msg (str provider "Item Successfully Equipped!")}
        )})
        #((println "ERROR on oauth token response" response)
          {:status 400 :body "Error on OAuth provider issuing access token"})
    )
  ))


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
        config ((keyword provider) oauth-providers)]
        ;; TODO match state->identity-> player_id to signed request player id
                
    (println "OAUTH callback" qs)
    (cond
      (clojure.string/blank? provider)    {:status 400 :body "must include oauth provider"}
      (nil? config)                       {:status 400 :body "oauth provider not supported"}
      (= error "access_denied")           {:status 400 :body "user rejected access"}
      (not (clojure.string/blank? error)) {:status 400 :body error} ; catch all error last
      (clojure.string/blank? code)        {:status 400 :body "no code provided for oauth flow"}
      :else                               (request-access-token provider config code))))

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
        request-config (assoc base-config :form-params { ;; (json/write-str ?
                        :grant_type "refresh_token"
                        :refresh_token (:refresh_token id)
                        :client_id (:client-id provider-config)})]
    (try (let [response (client/post (:token-uri provider-config) request-config)
              new_token (:access_token (json->map (:body response)))]
      ;; (println "refresh token response " (json/read-str (:body response) :key-fn keyword))
      (db/call id/set-identity-credentials {
        :pid player-id ;; TODO player-id when fixed in init-oauth-handler
        :provider provider
        :access_token new_token
        :refresh_token (:refresh_token id) ;; so we dont overwrite with null
      })
    new_token)
    (catch Exception err
        (println (str "Error refreshing token on *" provider "*: ") (ex-message err) (ex-data err))
    ))
))

(defn oauthed-request-config
  "For sending requests on behalf of a user to an OAuth2 server
  User must have completed oauth flow and have:Identity in db already"
  [access-token]
  {:accept :json :async? false ;; TODO bottleneck but not important with minimal users
  :headers  {"Authorization" (str "Bearer " access-token)
              "Content-Type" "application/json"}})

;; TODO OAuth providers return a new access token on each request
;; Create a helper function that updates :Identity in DB with new access token



;;; OAuth2 best practices
;;; https://docs.cloud.coinbase.com/sign-in-with-coinbase/docs/sign-in-with-coinbase-security
;;; included a state GET parameter during the OAuth2 authorization process. Verifying that this variable matches upon receipt of an authorization code 
;;; validates our SSL certificate when it connects over https

;; stealing code from:
;; https://github.com/cemerick/friend
;; https://github.com/propan/geheimtur
;; https://github.com/yetanalytics/pedestal-oidc

