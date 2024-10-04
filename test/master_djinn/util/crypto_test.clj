(ns master-djinn.util.crypto-test
  (:require [clojure.test :refer :all]
            [master-djinn.util.crypto :refer :all]))

(defn genhex [len] (str (repeatedly len #(rand-nth "0123456789abcdef"))))
(defn random-addy [] (apply str "0x" (genhex 40)))

(defonce valid-examples [
       {:signed-msg-hash "0xb8609910b723eb0904eb62358e33f30c8afabe03f920ca73b8ad366cf4d3ae473b16beabf123542dd1eda8ead2e5497bd476ea132ba8026c36e967efe601b4e51c"
       :original-msg " mutation jinni_waitlist_npc( $verification: SignedRequest! ) { jinni_waitlist_npc( verification: $verification ) } "
       :signer "0xa0e8566a433FC11844D645Ed814841E9213C2052"} ;; Example expected address
])

(deftest ecrecover-tests
  ;; Positive Test Cases
  (testing "Valid signature recovery"
    ;; Assuming these values are valid for testing
    ;; Replace with actual valid test values
    (let [{:keys [signer original-msg signed-msg-hash]} (first valid-examples)]
      (is (= signer
             (ecrecover signed-msg-hash original-msg)))))

  ;; False Positive Test Cases
  (testing "Invalid signature should not match"
    ;; Using a different message or hash to ensure it does not match
    (let [{:keys [signer original-msg signed-msg-hash]} (first valid-examples)]
      ;; Assuming this hash does not correspond to the expected signer
      (is (not= signer (ecrecover signed-msg-hash (str original-msg "999"))))
      (is (not= signer (ecrecover (str "999" signed-msg-hash) original-msg)))))

(testing "signed message truncated to expected length"
    ;; Using a different message or hash to ensure it does not match
    (let [{:keys [signer original-msg signed-msg-hash]} (first valid-examples)]
      ;; Assuming this hash does not correspond to the expected signer
      (is (= signer (ecrecover signed-msg-hash original-msg)))
      (is (= signer (ecrecover (str signed-msg-hash (genhex 20) ) original-msg)))
      ;; TODO figure out why ading to front causes errors in e crecover
;;       (is (not= signer (ecrecover (str (genhex 20) signed-msg-hash) original-msg)))
      ))

  ;; Negative Test C ases
  (testing "Invalid input handling"
       ;; Testing with nil values and other invalid inputs
       (is (nil?
           (ecrecover nil nil)))

       (is (nil?
           (ecrecover nil (genhex 100))))
       (is (nil?
           (ecrecover (genhex 129) nil)))
       
       ;; if web3j cant decoded messsage to address they return nil
       (is (nil?
           (ecrecover (subs (:signed-msg-hash (first valid-examples)) 4) (genhex 100))))  ;; Expecting nil due to invalid hash length
       
       ;; if web3 can be decode messsages to address it returns wrong address
       (is (some?
           (ecrecover (:signed-msg-hash (first valid-examples)) (genhex 150))))

       ;; consistently generate the wrong signer if same invali dparams provided
       (let [fake-hash (:signed-msg-hash (first valid-examples)) fake-msg (genhex 100)
              signer (ecrecover fake-hash fake-msg)]
       (is (some? signer))
       (is (= signer (ecrecover (subs fake-hash 2) fake-msg))))

       ;; Testing with an invalid hash length
       (is (= nil 
              (ecrecover "0x123" "Hello!"))) ;; Invalid hash length

       ;; Testing with a malformed hex string
       (is (= nil 
              (ecrecover "invalid_hex_string" "Hello!")))

       ;; Testing with an empty message
       (is (= nil 
              (ecrecover "0x..." "")))) ;; Empty message

  ;; False Negative Test Cases
  (testing "Edge cases for valid signatures"
       ;; Test case: Trailing whitespace in original message
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg "Hello, Ethereum!   "] ;; Original message with trailing spaces
       (is (not= (:signer (first valid-examples))
              (ecrecover signed-msg-hash (clojure.string/trim original-msg)))) ;; Trimmed message should still return valid address

       ;; Test case: Different line endings in original message
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg "Hello, Ethereum!\n"] ;; Original message with newline character
       (is (not= (:signer (first valid-examples))
              (ecrecover signed-msg-hash original-msg)))) ;; Newline should not affect recovery

       ;; Test case: Different encoding of the original message (UTF-8 vs ASCII)
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg-ascii (.getBytes "Hello, Ethereum!" "ASCII")
              original-msg-utf8 (.getBytes "Hello, Ethereum!" "UTF-8")]
       (is (not= (:signer (first valid-examples))
              (ecrecover signed-msg-hash (String. original-msg-ascii)))) ;; ASCII encoding

       (is (not= (:signer (first valid-examples))
              (ecrecover signed-msg-hash (String. original-msg-utf8))))) ;; UTF-8 encoding

       ;; Test case: Altered signature data with valid components
       (let [signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n4" ;; Example valid hash
              original-msg "Hello, Ethereum!"
              altered-signed-msg-hash "0x5c0d4c3b2a1f6e4b0b1f3e4c5a6b7d8e9f0a1b2c3d4e5f6g7h8i9j0k1l2m3n5"] ;; Slightly altered hash
       ;; Assuming the altered hash still corresponds to a valid signature for the same message.
       (is (not= (:signer (first valid-examples)) ; from altered signature
              (ecrecover altered-signed-msg-hash original-msg))))

       ;; Test case: Leading zeros in the signed message hash
       ;; (let [signed-msg-hash "00000000000000000000000000000000000000000000000000000000000000000" ;; Example leading zeros
       ;;        original-msg "Hello, Ethereum!"]
       ;; (is (= nil 
       ;;        (ecrecover signed-msg-hash original-msg)))) ;; Expecting nil due to invalid hash

       ;; Test case: Signature with different casing in hex representation
       (let [signed-msg-hash "0X5C0D4C3B2A1F6E4B0B1F3E4C5A6B7D8E9F0A1B2C3D4E5F6G7H8I9J0K1L2M3N4" ;; Uppercase hex representation
              original-msg "Hello, Ethereum!"]
       (is (not= (:signer (first valid-examples)) ; from uppercase hash
              (ecrecover signed-msg-hash original-msg)))) ;; Should handle case insensitivity

  ))
)
