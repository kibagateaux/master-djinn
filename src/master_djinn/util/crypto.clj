(ns master-djinn.util.crypto
    (:import  (org.web3j.crypto ECKeyPair Sign Keys)
              (org.web3j.utils Numeric)))
;; ETH signing scheme specs
;; https://eips.ethereum.org/EIPS/eip-191
;; https://eips.ethereum.org/EIPS/eip-712

;; Java original sources
;; 1. https://gist.github.com/djma/386c2dcf91fefc004b14e5044facd3a9
;; 2. https://gist.github.com/megamattron/94c05789e5ff410296e74dad3b528613

;; Java uses UTF16 encoding, so each character is 2 bytes
;; so this equals 0x19 0x00 for personal messages
;; TODO not needed if using Sign.signPrefixedMessage
;; TODO does etherjs also add this prefix?
(defonce GETH_SIGN_PREFIX "\u0019Ethereum Signed Message:\n")
(defonce PREFIX_BYTE_LENGTH (.getBytes GETH_SIGN_PREFIX))

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
;; TODO defmulti
(defn bigint->hex
    "@DEV: for >256 bytes if too big of Java Long"
    [i]
    (.toString i 16))

;; MM dev samples
(def test-signer-good {:signer "0x0AdC54d8113237e452b614169469b99931cF094e"
    :query "query get_players{\n  players {\n    id\n  }\n}}"
    ;; etherscan + MM
    ;; :signature "0xffe2c2bc51477bc5c5389df59a19bd30744d6e517c4029eb0bc9f60b925c8521035934ba4fb91855656b4b82227fc2468777404c66cf81ae751264cb958b98d61c"
    ;; MEW + MM
    :signature "0x7175657279206765745f706c61796572737b5c6e2020706c6179657273207b5c6e2020202069645c6e20207d5c6e7d7d"})
(def test-signer-bad {:signer "0x0AdC54d8113237e452b614169469b99931cF094e"
    :query "Hark! This is an example signed message but an invalid query that should fail!!!"
    ;; etherscan + MM
    :signature "0x6233e0e51bd00c25c6d9176cb44d13141e476c9049fbe2a5849af6cfa5e3db784841b8fb140a0b554db3e3f41b129d8a6ad49739c959199a0efabcdcd75423f71c"})

(defn ecrecover
    "original-msg is human readable string that signer was shown
    signed-msg-hash is hexstring bytecode output of rpc/wallet signing function
    returns checksummed ethereum address that signed msg-hash"
    [signed-msg-hash original-msg]
    (let [raw-hex-str (if (clojure.string/starts-with? signed-msg-hash "0x")
                        (subs signed-msg-hash 2)
                        signed-msg-hash)
        r (hex->bytes (subs raw-hex-str 0 64))
        s (hex->bytes (subs raw-hex-str 64 128))
        _v (subs raw-hex-str 128 130) ;; could be 0/1 or 27/28
        ;; TODO cleanup and can def optimize this by just manipulating bytes manually but cant be fucked doing that with java atm
        v (hex->bytes (if (< (hex->int _v) 27) (int->hex (+ (hex->int _v) 27)) _v)) ;; so coerce to ETH native 27/28
        zzz (println "ECRECOVER r s v: " r s v)
        signature-data (new org.web3j.crypto.Sign$SignatureData (first v) r s)
        ;; Using Sign.signedPrefixedMessageToKey for EIP-712 compliant signatures
        pubkey (Sign/signedPrefixedMessageToKey (.getBytes original-msg) signature-data)]
    ;; TODO when does pubkey return null on invalid signatures?
    ;; test 1. signed-hash og-msg mismatch. 2. 
    (println "ECRECOVER addy vs expected addy: " pubkey (:signer test-signer-good))
    (if (nil? pubkey) nil (Keys/toChecksumAddress (Keys/getAddress (bigint->hex pubkey))))))

(defn extract-encoded-data
    "takes a signed ethereum message (not transaction) and returns the signers address and raw data signed
    You cant get the raw data directly from the signed message, can only recreate input and hash it, and check that it matches signed message
    So in practice we can only accept defined GQL queries, not arbitrary ones.

    TODO 
    "
    [signed-message message]
    ;; TODO anything else we want to return here like metadata?
    ;; if not then call ecrecover directly
    (ecrecover signed-message message))


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
    (let  [request (:request context)
            {sig :signature
            q :_raw_query} (:graphql-vars request)
            signer (extract-encoded-data sig q)
            ;; add signer to app context for use in resolvers
            with-signer (assoc-in context [:request :graphql-vars :signer] signer)
            ;; aaa (println "parse-signed-POST-query with sig: " (get-in with-signer [:request :graphql-vars] ))
            ;; replace original query sent with signed query for lacinia to execute secure query
            with-query (assoc-in with-signer [:request :graphql-query] q)
            ;; aaa (println "parse-signed-POST-query with sig: " (get-in with-query [:request :graphql-query] ))
            ]
        ;; @DEV: TODO FIXES
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

        (if (and sig (not signer))
            ;; else return error
            (throw (Exception. "Signature does not match the right signer"))
            with-query)))

;; TODO GET not supported by lacinia2 but no reason it shouldnt be so implement on our own
;; (defn parse-signed-GET-query
;;     "
;;     Takes a signed http request 
;;     http://myapi/graphql?query={me{name}} OR with variables
;;     http://myapi/graphql?query=myQuery($name: string){me(where: (id: $name)){name}}&variables='{\"name\":\"myname\"}'
    
;;     @DEV: `variables` could include vars that arent needed for query

;;     SHOULD include a query name so we can verify against queries in schema
;;     returns - GQL context with verified query and player address to be injected into lacinia-app-context
;;     "
;;     [request]
;;     (let []
;;     )
;; )