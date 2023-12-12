(ns master-djinn.util.gql.schema
  (:require
    [master-djinn.util.types.core :refer [types]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :as util]
    [master-djinn.incantations.transmute.core :as trans]
    [master-djinn.util.gql.incantations :as i]
    [master-djinn.util.db.core :as db]))

(def resolver-map {
  :Query/players (fn [ctx args val]
  ;; TODO add parse results fn to generate_resulver for cleaner code
    (let [results ((db/generate-resolver db/get-all-players) ctx args val)]
      (:players results)))

  
  ;; nested field resolvers e.g. { identity { avatar { actions { id }}}}
  ;; :Avatar/actions (db/generate-resolver db/get-user-actions)

  ;;; Mutations
  ;; general game actions
  :Mutation/submit_data trans/multiplexer
  :Mutation/activate_jinni i/activate-jinni
  :Mutation/sync_provider_id i/sync-provider-id
  
  ;; spotify
  :Mutation/spotify_follow i/spotify-follow
  :Mutation/spotify_disco i/spotify-disco
  :Mutation/spotify_top_tracks i/spotify-top-tracks
  :Mutation/spotify_top_playlists i/spotify-top-playlists
  ;; :Mutation/spotify_create_playlist i/spotify-create-playlist

  ;; github
  :Mutation/github_sync_repos i/github-sync-repos

})

(defn jinni-schema
  []
  (-> types
      (util/inject-resolvers resolver-map)
      schema/compile))