;; generalized implementation of oauth protocol to add any amount of login providers to app easily
;; generate a test suite for each function in 
;; the most important functions are oath-callback-handler, request-access-token, refresh-access-token, decode-oauth-state
;; write a new (testing) case for each logic path, code branch, edge case, positive and negative assertions, 
;; 

;; For oauth-callback-handler write additional tests that cover
;; 1. Flow for native app vs webapp
;; 2. Redirect_url usage
;; 3. Redirect state and formats for signatures like on frontend

(ns master-djinn.portal.core-test
  (:require [clojure.test :refer :all]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.crypto :as crypt]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.types.core :refer [load-config]]
            [clojure.string :as str])
    (:import  (org.web3j.crypto ECKeyPair Sign Keys)))

(deftest get-redirect-uri-test
  (testing "Constructs correct redirect URI"
    (with-redefs [load-config (constantly {:api-domain "test.jinni.health"})]
      (is (= "https://test.jinni.health/oauth/callback?provider=Spotify"
             (portal/get-redirect-uri "Spotify")))
    (is (= "https://test.jinni.health/oauth/callback?provider="
             (portal/get-redirect-uri "")))
    (is (= "https://test.jinni.health/oauth/callback?provider="
             (portal/get-redirect-uri nil)))
    (is (= "https://test.jinni.health/oauth/callback?provider=82983519"
             (portal/get-redirect-uri 82983519))))))

(deftest base64-encode-test
  (testing "Correctly encodes string to base64"
    (is (= "SGVsbG8gV29ybGQ="
           (portal/base64-encode "Hello World")))))

(deftest get-provider-auth-token-test
  (testing "Constructs correct Basic auth token from portal config mapping"
    (with-redefs [portal/oauth-providers {:TestProvider {:client-id "test-id" :client-secret "test-secret"}}]
      (is (= "Basic dGVzdC1pZDp0ZXN0LXNlY3JldA=="
             (portal/get-provider-auth-token "TestProvider")))
      (is (= "Basic Og=="
             (portal/get-provider-auth-token "")))
      (is (thrown? Exception
             (portal/get-provider-auth-token nil))))))

(deftest get-oauth-login-request-config-test
  (testing "Returns correct request config"
    (with-redefs [portal/get-provider-auth-token (constantly "Basic test-token")]
      (let [config (portal/get-oauth-login-request-config "TestProvider")]
        (is (= :json (:accept config)))
        (is (false? (:async? config)))
        (is (= {"Authorization" "Basic test-token"
                "Content-Type" "application/x-www-form-urlencoded"}
               (:headers config)))))))

(deftest decode-oauth-state-test
  (testing "Correctly decodes valid state"
  ;; state = "{playerId}.{clientReactNativePlatform}.{randomNonce}.{playerSignatureForAouthRequest}"
  ;; signature = "{playerId}.{oauthProviderId}.{randomNonce}"
    (let [
          provider "TestProvider"
          nonce "randomnonce"
          player (crypt/get-signer "1358230598135")
          pid (:pid player)
          signature (crypt/sign (str pid "." provider "." nonce) (:pk player))]

   (is (= pid (portal/decode-oauth-state provider (str/join "." [pid "web" nonce signature]))))

  (testing "Returns nil for invalid state"
    (is (nil? (portal/decode-oauth-state nil "invalid.state.format")))
    (is (nil? (portal/decode-oauth-state provider "invalid.state.format")))
    (is (nil? (portal/decode-oauth-state provider "invalid.state.format.0xmsg")))
    (is (nil? (portal/decode-oauth-state provider signature)))
    (is (nil? (portal/decode-oauth-state provider signature)))
    ; valid state sig w/ invalid state still nil
    (is (nil? (portal/decode-oauth-state "" (str "invalid.state.format." (crypt/sign "invalid..format" "aaaaaaaa0000000000aaaaa")))))
    (is (nil? (portal/decode-oauth-state provider (str "invalid.state.format." (crypt/sign "invalid.TestProvider.format" "aaaaaaaa0000000000aaaaa")))))
    (is (nil? (portal/decode-oauth-state "Github" (str "invalid.state.format." (crypt/sign "invalid.Github.format" "aaaaaaaa0000000000aaaaa")))))
    (is (nil? (portal/decode-oauth-state "Github" (str "invalid.state.format." (crypt/sign "invalid.Github.format" "aaaaaaaa0000000000aaaaa"))))))
)))

