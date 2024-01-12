(ns master-djinn.util.core
  (:require [master-djinn.util.crypto :refer [TEST_SIGNER]]
            [clojure.data.json :as json]))

(defn json->map [j] (json/read-str j :key-fn keyword))
(defn map->json [m] (json/write-str m))

(defn now "get current time in ISO 8601 locale time" []
  (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'")
        utc (java.time.ZoneId/of "UTC")
        now (java.time.ZonedDateTime/now utc)]
    (.format formatter now)))

(defn get-signer
  "extract API request signer injected by /util/crypto pedestal interceptor"
  [gql-ctx]
  (or (get-in gql-ctx [:request :signer]) TEST_SIGNER))