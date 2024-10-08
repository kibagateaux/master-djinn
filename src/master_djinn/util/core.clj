(ns master-djinn.util.core
  (:require [master-djinn.util.crypto :refer [TEST_SIGNER]]
            [clojure.data.json :as json])
  (:import  (java.time Instant)))

(defn json->map [j] (json/read-str j :key-fn keyword))
(defn map->json [m] (json/write-str m))
(defn prettify [j] (json/pprint (json/read-str j {:key-fn keyword})))

(defn get-signer
  "extract API request signer injected by /util/crypto pedestal interceptor"
  [gql-ctx]
  (or (get-in gql-ctx [:request :signer]) TEST_SIGNER))

(defn now
  "get current time in ISO 8601 locale time with optional offset in seconds"
  ([]
   (now 0)) ;; default to no offset
  ([offset-seconds]
   (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")
         utc (java.time.ZoneId/of "UTC")
         now (java.time.ZonedDateTime/now utc)
         adjusted-now (if (> offset-seconds 0) 
                         (.plusSeconds now offset-seconds) 
                         (.minusSeconds now (Math/abs offset-seconds)))]
     (.format formatter adjusted-now))))

(defn iso->unix
  "takes ISO 8601 time and converts to seconds since UNIX epoch"
  [iso-timestamp]
  (.getEpochSecond (java.time.Instant/parse iso-timestamp)))

(defn update-time [ts]
  (clojure.instant/parse-timestamp ts "timestamp")) 
  ;; if need regex not string name 
  ;; (def ^:private timestamp #"(\d\d\d\d)(?:-(\d\d)(?:-(\d\d)(?:[T](\d\d)(?::(\d\d)(?::(\d\d)(?:[.](\d+))?)?)?)?)?)?(?:[Z]|([-+])(\d\d):(\d\d))?")
  ;; worse case use lib https://github.com/dm3/clojure.java-time


;; (defn iso->unix
;;   "takes ISO 8601 time and converts to milliseconds since UNIX epoch"
;;   [ts]
;;   (* 1000 (.getEpochSecond (.parse (new Instant) ts))))


;; (defn unix->iso
;;   "takes milliseconds since UNIX epoch and converst to ISO 8601"
;;   [ts]
;;   (java.util.Date ts))