(deftest request-access-token-test
  (testing "Successfully requests access token"
    (with-redefs [
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  clj-http.client/post (constantly {:body "{\"access_token\":\"test-token\",\"refresh_token\":\"test-refresh\"}"})]
      (let [result (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")]
        (is (= "test-token" (:access_token result)))
        (is (= "test-refresh" (:refresh_token result))))))
  
  (testing "Returns nil on http error invalid provider/url or 401/403 access denied"
    (with-redefs [
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  ;; TODO fuzz eerror msg
                  clj-http.client/post (constantly {:body (str "{\"error\":\"" "ansfajnfaiuefni3912ir20392jn" "\"}")})]
      (is (nil? (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")))))
  
  (testing "Returns http body when no error"
    (with-redefs [
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  ;; TODO fuzz resposne data
                  clj-http.client/post (constantly {:body (str "{\"anyThing\":\""  "theanythingmessage"  "\"}")})]
      (is (= {:anyThing "theanythingmessage" } (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")))))
  
  (testing "Returns nil on 401/403 access denied"
    (with-redefs [
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  clj-http.client/post (constantly {:body "{\"error\":\"invalid_access_token\"}"})]
      (is (nil? (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")))))
  
  (testing "Requests if access_token but no refresh_token in config"
    (with-redefs [
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  clj-http.client/post (constantly {:body "{\"access_token\": \"new-access-token\", \"refresh_token\": \"new-refresh-token\"}"})]
        (let [response (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")]
            (is (= "new-access-token" (:access_token response)))
        )
    ))
      
)




(deftest oauth-callback-handler-test
  (testing "Successful OAuth callback for web app returns json with oauth data"
    (with-redefs [portal/decode-oauth-state (constantly "test-pid")
                  portal/request-access-token (constantly {:access_token "test-token" :refresh_token "test-refresh"})
                  master-djinn.util.db.identity/init-player-identity (constantly "test-pid")
                  master-djinn.util.db.identity/set-identity-credentials (constantly "test-pid")]
      (let [provider "Github"
            request {:query-params {:provider provider
                                    :code "test-code" 
                                    :state "test-pid.web.nonce.signature"}}
            response (portal/oauth-callback-handler request)]
        (is (= 301 (:status response)))
        (is (= (str "{\"id\":\"test-pid\",\"provider\":\""provider"\",\"state\":\"test-pid.web.nonce.signature\",\"msg\":\""provider" Item Successfully Equipped!\"}")
               (:body response))))))
  
  (testing "Successful OAuth callbacks on native apps redirect to deeplinks in the app"
    (with-redefs [portal/decode-oauth-state (constantly "test-pid")
                  portal/request-access-token (constantly {:access_token "test-token" :refresh_token "test-refresh"})
                  master-djinn.util.db.identity/init-player-identity (constantly "test-pid")
                  master-djinn.util.db.identity/set-identity-credentials (constantly "test-pid")]
      (let [provider "Github"
            request {:query-params {:provider provider
                                    :code "test-code" 
                                    :state "test-pid.native.nonce.signature"}}
            response (portal/oauth-callback-handler request)]
        (is (= 301 (:status response)))
        (is (= (str "jinni-health://inventory/"provider"?state=test-pid.native.nonce.signature")
               (get-in response [:headers "Location"]))))))
    
    
    (let [provider "Github" nonce "ajsnfakjnsfaf"
          player (crypt/get-signer "1358230598135")
          pid (:pid player)
          signature (crypt/sign (str pid "." provider "." nonce) (:pk player))
          request {:query-params {:provider provider
                                    :code "test-code" 
                                    :state (str pid "." "android" "." nonce "." signature)}}]
    (testing "handles invalid oauth JSON response from provider"
        (with-redefs [portal/request-access-token (constantly {:non-oauth-response "notatoken"})
                    master-djinn.util.db.identity/init-player-identity (constantly pid)]
        (let [response (portal/oauth-callback-handler request)]
            (is (= 400 (:status response)))
            (is (= "Error on OAuth provider issuing access token" (:body response))))))

    (testing "Throws error on missing access token in token request"
        (with-redefs [portal/request-access-token (constantly {})
                    master-djinn.util.db.identity/init-player-identity (constantly pid)]
        (let [response (portal/oauth-callback-handler request)]
            (is (= 400 (:status response)))
            (is (= "Error on OAuth provider issuing access token" (:body response))))))

    (testing "Completes oauth flow if no refresh token provided"
        (with-redefs [portal/request-access-token (constantly {:access_token "Asfnawfawfa"})
                        master-djinn.util.db.identity/init-player-identity (constantly pid)]
            (let [response (portal/oauth-callback-handler request)]
                (is (= 301 (:status response)))
                (is (some? (:body response))))))
    ;; (refresh-token) tests show that we can still get new access_token using oauth even if no refresh_token 

  (testing "Handles invalid inputs, signatures, and error cases"
    (let [gen-state  (fn [msg] (crypt/sign msg (:pk player)))]

    (are [query-params expected-status expected-body]
         (= {:status expected-status :body expected-body}
            (select-keys (portal/oauth-callback-handler {:query-params query-params}) [:status :body]))
        
        ;; TOO "Handles expired state signature"

      ;; "Handles missing state parameter"
      {:provider "TestProvider" :code "asfawf"}
      401 "unverified player"

      ;; bad state cant be decoded thus no player to oauth. always reverts here first if bad state
      {:provider "TestProvider" :code "" :state "bad.state.components.0xhere"}
      401 "unverified player"

        ;;  "Handles malformed state format"
      {:provider "TestProvider" :code "test" :state "invalid.stateFormatting"}
      401 "unverified player"

      {:provider "TestProvider" :code "test" :state "ajsnakjnwjawf.#@#^@$&#@(#@.@#%@)O#)$!.0xasn@#%@(D(VISnq3jr1(I!1))))"}
      401 "unverified player"

        ;; valid provider in url params but different provider signed by player
      {:provider "Github" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "UnsupportedProvider" "." "nonce1")))}
      401 "unverified player"

      {:provider "" :code "test" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "" "." "nonce1")))}
      400 "must include oauth provider"
      
      ;; "Handles invalid provider"
      {:provider "UnsupportedProvider" :code "test" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "UnsupportedProvider" "." "nonce1")))}
      400 "oauth provider not supported"
      
      {:provider "Github" :error "access_denied" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "Github" "." "nonce1")))}
      400 "user rejected access"
      
      {:provider "Github" :error "some_other_error" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "Github" "." "nonce1")))}
      400 "some_other_error"

        ;; init-player-identity is stubbed so assume player is real
        ;; see util/identity.clj for tests on init-player-identity 

        ;;  "Handles empty code parameter"
      {:provider "Github" :code "" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "Github" "." "nonce1")))}
      400 "no code provided for oauth flow")
    )))
)


(deftest refresh-access-token-test
  (let [test-player-id "test-player"
        test-provider "TestProvider"
        old-refresh-token "old-refresh"
        new-access-token "new-token"
        new-refresh-token "new-refresh"]

    (testing "Successfully refreshes access token"
      (with-redefs [master-djinn.util.db.identity/getid (constantly {:refresh_token old-refresh-token})
                    portal/get-oauth-login-request-config (constantly {})
                    clj-http.client/post (constantly {:body (str "{\"access_token\":\"" new-access-token "\",\"refresh_token\":\"" new-refresh-token "\"}")})
                    db/call (constantly nil)]
        (let [result (portal/refresh-access-token test-player-id test-provider)]
          (is (= new-access-token result)))))

    (testing "On success calls db/call with correct data"
      (let [db-call-args (atom nil)]
        (with-redefs [master-djinn.util.db.identity/getid (constantly {:refresh_token old-refresh-token})
                      portal/get-oauth-login-request-config (constantly {})
                      clj-http.client/post (constantly {:body (str "{\"access_token\":\"" new-access-token "\",\"refresh_token\":\"" new-refresh-token "\"}")})
                      db/call (fn [& args] (reset! db-call-args args))]
          (portal/refresh-access-token test-player-id test-provider)
          (is (= [master-djinn.util.db.identity/set-identity-credentials 
                  {:pid test-player-id
                   :provider test-provider
                   :access_token new-access-token
                   :refresh_token new-refresh-token}] 
                 @db-call-args)))))

    (testing "On success actually updates database with new token values"
      (let [test-db (atom {})]
        (with-redefs [master-djinn.util.db.identity/getid (fn [pid provider] (get @test-db [pid provider]))
                      portal/get-oauth-login-request-config (constantly {})
                      clj-http.client/post (constantly {:body (str "{\"access_token\":\"" new-access-token "\",\"refresh_token\":\"" new-refresh-token "\"}")})
                      db/call (fn [f & args] (swap! test-db assoc [test-player-id test-provider] args))]
          (portal/refresh-access-token test-player-id test-provider)
          (let [updated-identity (first (get @test-db [test-player-id test-provider]))]
            (is (= new-access-token (:access_token updated-identity)))
            (is (= new-refresh-token (:refresh_token updated-identity)))))))

    (testing "Throws exception on refresh failure"
      (with-redefs [master-djinn.util.db.identity/getid (constantly {:refresh_token old-refresh-token})
                    portal/get-oauth-login-request-config (constantly {})
                    clj-http.client/post (constantly {:body "{\"error\":\"invalid_grant\"}"})]
        (is (thrown? Exception (portal/refresh-access-token test-player-id test-provider)))
        (try (portal/refresh-access-token test-player-id test-provider)
        (catch Exception e
            (is (= "bad_refresh_token" (ex-message e)))
            (is (= {:status 401} (ex-data e)))))))))

(deftest oauthed-request-config-test
  (testing "Constructs correct oauthed request config"
    (let [config (portal/oauthed-request-config "test-token")]
      (is (= :json (:accept config)))
      (is (false? (:async? config)))
      (is (= {"Authorization" "Bearer test-token"
              "Content-Type" "application/json"}
             (:headers config))))))