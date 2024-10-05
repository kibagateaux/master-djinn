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
    (let [pid "0x1234"
          provider "TestProvider"
          nonce "randomnonce"
          signature (crypt/sign (str pid "." provider "." nonce) "aaaaaaaa0000000000aaaaa") ; sign with random adress
        ]
      (with-redefs [crypt/ecrecover (constantly pid)]
        (is (= pid (portal/decode-oauth-state provider (str/join "." [pid "web" nonce signature])))))))
  
  (testing "Returns nil for invalid state"
    (is (nil? (portal/decode-oauth-state "TestProvider" "invalid.state.format")))))

(deftest request-access-token-test
  (testing "Successfully requests access token"
    (with-redefs [portal/get-oauth-login-request-config (constantly {})
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  clj-http.client/post (constantly {:body "{\"access_token\":\"test-token\",\"refresh_token\":\"test-refresh\"}"})]
      (let [result (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")]
        (is (= "test-token" (:access_token result)))
        (is (= "test-refresh" (:refresh_token result))))))
  
  (testing "Returns nil on error response"
    (with-redefs [portal/get-oauth-login-request-config (constantly {})
                  portal/get-redirect-uri (constantly "http://test-redirect")
                  clj-http.client/post (constantly {:body "{\"error\":\"invalid_grant\"}"})]
      (is (some? (:error (portal/request-access-token "TestProvider" {:token-uri "http://test-token-uri"} "test-code")))))))

(deftest oauth-callback-handler-test
  (testing "Successful OAuth callback for web app"
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
        (is (= (str "{\"id\":\"test-pid\",\"provider\":\""provider"\",\"state\":\"test-pid.web.nonce.signature\",\"msg\":\""provider"Item Successfully Equipped!\"}")
               (:body response))))))
  
  (testing "Successful OAuth callbacks on native apps redirect to eeplinks in the app"
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
  
  (testing "Handles various error cases"
    (let [
        pk (ECKeyPair/create (BigInteger. "1234567890"))
        pubk (.getPublicKey pk)
        pid (Keys/toChecksumAddress (Keys/getAddress pubk))
        gen-state  (fn [msg] (crypt/sign msg pk))
        test-data (gen-state (str pid"." "" "." "nonce1"))
    ]
    
    (println "Decoding generated state" test-data )
    (println "Decoding generated state" pid (crypt/ecrecover test-data (str pid"." "" "." "nonce1")))
    
    (are [query-params expected-status expected-body]
         (= {:status expected-status :body expected-body}
            (select-keys (portal/oauth-callback-handler {:query-params query-params}) [:status :body]))
      
      ;; bad state cant be decoded thus no player to oauth. always reverts here first if bad state
      {:provider "TestProvider" :code "" :state "bad.state.components.0xhere"}
      401 "unverified player"

      {:provider "TestProvider" :code "test" :state "invalid.stateFormatting"}
      401 "unverified player"

        ;; valid provider in url params but different provider signed by player
      {:provider "Github" :error "access_denied" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "UnsupportedProvider" "." "nonce1")))}
      401 "unverified player"

      {:provider "" :code "test" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "" "." "nonce1")))}
      400 "must include oauth provider"
      
      {:provider "UnsupportedProvider" :code "test" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "UnsupportedProvider" "." "nonce1")))}
      400 "oauth provider not supported"
      
      {:provider "Github" :error "access_denied" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "Github" "." "nonce1")))}
      400 "user rejected access"
      
      

        ;; need actual state with pid
      {:provider "Github" :code "" :state (str pid"." "web" "." "nonce1" "." (gen-state (str pid"." "Github" "." "nonce1")))}
      400 "no code provided for oauth flow"

      ))))

(deftest refresh-access-token-test
  (testing "Successfully refreshes access token"
    (with-redefs [master-djinn.util.db.identity/getid (constantly {:refresh_token "old-refresh"})
                  portal/get-oauth-login-request-config (constantly {})
                  clj-http.client/post (constantly {:body "{\"access_token\":\"new-token\",\"refresh_token\":\"new-refresh\"}"})
                  master-djinn.util.db.identity/set-identity-credentials (constantly "test-player")]
      (is (= "new-token" (portal/refresh-access-token "test-player" "TestProvider")))
      ))
  
  (testing "Throws exception on refresh failure"
    (with-redefs [master-djinn.util.db.identity/getid (constantly {:refresh_token "old-refresh"})
                  portal/get-oauth-login-request-config (constantly {})
                  clj-http.client/post (constantly {:body "{\"error\":\"invalid_grant\"}"})]
      (is (thrown? Exception (portal/refresh-access-token "test-player" "TestProvider"))))))

(deftest oauthed-request-config-test
  (testing "Constructs correct oauthed request config"
    (let [config (portal/oauthed-request-config "test-token")]
      (is (= :json (:accept config)))
      (is (false? (:async? config)))
      (is (= {"Authorization" "Bearer test-token"
              "Content-Type" "application/json"}
             (:headers config))))))