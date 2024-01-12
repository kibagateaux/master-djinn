(ns master-djinn.util.crypto
    (:import  (org.web3j.crypto ECKeyPair Sign Keys)
              (org.web3j.utils Numeric)))

(defonce MALIKS_MAJIK_CARD "0x1a861777Ba3BceD36E63242C2DdE484CA5563587")
(defonce MALEKS_MAJIK_CARD "0x46C79830a421038E75853eD0b476Ae17bFeC289A")
(defonce TEST_SIGNER "0x0AdC54d8113237e452b614169469b99931cF094e")
(defonce MASTER_DJINNS (set [
    MALIKS_MAJIK_CARD
    MALEKS_MAJIK_CARD
    TEST_SIGNER
]))

;; ETH signing scheme specs
;; https://eips.ethereum.org/EIPS/eip-191
;; https://eips.ethereum.org/EIPS/eip-712
;; https://solidity-by-example.org/signature/

;; Java original sources
;; ref - https://gist.github.com/megamattron/94c05789e5ff410296e74dad3b528613?permalink_comment_id=4350599#gistcomment-4350599
;; og - https://gist.github.com/djma/386c2dcf91fefc004b14e5044facd3a9

(defn hex->bytes
    "convert a hex string to an array of bytes in native Java
    can accept with or without 0x prefix"
    [s]
    (Numeric/hexStringToByteArray s))

(defn bytes->hex
  "Converts a byte array to a hex string"
  ([bytes]
    (bytes->hex bytes "0x"))
  ([bytes prefix]
    (str prefix (format "%x" (BigInteger. 1 bytes)))))

(defn hex->int
    [s]
    ;; (Long/parseLong s 16))
    (BigInteger. s 16))

(defn int->hex
    "@DEV: only works for <256 bytes because of Java Long. If larger values being converted switch to (BigInteger. )"
    [i]
    (Integer/toString i 16))

(defn bigint->hex
    "@DEV: for >256 bytes if too big for Java Long"
    [i]
    (.toString i 16))

;; examples
;; (def test-signer-good {:signer "0x0AdC54d8113237e452b614169469b99931cF094e"
;;     :query "query get_players{\n  players {\n    id\n  }\n}}"
;;     ;; MEW + MM
;;     ;; :signature "0x7175657279206765745f706c61796572737b5c6e2020706c6179657273207b5c6e2020202069645c6e20207d5c6e7d7d"})
;;     :signature "0xf67454814ba74310d2ed31451c8db378c2f8417d5b00aac6920d95c04c4d78fe604e8ffd8c9e9889d270a2c9a4be525ce64ff935e8a5961a57be02e75284354b1b"})
    
;; (def test-signer-good2 {:signer "0x08628f1fbcae43e0926459d2fbbd0d01702fbeea"
;;     :query "query get_players{\n  players {\n    id\n  }\n}}"
;;     ;; MEW + MM
;;     ;; :signature "0x7175657279206765745f706c61796572737b5c6e2020706c6179657273207b5c6e2020202069645c6e20207d5c6e7d7d"})
;;     :signature "77a11304fbe22046d4ecf29b2b864baa64e981e065e1c0a56f5b39407f4aa95f58616c26eb868def223942d05d2a67b75c652d700afa230c74a7585e81f8286a1c"})

;; (def test-signer-bad {:signer "0x0AdC54d8113237e452b614169469b99931cF094e"
;;     :query "Hark! This is an example signed message but an invalid query that should fail!!!"
;;     ;; etherscan + MM
;;     :signature "0x6233e0e51bd00c25c6d9176cb44d13141e476c9049fbe2a5849af6cfa5e3db784841b8fb140a0b554db3e3f41b129d8a6ad49739c959199a0efabcdcd75423f71c"})

