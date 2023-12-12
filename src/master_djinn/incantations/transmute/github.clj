(ns master-djinn.incantations.transmute.github
  (:require [clojure.data.json :as json]
            [master-djinn.util.types.core :refer [resource-type->name resource->uuid]]))


(defonce transmuter-data-provider "AndroidHealthConnect")

(defn Repo->Resource
  [pid username provider input]
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.

  TODO: (spec/valid :github-repo input)
  "
  (println "T:Github:Repo" pid username provider input)
  (let [transmuter-version "0.0.1"
        {:keys [id name description url visibility created-at pushed-at owner collaborators]} input
        resource-owner (:username (:owner input))]
    {
    :name (resource-type->name :Software)
    :provider provider
    :player_id pid
    :player_relation (if (= username resource-owner) "CONTROLS" "STEWARDS")
    :data {
        :provider provider
        :provider_id id
        :accessibility (clojure.string/lower-case visibility)
        :creators (clojure.string/join "," (concat (map :username (:nodes collaborators)) username))
        :uuid (resource->uuid provider resource-owner name transmuter-version)
        :metadata {
            :last_used pushed-at
            :created_at created-at
        }
    }}))