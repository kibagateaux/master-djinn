(ns master-djinn.incantations.transmute.github
  (:require [clojure.data.json :as json]
            [master-djinn.util.types.core :as types]))


(defonce transmuter-provider "Github")

(defn Repo->Resource
  [pid username provider input]
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  @param pid - player avatar id in Jinni
  @param repo - name of repo on github
  @param commit - individual object returned from array response from Github GraphQL query 
  
  TODO: (spec/valid :github-repo input)
  "
;;   (println "Trans:Github:Repo" pid username provider input)
  (let [transmuter-version "0.0.1"
        {:keys [id name description url visibility createdAt pushedAt owner collaborators]} input
        resource-owner (:username (:owner input))
        resource-type (types/normalize-resource-type :Software)]
    {
    :resource_type resource-type
    :provider provider
    :player_id pid
    ;; cypher cant MERGE on dynamic relations, only use STEWARD until figure out way to prevent dupes
    ;; :player_relation (if (= username resource-owner) "CONTROLS" "STEWARDS")
    :player_relation  "STEWARDS"
    :data {
        :name name
        :desc description
        :href url
        :provider_id id
        :accessibility (clojure.string/lower-case visibility)
        :creators (clojure.string/join "," (map :username (:nodes collaborators)))
        :uuid (types/resource->uuid resource-owner provider resource-type name transmuter-version)
        :last_used pushedAt
        :created_at createdAt
    }}))

(defn Commit->Action
  [pid repo commit]
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  @param pid - player avatar id in Jinni
  @param repo - name of repo on github
  @param commit - individual object returned from array response from Github GraphQL query 
  
  TODO: (spec/valid :github-action input)
  "
  ;; (println "Trans:Github:Repo" pid transmuter-provider commit)
  (let [transmuter-version "0.0.1"
        {:keys [oid committedDate message author]} commit
        action-type (types/normalize-action-type :Coding)]
    {
    :action_type action-type
    :provider transmuter-provider
    :player_id pid
    :player_relation "DID"
    :data {
        :uuid (types/action->uuid pid transmuter-provider transmuter-provider action-type committedDate transmuter-version)
        :desc message
        :start_time committedDate
        :end_time committedDate
        :data_source transmuter-provider}}))