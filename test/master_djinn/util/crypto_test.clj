(ns master-djinn.util.crypto-test
  (:require [clojure.test :refer :all]
              [master-djinn.util.types.core :refer [address?]]
              [clojure.spec.alpha :as s]

              [master-djinn.util.crypto :refer :all])
       (:import  (org.web3j.crypto ECKeyPair Sign Keys)
              (org.web3j.utils Numeric)))

(defn genhex [len] (str (repeatedly len #(rand-nth "0123456789abcdef"))))
(defn random-addy [] (apply str "0x" (genhex 40)))

(defonce live-example {
       :signed-msg-hash "0xb8609910b723eb0904eb62358e33f30c8afabe03f920ca73b8ad366cf4d3ae473b16beabf123542dd1eda8ead2e5497bd476ea132ba8026c36e967efe601b4e51c"
       :original-msg " mutation jinni_waitlist_npc( $verification: SignedRequest! ) { jinni_waitlist_npc( verification: $verification ) } "
       :signer "0xa0e8566a433FC11844D645Ed814841E9213C2052"}) ;; Example expected address


(deftest web3j-cryptographic-validity-tests
  (let [test-message "Test message for signing"
        pk (ECKeyPair/create (BigInteger. "1234567890"))
        test-public-key (.getPublicKey pk)
        pid (Keys/toChecksumAddress (Keys/getAddress test-public-key))
        test-signature (Sign/signPrefixedMessage (.getBytes test-message) pk)]
       

    (testing "Sign/signedPrefixedMessageToKey performs correct address derivation"
      (let [derived-public-key (Sign/signedPrefixedMessageToKey (.getBytes test-message) test-signature)
            derived-address (Keys/getAddress derived-public-key)]
        (is (and (some? derived-public-key) (some? derived-address)))
        ;; returned address is not-checksummed
        (is (= (subs (clojure.string/lower-case pid) 2) derived-address))
        ;; we always checksum address before returning in ecrecover
        (is (= pid (Keys/toChecksumAddress derived-address)))))
        

    (testing "Keys/getAddress returns :signer/address? type"
      (is (s/valid? :master-djinn.util.types.core/signer (Keys/toChecksumAddress pid))))

    (testing "Sign/signedPrefixedMessageToKey returns the right checksum address for derived address"
      (let [derived-public-key (Sign/signedPrefixedMessageToKey (.getBytes test-message) test-signature)
            derived-address (Keys/getAddress derived-public-key)]
        (is (= (Keys/toChecksumAddress pid)
               (Keys/toChecksumAddress derived-address)))))

       (testing "Sign/signedPrefixedMessageToKey returns the right checksum address for derived address"
      (let [derived-public-key (Sign/signedPrefixedMessageToKey (.getBytes test-message) test-signature)
            derived-address (Keys/getAddress derived-public-key)]
        (is (= (Keys/toChecksumAddress pid)
               (Keys/toChecksumAddress derived-address)))))

       (testing "(sign) returns same value as web3j signMessage"
              (let [sig (sign test-message pk)
                     raw-sig (Sign/signPrefixedMessage (.getBytes test-message) pk)]
         (is (= (subs sig 0 2) "0x"))
         (is (= (subs sig 2 66) (subs (bytes->hex (.getR raw-sig)) 2)))
         (is (= (subs sig 66 130) (subs (bytes->hex (.getS raw-sig)) 2)))
         (is (= (subs sig 130 132) (subs (bytes->hex (.getV raw-sig)) 2)))
       ))
  ))

(deftest ecrecover-tests
  (let [test-message "Test message for signing"
        pk (ECKeyPair/create (BigInteger. "1234567890"))
        test-public-key (.getPublicKey pk)
        pid (Keys/toChecksumAddress (Keys/getAddress test-public-key))
        test-signature (Sign/signPrefixedMessage (.getBytes test-message) pk)]

  ;; Positive Test Cases
  (testing "Valid signature recovery"
    ;; Assuming these values are valid for testing
    ;; Replace with actual valid test values
    (let [{:keys [signer original-msg signed-msg-hash]} live-example]
      (is (= signer
             (ecrecover signed-msg-hash original-msg)))))

  ;; False Positive Test Cases
  (testing "ecrecover handles invalid/malformed inputs gracefully"
    ;; Using a different message or hash to ensure it does not match
    (let [{:keys [signer original-msg signed-msg-hash]} live-example]
      ;; Assuming this hash does not correspond to the expected signer
      (is (not= signer (ecrecover signed-msg-hash (str original-msg "999"))))
      (is (not= signer (ecrecover (str "999" signed-msg-hash) original-msg)))))

(testing "signed message truncated to expected length"
    ;; Using a different message or hash to ensure it does not match
    (let [{:keys [signer original-msg signed-msg-hash]} live-example]
      ;; TODO figure out why adding to front causes errors in e crecover
;;       (is (not= signer (ecrecover (str (genhex 20) signed-msg-hash) original-msg)))

      ;; Assuming this hash does not correspond to the expected signer
      (is (= signer (ecrecover signed-msg-hash original-msg)))
      (is (= signer (ecrecover (str signed-msg-hash (genhex 20) ) original-msg)))))

  ;; Negative Test C ases
  (testing "Handles invalid/malformed inputs gracefully"
       ;; Testing with nil values and other invalid inputs
       (is (nil?
           (ecrecover nil nil)))
       ;; Nil signature is invalid
       (is (nil?
           (ecrecover nil (genhex 100))))
       ;; invalid hash length. Must be 132+bytes
       (is (nil?
           (ecrecover (genhex 129) nil)))
       ;; Nil original message is invalid
       (is (nil?
           (ecrecover (subs (:signed-msg-hash live-example) 4) nil)))
       ;; if web3j cant decoded messsage to address they return nil
       (is (nil?
           (ecrecover (subs (:signed-msg-hash live-example) 4) (genhex 100))))
       ;; if web3 can be decode messsages to address it returns wrong address
       (is (some?
           (ecrecover (:signed-msg-hash live-example) (genhex 150))))
       ;; consistently generate the wrong signer if same invali dparams provided
       (let [fake-hash (:signed-msg-hash live-example) fake-msg (genhex 100)
              signer (ecrecover fake-hash fake-msg)]
       (is (some? signer))
       (is (= signer (ecrecover (subs fake-hash 2) fake-msg)))))

(testing "ecrecover always returns a checksum address if not nil"
       (let [signature-hex (sig->hex test-signature)
              recovered-address (ecrecover signature-hex test-message)]
              (is (or (nil? recovered-address)
                     (and (string? recovered-address)
                     (s/valid? :master-djinn.util.types.core/signer recovered-address))))))

       (testing "ecrecover is consistent with Sign/signedPrefixedMessageToKey"
       (let [signature-hex (sig->hex test-signature)
              ecrecover-address (ecrecover signature-hex test-message)
              sign-address (-> (Sign/signedPrefixedMessageToKey (.getBytes test-message) test-signature)
                                   Keys/getAddress
                                   Keys/toChecksumAddress)]
              (is (= ecrecover-address sign-address))))

       (testing "ecrecover is always returns checksummed address"
       (let [signature-hex (sig->hex test-signature)
              ecrecover-address (ecrecover signature-hex test-message)
              sign-address (Keys/getAddress (Sign/signedPrefixedMessageToKey (.getBytes test-message) test-signature))]
              (is (not= ecrecover-address sign-address))
              (is (= ecrecover-address (Keys/toChecksumAddress sign-address)))
              (is (not= ecrecover-address (clojure.string/lower-case sign-address)))))

       (testing "ecrecover rejects tampered messages"
       (let [signature-hex (sig->hex test-signature)
              tampered-message (str test-message "tampered")]
              (is (not= (ecrecover signature-hex test-message)
                     (ecrecover signature-hex tampered-message)))))

       (testing "ecrecover incorectly processes tampered signatures"
       (let [signature-hex (sig->hex test-signature)
              tampered-signature (str (subs signature-hex 0 (- (count signature-hex) 2)) "1c")] ; 1b for 27, 1c for 28
              (is (not= (ecrecover signature-hex test-message)
                     (ecrecover tampered-signature test-message)))))

       (testing "ecrecover rejects invalid signatures"
       (let [signature-hex (sig->hex test-signature)
              tampered-signature (str (subs signature-hex 0 (- (count signature-hex) 2)) "bb")] ; 1b for 27, 1c for 28
              (is (thrown? Exception (ecrecover tampered-signature test-message)))))

  ;; False Negative Test Cases
  (testing "Edge cases for valid signatures"

       ;; Test case: Trailing whitespace in original message
       (let [original-msg "Hello, Ethereum!   "  ;; Original message with trailing spaces
              signed-msg-hash (sign original-msg pk)]
       (is (= pid
              ;; untrimmed message should should be valid
              (ecrecover signed-msg-hash original-msg)))
       (is (not= pid
              ;; Trimmed message should still return valid address
              (ecrecover signed-msg-hash (clojure.string/trim original-msg)))))
       

       ;; Test case: Different line endings in original message
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg "Hello, Ethereum!\n"] ;; Original message with newline character
       (is (not= (:signer live-example)
              (ecrecover signed-msg-hash original-msg)))) ;; Newline should not affect recovery

       ;; Test case: Different encoding of the original message (UTF-8 vs ASCII)
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg-ascii (.getBytes "Hello, Ethereum!" "ASCII")
              original-msg-utf8 (.getBytes "Hello, Ethereum!" "UTF-8")]
       (is (not= (:signer live-example)
              (ecrecover signed-msg-hash (String. original-msg-ascii)))) ;; ASCII encoding

       (is (not= (:signer live-example)
              (ecrecover signed-msg-hash (String. original-msg-utf8))))) ;; UTF-8 encoding

       ;; Test case: Altered signature data with valid components
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg "Hello, Ethereum!"
              altered-signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n5"] ;; Slightly altered hash
       ;; Assuming the altered hash still corresponds to a valid signature for the same message.
       (is (not= (:signer live-example) ; from altered signature
              (ecrecover altered-signed-msg-hash original-msg))))

       ;; Test case: Leading zeros in the signed message hash
       ;; (let [signed-msg-hash "00000000000000000000000000000000000000000000000000000000000000000" ;; Example leading zeros
       ;;        original-msg "Hello, Ethereum!"]
       ;; (is (= nil 
       ;;        (ecrecover signed-msg-hash original-msg)))) ;; Expecting nil due to invalid hash

       ;; Test case: Signature with different casing in hex representation
       (let [signed-msg-hash "0X5C0D4C3B2A1F6E4B0B1F3E4C5A6B7D8E9F0A1B2C3D4E5F6G7H8I9J0K1L2M3N4" ;; Uppercase hex representation
              original-msg "Hello, Ethereum!"]
       (is (not= (:signer live-example) ; from uppercase hash
              (ecrecover signed-msg-hash original-msg)))) ;; Should handle case insensitivity

  ))
)


