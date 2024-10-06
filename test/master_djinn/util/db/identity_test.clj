;; (ns master-djinn.util.db.identity-test
;;   (:require [clojure.test :refer :all]
;;               [master-djinn.util.types.core :refer [address?]]
;;               [clojure.spec.alpha :as s]

;;               [master-djinn.util.crypto :refer :all])
;;        (:import  (org.web3j.crypto ECKeyPair Sign Keys)
;;               (org.web3j.utils Numeric)))

;; TODO actually sync to a remote test database to run suite against
;; ;; DB tests

;; waitlist-npc
;; - match (a:Avatar {id: $pid}), (n) RETURN count(a) as playerNodes, count(n) as totalNodes
;; - z
;; - match (a:Avatar {id: $pid}, (n) RETURN count(a) as playerNodes, count(n) as totalNodes
;; created? > playerNodes2 playerNodes1
;; if created? (is = totalNodes2 (+ 1 totalNodes1))
;; else (is = totalNodes2 totalNodes1) (is = playerNodes2 playerNodes1)
;; TODO check to ensure no data is compromised on their node if they already registered. 


;; activate-jinni
;; init-player-identity
;; - returns nil if (a:Avatar {id: $pid}) not already in database
;; - returns nonce if identity created_at
;; - 

;; set-identity-credentials
;; - must have access_token provider and pid as params
;; - resets access token  and refesh_token if present


;; get-identity

