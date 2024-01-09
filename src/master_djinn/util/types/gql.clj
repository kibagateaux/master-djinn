(ns master-djinn.util.types.gql
  (:require [master-djinn.util.types.core :as types]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as spec-test]))


;;; API & Middleware Types

;; Queries MAY be signed but mutations MUST be
(spec/def ::query string?)
(spec/def ::variables map?)
(spec/def ::request (spec/keys
  :req-un [::query ::variables]))

(spec/def ::verification (spec/keys
;; TODO nested in [:variables :verification]
  :req-un [::_raw_query ::signature]))

;; TODO look at better spec composition examples
;; will this fail bc extra data in :variables not included in ::verification?
(spec/def ::signed-query (spec/&
  ::query
  (spec/keys :req-un [::verification])))

;; TODO change and/or to normal clojure functions instead of spec
;; (s/keys :req [::x ::y (or ::secret (and ::user ::pwd))] :opt [::z])
(spec/def ::verified-signed-query (spec/or
  ;; TODO trying to say, either not signed at at all or signed and verified
  ;; i guess depends where im using this. If in crypto util then want to check only for ::signer
  ;; if in the api middleware than want OR to handle both
  ::query 
  (spec/& ::signed-query (spec/keys :req-un [::signer])))) ;; TODO compose: need to nest ::signer inside :variables

(spec/def ::mutation (spec/&
  ::query
  (spec/keys :req-un [::verification]))) ;; TODO nested in :variables
