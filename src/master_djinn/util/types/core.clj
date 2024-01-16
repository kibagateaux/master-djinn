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

;; prevent compile fail c no file. check file exists. If no, empty map, else parse file.
(def local-config 
 (if (.exists (io/as-file "resources/env.edn"))
    (-> "env.edn"
        io/resource
        slurp
        edn/read-string)
      {}))

(defn load-config
  "Load config from Java System vars or .edn config file in /resources
  Prioritize server env vars over file config and only override System if there are no env vars at all.
  If some vars are missing thats fine. Depends on services that host wants to provide"
  []
  (let [file-config local-config
        env-config {:api-host (or (System/getenv "API_HOST") "0.0.0.0")
                    :api-domain (or (System/getenv "API_DOMAIN") "scry.jinni.health")

                    :activitydb-uri (System/getenv "ACTIVITYDB_URI")
                    :activitydb-user (System/getenv "ACTIVITYDB_USER")
                    :activitydb-pw (System/getenv "ACTIVITYDB_PW")                    
                    
                    :strava-client-id (System/getenv "STRAVA_CLIENT_ID")
                    :strava-client-secret (System/getenv "STRAVA_CLIENT_SECRET")
                    :spotify-client-id (System/getenv "SPOTIFY_CLIENT_ID")
                    :spotify-client-secret (System/getenv "SPOTIFY_CLIENT_SECRET")
                    :github-client-id (System/getenv "GITHUB_CLIENT_ID")
                    :github-client-secret (System/getenv "GITHUB_CLIENT_SECRET")}]
    
    ;; TODO return (into file-config env-config)
     (if (empty? file-config) env-config file-config)))


;; (defmacro get-func-version
;;   "Reads the custom metadata added to a function and gets the version added to it."
;;   [func]
;;   `(:v (meta (resolve ~func))))

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
(defonce PLAYGROUND_UUID (--uuid/v5 --uuid/+namespace-url+ "https://jinni.health/"))
(defonce GAME_NAME "Jinni")
(defn avatar->uuid
  "UUID namespace hierarchy:
  player-id -> playground -> game
  Use PLAYGROUND_UUID instead of uuid/+null+ because different avatars in different spaces/games should generate new ids
  "
  [player]
    (uuid PLAYGROUND_UUID GAME_NAME player))

(defn action->uuid
  "UUID namespace hierarchy:
  player-id -> data provider -> data origin -> action-type -> action-start-time -> transmuter-version-number
  Initially takes to arguments for main scope for generating ids - player and data provider
  returns a function to generate ids for individual actions there after
  
  provider and source MAY be the same
  start-time MUST be local UTC format 2023-09-07T09:44:16.818Z
  "
  ;; TODO ideally pass in standardized :Action object and then destructure so API is simpler
  [player provider source action-type start-time version]
    (uuid --uuid/+null+ player provider source action-type start-time version))
  
(defn resource->uuid
  "UUID namespace hierarchy:
  resource provider -> resource owner (provider_id) -> resource type -> resource name -> transmuter-version-number
  "
  ;; TODO ideally pass in standardized :Action object and then destructure so API is simpler
  [owner provider resource-type name version]
    (uuid --uuid/+null+ owner provider resource-type name version))
  
;;; generate Sets for common types for easy lookups
(def is-action-type?
  (set (get-in types [:enums :ActionTypes :values])))
(def is-action-relation?
  (set (get-in types [:enums :ActionRelations :values])))
(def is-resource-type?
  (set (get-in types [:enums :ResourceTypes :values])))
(def is-data-provider?
  (set (get-in types [:enums :Providers :values])))

(defn normalize-action-type [action-type]
  (if (is-action-type? action-type) (name action-type) nil))
(defn normalize-resource-type [resource-name]
  (if (is-resource-type? resource-name) (name resource-name) nil))

;;; Crypto Types
(defn address? [str]
  (re-matches #"0x[a-fA-F0-9]{40}" str))
(defn date? "check YYYY-MM-DD format. NOT a timestamp" [str]
  (re-matches #"\d{4}-\d{2}-\d{2}" str))
(defn signature? [str]
  (re-matches #"0x[a-fA-F0-9]+" str))
(defn uuid-v5? "specifically matches UUID v5 which we use exclusively" [str]
  (re-matches #"(?i)^[0-9A-F]{8}-[0-9A-F]{4}-[5][0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}$" str))
(spec/def ::signer address?)
(spec/def ::signature signature?)
(spec/def ::id string?) ;; usually players public id on a provider
(spec/def ::uuid uuid-v5?) ;; UUID for any entity in system
(spec/def ::empty-array (spec/or :vec (spec/and vector? empty?) :list (spec/and list? empty?)))
(spec/def ::access_token string?) ;; usually players public id on a provider
(spec/def ::refresh_token string?) ;; usually players public id on a provider

;;; Basic Data Types
(spec/def ::name string?)
(spec/def ::birthday date?)

(spec/def ::provider is-data-provider?)
(spec/def ::data_source string?)
(spec/def ::player_id address?)
(spec/def ::timestamp string?) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
(spec/def ::player_relation is-action-relation?)
;;; Input Data From Players & Providers
; Action
(spec/def ::startTime ::timestamp) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
(spec/def ::endTime ::timestamp) ;; TODO regex for ISO "2023-09-07T09:44:16.818Z"
(spec/def ::count (spec/and int? pos?))
(spec/def ::action_type string?) ;; nnormalized or on-normalized basic inputs e.g Step
(spec/def ::metadata map?)
; Resource
(spec/def ::resource_type string?)  ;; nnormalized or on-normalized basic inputs e.g Step
(spec/def ::image string?)
(spec/def ::url string?)
(spec/def ::creators string?)

;; per provider input data types. TODO match ::provider to ::raw_data input
(spec/def ::android-health-connect-action
  (spec/keys :req-un [::startTime ::endTime ::metadata]
            :opt-un [::count]))
(spec/def ::ios-health-action
  (spec/keys :req-un [::startTime ::endTime]))
(spec/def ::raw_data
  (spec/* (spec/or ::android-health-connect-action ::ios-health-action)))

(spec/def ::provider-input-actions (spec/keys :req-un [
    ::provider
    ::player_id
    ::action_type
    ::raw_data]))
;;; TODO implement  https://github.com/typedclojure/typedclojure/
;;; https://github.com/typedclojure/typedclojure/blob/main/example-projects/spec1-type-providers/src/typed_example/spec1_extensible.clj
;; https://github.com/bhb/expound