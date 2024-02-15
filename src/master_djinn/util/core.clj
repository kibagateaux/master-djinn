(ns master-djinn.util.core
  (:require [master-djinn.util.crypto :refer [TEST_SIGNER]]
            [clojure.data.json :as json])
  (:import  (java.time Instant)))

(defn json->map [j] (json/read-str j :key-fn keyword))
(defn map->json [m] (json/write-str m))

(defn get-signer
  "extract API request signer injected by /util/crypto pedestal interceptor"
  [gql-ctx]
  (or (get-in gql-ctx [:request :signer]) TEST_SIGNER))

(defn now "get current time in ISO 8601 locale time" []
  (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")
        utc (java.time.ZoneId/of "UTC")
        now (java.time.ZonedDateTime/now utc)]
    (.format formatter now)))

;; (defn iso->unix
;;   "takes ISO 8601 time and converts to milliseconds since UNIX epoch"
;;   [ts]
;;   (* 1000 (.getEpochSecond (.parse (new Instant) ts))))


;; (defn unix->iso
;;   "takes milliseconds since UNIX epoch and converst to ISO 8601"
;;   [ts]
;;   (java.util.Date ts))

(defn get-signer
  "extract API request signer injected by /util/crypto pedestal interceptor"
  [gql-ctx]
  (or (get-in gql-ctx [:request :signer]) TEST_SIGNER))