(ns master-djinn.util.types.core
  (:require [clojure.java.io :as io]
            [clj-uuid :as --uuid]
            [clojure.edn :as edn]))

(def types (-> "jinni-schema.edn"
      io/resource
      slurp
      edn/read-string))

;;; generate Sets for common types for easy lookups
(def valid-action-type?
  (set (get-in types [:enums :ActionTypes :values])))
(def valid-action-name?
  (set (get-in types [:enums :ActionNames :values])))
(def valid-data-provider?
  (set (get-in types [:enums :data-providers :values])))

(def local-config (-> "env.edn"
      io/resource
      slurp
      edn/read-string))

(defn load-config []
  (let [file-config local-config
        env-config {:activitydb-uri (System/getenv "ACTIVITYDB_URI")
                    :activitydb-user (System/getenv "ACTIVITYDB_USER")
                    :activitydb-pw (System/getenv "ACTIVITYDB_PW")
                    :identitydb-uri (System/getenv "IDENTITYDB_URI")
                    :identitydb-user (System/getenv "IDENTITYDB_USER")
                    :identitydb-pw (System/getenv "IDENTITYDB_PW")
                    
                    :strava-client-id (System/getenv "STRAVA_CLIENT_ID")
                    :strava-client-secret (System/getenv "STRAVA_CLIENT_SECRET")
                    :spotify-client-id (System/getenv "SPOTIFY_CLIENT_ID")
                    :spotify-client-secret (System/getenv "SPOTIFY_CLIENT_SECRET")}]
    ;; prioritize server env vars over file config
    ;; only override if there are no env vars at all.
    ;; If some are missing thats fine. Depends on services that host wants to provide
     (if (every? nil? (vals env-config)) file-config env-config)))


;; (defmacro get-func-version
;;   "Reads the custom metadata added to a function and gets the version added to it."
;;   [func]
;;   `(:v (meta (resolve ~func))))

(defn ^:private -uuid
  "recursively apply namespaces to UUID creating a hierarchy
  We dont use app/server specific namespaces so players can
  self-host their data yet be in sync with centralized db and service providers
  "
  [& namespaces]
  (str (reduce #(--uuid/v5 %1 %2) --uuid/+null+ namespaces)))

(defn ->uuid
  "UUID namespace hierarchy:
  player-id -> data provider -> data origin -> action-name -> action-start-time -> transmuter-version-number
  Initially takes to arguments for main scope for generating ids - player and data provider
  returns a function to generate ids for individual actions there after
  
  provider and source MAY be the same
  start-time MUST be local UTC format 2023-09-07T09:44:16.818Z
  "
  [player provider]
  (fn [source action-name start-time version]
    (-uuid player provider source action-name start-time version)))
  

(defn action-type->name [action-name]
  (if (valid-action-name? action-name) (name action-name) nil))

;;; TODO implement  https://github.com/typedclojure/typedclojure/
;;; https://github.com/typedclojure/typedclojure/blob/main/example-projects/spec1-type-providers/src/typed_example/spec1_extensible.clj
(defn action?
  "All keywords are underscored which is not semantic for clojure for better operability with Neo4j DB"
  [action]
  (let [type (get action :type)
        data (get action :data)]
    (and (not nil data) ;; TODO turn into (cond) checking negation with custom errors for each?
        ;; custom type may be nullbut if not null must be valid
         (and (not nil type)
              (valid-action-type? type))
         (contains? action :player_id)
        ;;  (contains? data :device_id) ;; MAY be null
         ;; data provider name MUST NOT be null
         (valid-data-provider? (get data :data_provider))
         ;; action name MUST NOT be null
         (valid-action-name? (get data :name))
         (contains? data :name)
         (contains? data :timestamp))))