(defn ecrecover
    "original-msg is human readable string that signer was shown
    signed-msg-hash is hexstring bytecode output of rpc/wallet signing function
    returns checksummed ethereum address that signed msg-hash
    @DEV: Assumes signed-msg-hash is EIP-712 compliant with prefix '\\x19Ethereum Signed Message:\\n{msg-length}'
    @DEV: Always returns an address even if invalid data. Must check against expected signer (def a bug, not how solidity ecrecover works, potential attack vector since not entirely based on cryptography)
        Open issue on web3j repo - https://github.com/web3j/web3j/issues/1989
    "
    [signed-msg-hash original-msg]
    (let [raw-hex-str (if (clojure.string/starts-with? signed-msg-hash "0x")
                        (subs signed-msg-hash 2)
                        signed-msg-hash)
        r (hex->bytes (subs raw-hex-str 0 64))
        s (hex->bytes (subs raw-hex-str 64 128))
        _v (subs raw-hex-str 128 130) ;; could be 0/1 or 27/28
        v (hex->bytes (if (< (hex->int _v) 27) (int->hex (+ (hex->int _v) 27)) _v)) ;; so coerce to ETH native 27/28
        signature-data (new org.web3j.crypto.Sign$SignatureData (first v) r s)
        hashed-msg (.getBytes original-msg)
        ;; byte conversion mismatch on "/n".
        fixed-hashed-msg (hex->bytes (clojure.string/replace (bytes->hex hashed-msg) #"0a" "5c6e"))
        ;; eee (println "ECRECOVER hashed msg: " (bytes->hex hashed-msg) (bytes->hex fixed-hashed-msg) )
        ;; Using Sign.signedPrefixedMessageToKey for EIP-712 compliant signatures
        pubkey (Sign/signedPrefixedMessageToKey fixed-hashed-msg signature-data)
        address (if (nil? pubkey) nil (Keys/toChecksumAddress (Keys/getAddress (bigint->hex pubkey))))]
    address))

(defn handle-signed-POST-query
    "Takes Lacinia app context including a signed gql request (query or mutation), gets the ETH signer, 
    http://myapi/graphql?query={me{name}} OR with variables
    http://myapi/graphql?query=myQuery($name: string){me(where: (id: $name)){name}}&variables='{\"name\":\"myname\"}'
    @DEV: MUST come after ::graphql-data interceptor and before ::graphql-parser interceptor from Lacinia
    @DEV: `variables` could include vars that arent needed for query

    SHOULD include a query name so we can verify against queries in schema
    returns - Pedestal request context with verified query and player address to be injected into Lacinia GQL data
    "
    [context]
    (let  [{sig :signature q :_raw_query} (get-in context [:request
                                                            :graphql-vars
                                                            :verification])
            aaa (println "util.crypto/prase-signed-query: " sig q)
            signer (ecrecover sig q)
            aaa (println "parse-signed-POST-query with sig: " signer)
            ;; add signer to app context for use in resolvers
            with-signer (assoc-in context [:request :signer] signer)
            ;; replace original query sent with signed query for lacinia to execute secure query
            with-query (assoc-in with-signer [:request :graphql-query] q)
            ;; aaa (println "parse-signed-POST-query with sig: " (get-in with-query [:request :graphql-query]))
            ]
        ;; (clojure.pprint/pprint (:request context))

        ;; MAJOR SECURITY BUG #1: if `sig` or `q` are mismatched we get WRONG address from ecrecover, NOT `nil` as expected
        ;; MAJOR SECURITY BUG #2: replay attack if query uses variables someone can get a users query and replace with any variables that they didnt approve

        ;; @DEV: TODO FIXES
        ;; 1. fix security bug #1!!!  How? check that signer is :Identity in DB (bad), pass in pid with :verification data (bad), 
        ;; 1. fix security bug #2!!!  sign variables and add in verification as well, ecrecover those and replace like query
        ;; 1. if signature/_raw_query on in POST variables even if they aren't required for the query sent
        ;; then we will still go through this code path even if we dont need to
        ;; 2. if you do `mutation submit_data(...) but define mutation/query some_other_name{...}
        ;; then lacinia will throw an invalid operation bc we are telling it to execute some_other_name
        ;; but they are still seeing submit_data somewhere in the data where we havent cleaned up properly
        ;; WORKAROUND: use unamed query/mutation in signed query and raw query
        ;; 3. create generalized query & mutation on schema so we only have one entry point for each when using signed requests
        ;; since we will extract full query from signed message and then execute that
        ;; 4. ideally operation-name could map to a predefined query structure stored servers so we dont to pass the entire query data structure in
        ;; theoretically this could also all for "service discovery" oce we decentralize where servers can perform certain computations based on exposed operation-names
        ;; this requires having shared types/lib between frontend and backend without duplicating code which is a longer-term lift

        (if (and sig (not signer)) ;; always false bc ecrecover always returns an address even if invalid
            (throw (Exception. "Signature does not match the right signer"))
            with-query)))