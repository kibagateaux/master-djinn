(ns master-djinn.manifester.identity
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid]]
            [master-djinn.util.db.identity :as id]
            [master-djinn.util.crypto :as crypt]
            [neo4j-clj.core :as neo4j])
  (:import java.util.Base64))

(defonce MALIKS_MAJIK_CARD nil) ;; TODO

(defn activate-jinni
  [ctx args val]
  ;; TODO need to create player in activity and identity db
  ;; How to ensure idempotency + ACID across both DBs?
  ;; create revert queries for both DBs and use OR statement to revert one if other fails?
  (println "activate jinn arhs:" args)
  (let [
        ;; djinn (crypt/ecrecover (:majik_msg args) (:player_id args))
        djinn MALIKS_MAJIK_CARD
        pid (:signer args)
        ;; aaa (println "activate jinn:" djinn MALIKS_MAJIK_CARD pid)
        jid (uuid nil)]
        ;; TODO calc kin, archetype, tone for human + jinn bdays and add to Avatar model
    (cond
      ;;  TODO throw API errors. create resolver wrapper
      ;; TODO define in specs not code here
      ;; (nil? pid) (println "Player must give their majik to activation")
      ;; (not= (:player_id args) pid) (println "Signer !== Registrant")
      ;; (not= djinn MALIKS_MAJIK_CARD) (println "majik msg not from powerful enough djinn")
      
      ;; TODO query db to make ensure they dont have a jinn already. App sepcific logic that we want to remove so no DB constaint

      ;; default is succes route
      :else (neo4j/with-transaction id/identity-db tx
        (-> (id/create-player tx { :player {
          :id pid
          :uuid (avatar->uuid pid)
          :birthday (:birthday args)
        } :jinni {
          :id jid
          :uuid (avatar->uuid jid)
          ;; they are born now
          :birthday (-> (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC)
                       (.format java.time.format.DateTimeFormatter/ISO_INSTANT))
        }})
        doall
        first
        :jinni)))))

  
(defonce oauth-providers {
  :spotify {
    :auth-uri "https://accounts.spotify.com/authorize"
    :token-uri "https://accounts.spotify.com/api/token"
    :client-id (:spotify-client-id (load-config))
    :client-secret (:spotify-client-secret (load-config))
    ;; :client-id (or (System/getenv "spotify.client-id") "NO SPOTIFY ENV VARS")
    ;; :client-secret (or (System/getenv "spotify.client-secret") "NO SPOTIFY ENV VARS")
    ;; :scope              "user:email"
    ;; :user-info-parser #(-> % :body (parse-string true))
    :user-info-uri      "https://api.github.com/user"}
  :github {
    :auth-uri           "https://github.com/login/oauth/authorize"
    :token-uri          "https://github.com/login/oauth/access_token"
    :client-id          (:github-client-id (load-config))
    :client-secret      (:github-client-secret (load-config))
    ;; :scope              "user:email"
    ;; :user-info-parser #(-> % :body (parse-string true))
    :user-info-uri      "https://api.github.com/user"}
})

(defn get-redirect-uri [provider]
  (str (or (System/getenv "app.host") "https://e877-95-14-82-25.ngrok.io")
                            "/oauth/callback"
                            "?provider=" provider))

(defn base64-encode [to-encode]
  (String. (.encode (Base64/getEncoder) (.getBytes to-encode))))

