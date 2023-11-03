(ns master-djinn.util.types.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as spec-test]
            [environ.core :refer [env]]
            [clj-uuid :as --uuid]))

(def types (-> "jinni-schema.edn"
            io/resource
            slurp
            edn/read-string))

;; prevent compile fail c no file. check file exists if no, empty map, else parse file.
;; (def local-config {})
(def local-config 
 (if (.exists (io/as-file "resources/env.edn"))
    (-> "env.edn"
        io/resource
        slurp
        edn/read-string)
      {}))

(defn load-config []
  (let [file-config local-config
        env-config {

                    :activitydb-uri (System/getenv "ACTIVITYDB_URI")
                    :activitydb-user (System/getenv "ACTIVITYDB_USER")
                    :activitydb-pw (System/getenv "ACTIVITYDB_PW")
                    ;; :geodb-uri (System/getenv "IDENTITYDB_URI")
                    ;; :geodb-user (System/getenv "IDENTITYDB_USER")
                    ;; :geodb-pw (System/getenv "IDENTITYDB_PW")
                    
                    ;; :api-host (or (System/getenv "API_HOST") "0.0.0.0")


                    :strava-client-id (System/getenv "STRAVA_CLIENT_ID")
                    :strava-client-secret (System/getenv "STRAVA_CLIENT_SECRET")
                    :spotify-client-id (System/getenv "SPOTIFY_CLIENT_ID")
                    :spotify-client-secret (System/getenv "SPOTIFY_CLIENT_SECRET")}]
    
    ;; (println "LOAD CONFIG file exists? " "resources/env.edn" (.exists (io/as-file "resources/env.edn")))
    ;; (clojure.pprint/pprint "LOAD_CONFIG:system" env)
    (println "LOAD_CONFIG:test" (System/getenv "activitydb-uri") (System/getenv "ACTIVITYDB_URI") (env :SPOTIFY_CLIENT_ID) (env :spotify-client-id))
    ;; prioritize server env vars over file config
    ;; only override if there are no env vars at all.
    ;; If some are missing thats fine. Depends on services that host wants to provide
    ;; TODO return (into file-config env-config)
     (if (every? nil? (vals env-config)) file-config env-config)))


