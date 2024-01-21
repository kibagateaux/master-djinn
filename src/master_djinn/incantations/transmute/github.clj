(ns master-djinn.incantations.transmute.github
  (:require [clojure.data.json :as json]
            [master-djinn.util.types.core :as types]))


(defonce PROVIDER "Github")

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
        resource-type (types/normalize-resource-type :Software)]
    {
    :resource_type resource-type
    :provider provider
    :player_id pid
    :player_relation  "STEWARDS"
    :data {
        :name name
        :desc description
        :href url
        :provider_id id
        :accessibility (clojure.string/lower-case visibility)
        :creators (clojure.string/join "," (map :username (:nodes collaborators))) ;; includes owner by default
        :uuid (types/resource->uuid pid provider resource-type id transmuter-version)
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
  ;; (println "Trans:Github:Repo" pid PROVIDER commit)
  (let [transmuter-version "0.0.1"
        {:keys [oid committedDate message author]} commit
        action-type (types/normalize-action-type :Coding)]
    {
    :action_type action-type
    :provider PROVIDER
    :player_id pid
    :player_relation "DID"
    :data {
        :uuid (types/action->uuid pid PROVIDER PROVIDER action-type committedDate transmuter-version)
        :desc message
        :start_time committedDate
        :end_time committedDate
        :data_source PROVIDER}}))