(defn kebab->capital [s] ;; not needed anymore since i moved everything to capital case on back+frontend
  (->> (str/split s #"[ -]")
       (map str/capitalize)
       (str/join "")))
;; requests oauth oauth tokens (client/get "http://example.com/protected" {:oauth-token "secret-token"})

;;; Step #0 - Initiating provider authentication & authorization

(defn init-oauth-identity
  "Creates an identity on players avatar for the oauth provider.
  returns - nonce string or Exception"
  [pid provider]
  (let [nonce (str (java.util.UUID/randomUUID))]
      (println "generating player oauth: " pid provider nonce)
      (neo4j/with-transaction id/identity-db tx
        (->> {:pid pid
            :provider (kebab->capital provider)
            :nonce nonce}
          (id/init-player-identity tx)
          doall ;; this returns null list if player does not exist. Does not throw/revert
          ((fn [result] (if (empty? result)
            (throw (Exception. "Player does not exist in realm of jinn"))
            ;; only return nonce. dont want to leak data by returning identity node id, just return nonce 
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
    )
))

;;; Step #1 & #2 - Provider response handling
(defn get-oauth-api-request-config
  "@DEV: :body must be stringified before sent in request.
  We return as a map so can easily add new data to base config.

  returns - clj-http request map
  "
  [provider config]
  {:as :json ;; clj-http config to auto tranform to/from json
  ;; :coerce :always
  :async? false ;; TODO bottleneck but not important with minimal users
  ;; :content-type :application/json
  :headers  {"Authorization" (str "Basic " (base64-encode (str (:client-id config) ":" (:client-secret config))))
              "Content-Type" "application/x-www-form-urlencoded"}})


(defn request-access-token
  "Part #2 of OAuth2 flow

  1. use code to verify user authorization in our app
  2. receive access_token and refresh_token back from 

  OAuth provider returns - { :body { :scope :access_token :refresh_token :expires_in }}

  returns - pedestal response map
  "
  [provider oauth-config code]
  (let [base-config (get-oauth-api-request-config provider oauth-config)
        request-config (assoc-in base-config
                                [:form-params] ;; TODO :body or :form-params or :query-params?
                                (json/write-str {
                                  :redirect_uri (get-redirect-uri provider)
                                  :grant_type "authorization_code"
                                  :code code}))]
        ;; request-config (update-in base-config [:form-params] #(-> %
        ;;                                                   (merge {
        ;;                                                     :redirect_uri(get-in base-config [:body :redirect_uri]) :grant_type "authorization_code" :code code})
        ;;                                                   json/write-str))]
        ;; request-config (update-in base-config [:body] #(merge % {:grant_type "authorization_code" :code code}))]
      ;; (println "generate oauth req" provider (:client-id config) )
      (println "request" request-config)
    (println "requesting " provider " server" (:token-uri oauth-config))
    
    ;; (client/post (:token-uri config)  (assoc request-config :body (json/write-str request-config))
    (client/post (:token-uri oauth-config) request-config
      (fn [response]
        (println "oauth token response" response)
        {:status 200 :body (str provider " Item Successfully Equipped!")})
      (fn [exception]
        (println "oauth error is: " (ex-cause exception) (ex-message exception))
        (println "Full Excetion " exception)
        {:status 400 :body "1 Error on OAuth provider issuing access token"}))
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
                
          ;; (println request)
          ;; (println qs provider (:token-uri config) code)
    (cond
      (clojure.string/blank? provider)    {:status 400 :body "must include oauth provider"}
      (nil? config)                       {:status 400 :body "oauth provider not supported"}
      (= error "access_denied")           {:status 400 :body "user rejected access"}
      (not (clojure.string/blank? error)) {:status 400 :body error} ; catch all error last
      (clojure.string/blank? code)        {:status 400 :body "no code provided for oauth flow"}
      :else                               (request-access-token provider config code))
      ;; figure out how to request token to app
      ;; reconstruct oauth provider map on frontend
      ;; need - authorize uri, token uri, client id, client secret
  
  

  ;; if not provider - throw invalid oauth request
  ;; if (cond error 
  ;;    "access_denied" user rejected access
  ;;    "error on oauth provider")
  ;; POST `/api/token` 
  ;; (-> service-map
  ;;   (http/default-interceptors))
))



(defn oauth-refresh-token-handler
  "Part #3 of OAuth2 flow
  After access token expires, return a refresh token to get a new access token
  
  returns - pedestal response map
  "
  [provider config token]
  (let [base-config (get-oauth-api-request-config provider config)
        request-config (update-in base-config
                                  [:body] 
                                  #(-> %
                                    (merge {:grant_type "authorization_code" :refresh_token token})
                                    json/write-str))]
  )
)




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