;; (defmacro get-func-version
;;   "Reads the custom metadata added to a function and gets the version added to it."
;;   [func]
;;   `(:v (meta (resolve ~func))))

;; TODO move uuid to crypto util
(defn uuid
  "domain = initial UUID to scope namespaces within. SHOULD ALWAYS be --uuid/+null+
  recursively apply namespaces to UUID creating a hierarchy
  We dont use app/server specific namespaces so players can
  self-host their data yet be in sync with centralized db and service providers
  "
  [domain & namespaces]
  (str (reduce #(--uuid/v5 %1 %2) (or domain --uuid/+null+) namespaces)))

;; TODO maybe should be env vars but even if self-hosted these should be the same.
;; Not relevant until people start making new games which is very far in the future
(defonce PLAYGROUND_UUID (--uuid/v5 --uuid/+namespace-url+ "https://cryptonative.ai/"))
(defonce GAME_NAME "Jinni")
(defn avatar->uuid
  "UUID namespace hierarchy:
  player-id -> playground -> game
  Use PLAYGROUND_UUID instead of uuid/+null+ because different avatars in different spaces/games should generate new ids
  "
  [player]
    (uuid player PLAYGROUND_UUID GAME_NAME))

(defn action->uuid
  "UUID namespace hierarchy:
  player-id -> data provider -> data origin -> action-name -> action-start-time -> transmuter-version-number
  Initially takes to arguments for main scope for generating ids - player and data provider
  returns a function to generate ids for individual actions there after
  
  provider and source MAY be the same
  start-time MUST be local UTC format 2023-09-07T09:44:16.818Z
  "
  [player provider source action-name start-time version]
    (uuid --uuid/+null+ player provider source action-name start-time version))
  


;;; generate Sets for common types for easy lookups
(def is-action-type?
  (set (get-in types [:enums :ActionTypes :values])))
(def is-action-name?
  (set (get-in types [:enums :ActionNames :values])))
(def is-data-provider?
  (set (get-in types [:enums :data-providers :values])))

(defn action-type->name [action-name]
  (if (is-action-name? action-name) (name action-name) nil))

;;; Crypto Types

(defn address? [str]
  (re-matches #"0x[a-fA-F0-9]{40}" str))
(defn signature? [str]
  (re-matches #"0x[a-fA-F0-9]+" str))
(spec/def ::signer address?)
(spec/def ::signature signature?)
(spec/def ::uuid uuid?)

;;; API & Middleware Types

;; Queries MAY be signed but mutations MUST be
(spec/def ::gql.query string?)
(spec/def ::gql.variables map?)
(spec/def ::gql.query-request (spec/keys
  :req-un [::gql.query ::gql.variables]))

(spec/def ::gql.verification-data (spec/keys
;; TODO nested in [:variables :verification]
  :req-un [::gql.query ::signature]))

;; TODO look at better spec composition examples
;; will this fail bc extra data in :variables not included in ::gql.verification-data?
(spec/def ::gql.signed-query (spec/&
  ::gql.query
  (spec/keys :req-un [::gql.verification-data])))

;; TODO change and/or to normal clojure functions instead of spec
;; (s/keys :req [::x ::y (or ::secret (and ::user ::pwd))] :opt [::z])
(spec/def ::gql.verified-signed-query (spec/or
  ;; TODO trying to say, either not signed at at all or signed and verified
  ;; i guess depends where im using this. If in crypto util then want to check only for ::signer
  ;; if in the api middleware than want OR to handle both
  ::gql.query 
  (spec/& ::gql.signed-query (spec/keys :req-un [::signer])))) ;; TODO compose: need to nest ::signer inside :variables

(spec/def ::gql.mutation (spec/&
  ::gql.query
  (spec/keys :req-un [::gql.verification-data]))) ;; TODO nested in :variables


;;; Game Data Types 

;; TODO should i add a global namespace like djinn- to all spec/defs?
;; In preparation for playground/decentralized architecture and other people making their own games on top.

(spec/def ::data_provider is-data-provider?)
(spec/def ::data_source string?)
(spec/def ::player_id string?)
(spec/def ::timestamp string?) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
(spec/def ::start-time ::timestamp) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
(spec/def ::end-time ::timestamp) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
;; (spec/def ::timerange (spec/&)) ;; startTime and endTime or startDate and endDate keys. + start < end
(spec/def ::action.type is-action-type?)
(spec/def ::action.name is-action-name?)
(spec/def ::action.player_relation string?) ;; label of relationship in Neo4j graph
(spec/def ::action.date-item (spec/or :s string? :i int?)) ;; TODO should be map of arbitrary keys and data types
(spec/def ::action.metadata (spec/keys
  :req-un [::uuid ::data_source ::start-time ::end-time]
  :opt-un [ ])) ;; action and provider specific data e.g. :count 


;; TODO i shouldn't need to write transmuters anymore
;; Should just write specs about input data, output :Action, and transmuter func.
;; use spec/generator to create (::transmuter [::action-source-date] ::Action)
;; maybe need a defmacro bc need parameters e.g. "Step" for android-health but "Walk" on strava
(spec/def ::android-health-source-action
  (spec/keys :req-un [::start-time ::end-time ::action.metadata ::action.date-item])) ;; TODO app specific keys to data types
(spec/def ::ios-health-source-action
  (spec/keys :req-un [::start-time ::end-time ::action.metadata ::action.date-item])) ;; TODO app specific keys to data types

(spec/def ::action.provider-data
  (spec/+ map?) ;; TODO spec/or ::android-health-source-action ::strava-source-action
) ; :distinct true


;; TODO feel like :Action.:Actions need a lot of work on how they are structured so
;; API + playgroun data system is scalable while making DB queries simp[le/efficient]
(spec/def ::db.Action (spec/keys :req-un [
  ::action.name ;; :TODO non-namespaced keys = name 
  ::action.player_relation ;; :TODO non-namespaced keys = player_relation 
  ::action.metadata ;; :TODO non-namespaced keys = data 
]))

;; TODO fix semantics here. ::Actions is a group of ::Action + metadata not just a list of ::Action. submit to db as $actions
(spec/def ::db.action-entries (spec/+ ::db.Action)) ; :distinct true
(spec/def ::db.Actions (spec/keys
    :req-un [::data_provider ::player_id ::action.name ::db.action-entries]))

;;; For some reason seems like we ned to define write/side-effect specs after types/read specs

(spec/def ::submit-data-mutation-args (spec/&
  ::gql.mutation;; TODO compose: need to nest ::action-source-data inside
  (spec/keys :req-un [::action.provider-data])))  ;; TODO non-namespaced key = :raw_data

;;; Internal Code Types
(spec/fdef ::djinn.transmuter
        :args ::submit-data-mutation-args
        :ret ::db.Actions)

;; TODO helpers for transforming normal map keys to namespaced keys that clojure spec expects
;; (defn- qualify-keys [m]
;;   (into {} (for [[k v] m] [(keyword "gql" (name k)) v])))

;; (def data {:_raw_query "" :signature ""})

;; (s/valid? ::gql/verification-data (qualify-keys data))

;;; TODO implement  https://github.com/typedclojure/typedclojure/
;;; https://github.com/typedclojure/typedclojure/blob/main/example-projects/spec1-type-providers/src/typed_example/spec1_extensible.clj