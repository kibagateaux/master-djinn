(ns master-djinn.util.crypto
    (:require [master-djinn.util.gql.schema :refer [jinni-schema]]
    
    ))

;; (defn extract-signature
;;   [request]
;;   ;; ecrecover signed message by player
;;   ;; capture their graphql query and address
;;   ;; add both to app-context for lacinia and query context respectively
;; )

;; (def signature-interceptor
;;   "according to lacinia docs, should maybe replace this interceptor
;;   https://github.com/walmartlabs/lacinia-pedestal/blob/a6215b9e4eda573dd9ffb3d845b486641d695db6/src/com/walmartlabs/lp/pedestal2.clj#L148
;;   this just AUTHENTICATES that 0xblah sent the request and authorization is later.
;;   AUTHORIZATION that caller address can access query data handled in downstream resolvers.
;;   "
;;   (interceptor
;;    {:name ::signature
;;     :enter (fn [context]
;;               (let [{:keys [request]} context
;;                   signature (extract-signature request)]
;; TODO select parsing function based on GET vs POST
;;                 (assoc-in context [:request :lacinia-app-context :signature] signature)))}))

(defn parse-signed-GET-query
    "
    Takes a signed http request 
    http://myapi/graphql?query={me{name}} OR with variables
    http://myapi/graphql?query=myQuery($name: string){me(where: (id: $name)){name}}&variables='{\"name\":\"myname\"}'
    
    @DEV: `variables` could include vars that arent needed for query

    SHOULD include a query name so we can verify against queries in schema
    returns - GQL context with verified query and player address to be injected into lacinia-app-context
    "
    [request]
    (let [{:keys operation-name query variables } (:query-params request)
            {:keys [ v r s signature ]} variables]
    ;; TODO ideally operation-name could map to a predefined query structure so we dont to pass the entire query data structure in
    ;; theoretically this could also all for "service discovery" oce we decentralize where servers can perform certain computations based on exposed operation-names
    ;; this requires having shared types/lib between frontend and backend without duplicating code which is a longer-term lift
    (extract-encoded-data signature query v r s)
    )
)

(defn parse-signed-POST-query
    "
    Takes a signed gql request (query or mutation), gets the ETH signer, 
    http://myapi/graphql?query={me{name}} OR with variables
    http://myapi/graphql?query=myQuery($name: string){me(where: (id: $name)){name}}&variables='{\"name\":\"myname\"}'
    
    @DEV: `variables` could include vars that arent needed for query

    SHOULD include a query name so we can verify against queries in schema
    returns - GQL context with verified query and player address to be injected into lacinia-app-context
    "
    [request]
    (let [{:keys operation-name query variables signature} (:body request)
            {:keys [ v r s signature ]} variables]
    ;; TODO ideally operation-name could map to a predefined query structure stored servers so we dont to pass the entire query data structure in
    ;; theoretically this could also all for "service discovery" oce we decentralize where servers can perform certain computations based on exposed operation-names
    ;; this requires having shared types/lib between frontend and backend without duplicating code which is a longer-term lift
    (extract-encoded-data signature query v r s)
    )
)

(defn extract-encoded-data
    "takes a signed ethereum message (not transaction) and returns the signers address and raw data signed
    You cant get the raw data directly from the signed message, can only recreate input and hash it, and check that it matches signed message
    So in practice we can only accept defined GQL queries, not arbitrary ones.

    TODO 
    "
    [signed-message raw-data v r s]

    (let [prefix "\x19Ethereum Signed Message:\n32"
          message (str prefix (sha3 raw-data))
          pub (ecrecover message v r s)]
      (if (and pub (= (ethereum-address pub) (ethereum-address signed-message)))
        (ethereum-address pub)
        (throw (Exception. "Signature does not match the right signer")))))

;; chatgpt translated from function at:
;; https://gist.github.com/megamattron/94c05789e5ff410296e74dad3b528613
;; TODO make semantic - use clj data structs, etc.
(defonce GETH_SIGN_PREFIX "\u0019Ethereum Signed Message:\n")
;; TODO doesetherjs also add this prefix?
(defonce PREFIX_BYTE_LENGTH (.getBytes GETH_SIGN_PREFIX))

(defn hex->byte-array [s]
    (.toByteArray (new java.math.BigInteger s 16)))

(defn ecrecover
    [signed-hash raw-hex-data]
    ;; TODO remove 0x prefic from signed-hash and raw-data if neccesary
    (let [
        byte-data (hex->byte-array raw-hex-data)
        ;; TODO still need to remove prefix from byte-data
        r (subs signed-hash 0 64)
        s (subs signed-hash 64 128)
        _v (subs signed-hash 128 130) ;; could be 0/1 or 27/28
        v (if (< _iv 27) (+ _iv 27) _iv) ;; so coerce to ETH native 27/28
        msg-byte-length (byte-array (+ (.length GETH_SIGN_PREFIX) (.length byte-data)))]
    (println "ECRECOVER r s v: " r s v )
    ;; unnecessary bc imutable data structs in clojure
    ;; (System/arraycopy PREFIX_BYTE_LENGTH 0 msg-byte-length 0 (.length PREFIX_BYTE_LENGTH))
    ;; (System/arraycopy byte-data 0 msg-byte-length (.length PREFIX_BYTE_LENGTH) (.length byte-data))

    ;; Should use Sign.signedPrefixedMessageToKey for EIP-712 signatures?
    (let [pubkey (-> (Sign/signedMessageToKey byte-data
                        (new Sign$SignatureData (first (hex->byte-array v))
                                                (hex->byte-array r)
                                                (hex->byte-array s)))
                    .toString
                    (format "%x"))
            address (Keys/getAddress pubkey)]
            (println "ECRECOVER addy: " address)
        address)))