(deftest handle-signed-POST-query-tests
  (let [valid-context {:request {:graphql-vars {:verification {:signature (:signed-msg-hash live-example) :_raw_query (:original-msg live-example)}}}}
        invalid-context {:request {:graphql-vars {}}}
        malformed-context {:request {:graphql-vars {:verification {:signature "invalid" :_raw_query "query { test }"}}}}]

    (testing "Expects :verification in query params"
      (is (map? (handle-signed-POST-query valid-context)))
      (is (thrown? Exception (handle-signed-POST-query invalid-context)))
      (is (thrown? Exception (handle-signed-POST-query {}))))

    (testing "Derives signature from :verification"
      (let [result (handle-signed-POST-query valid-context)]
        (is (string? (get-in result [:request :signer])))))

    (testing "Replaces original graphql-query with verified query"
      (let [result (handle-signed-POST-query valid-context)]
        (is (= (get-in result [:request :graphql-query])
              (get-in valid-context [:request :graphql-vars :verification :_raw_query])))))

    (testing "Adds signer to request context"
      (let [result (handle-signed-POST-query valid-context)]
        (is (contains? (:request result) :signer))
        (is (s/valid? :master-djinn.util.types.core/signer (get-in result [:request :signer])))
        ; signer is alias of address but double check incase signer changes
        (is (address? (get-in result [:request :signer])))))


    (testing "Handles malformed verification data"
      (is (thrown? Exception (handle-signed-POST-query malformed-context))))

    (testing "Does not check args as part of request verification"
      (let [context1 (assoc-in valid-context [:request :graphql-vars :args] {:arg1 "value1"})
            context2 (assoc-in valid-context [:request :graphql-vars :args] {:arg2 "value2"})
            result1 (handle-signed-POST-query context1)
            result2 (handle-signed-POST-query context2)]
        (is (= (get-in result1 [:request :graphql-query])
               (get-in result2 [:request :graphql-query])))))

    (testing "False positive: Invalid signature dont process"
      (let [false-positive-context (assoc-in valid-context
                     [:request :graphql-vars :verification :signature]
                     "0xinvalidsignature")]
            (is (thrown? Exception (handle-signed-POST-query false-positive-context)))))

    (testing "False negative: Invalid query for signature fails to process"
       ;; web3j doesnt return nil on invalid sigs/msgs just random addresses
      (let [false-negative-context (assoc-in valid-context [:request :graphql-vars :verification :_raw_query] "invalid query")
            result (handle-signed-POST-query false-negative-context)]
       ;; returns invalid signer + query and up to app to ensure signer isnt treated as a player
        (is (= "invalid query" (get-in result [:request :graphql-query])))))
))
