(ns master-djinn.incantations.transmute.spotify
  (:require [clojure.data.json :as json]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :as types]))


(defonce PROVIDER "Spotify")


(defn Playlist->Resource
  [pid username provider playlist]
  "
  @DEV: transmuter-version is UUID namespace to playlist data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  @param pid - player avatar id in Jinni
  @param username - spotify 
  @param provider - 'Spotify'
  @param playlist - 
  
  TODO: (spec/valid :spotify-playlist)
  "
;;   (println "Trans:Github:Repo" pid username provider playlist)
;; (fn [track]
        ;; (let [{:keys [name id preview_url href artists]} track
        ;;         by (map #({:id (:id %) :name (:name %) :url (:href %)}) artists)]
        ;;     {:name name :id id :url href :image preview_url :creators by}))
  (let [transmuter-version "0.0.1"
        {:keys [id name description images public href owner]} playlist
        aaaa (println "T:Spotify:Playlist" name description images)
        time-of (now)
        resource-type (types/normalize-resource-type :Music)]
        aaaa (println "T:Spotify:Playlist" time-of resource-type)
    {
    :resource_type resource-type
    :provider provider
    :player_id pid
    :player_relation  "STEWARDS"
    :data {
        :name name
        :desc description
        :url href
        :image (if (first images) (:url (first images)) nil)
        :provider_id id
        :accessibility (if public "public" "private")
        :creators (:id owner)
        :uuid (types/resource->uuid pid provider resource-type id transmuter-version)
        :last_used time-of
        :created_at time-of
    }}))

(defn Artist->Avatar
  [pid username provider artist]
  "
  @DEV: transmuter-version is UUID namespace to playlist data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  @param pid - player avatar id in Jinni
  @param repo - name of repo on github
  @param commit - individual object returned from array response from Github GraphQL query 
  
  TODO: (spec/valid :github-repo playlist)
  "
;;   (println "Trans:Github:Repo" pid username provider playlist)
  (let [transmuter-version "0.0.1"
        {:keys [name id href]} artist]
    {:id(types/avatar->uuid pid)
    :name name
    ;; :href href
    ;; :provider_id id
    }))