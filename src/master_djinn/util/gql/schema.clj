(ns master-djinn.util.gql.schema
  (:require
    [master-djinn.util.core :refer [get-signer]]
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
  ;; :Avatar/actions (fn [ctx args val]
  ;;   (println "get avatar actions" args val)
  ;;   (let [signer (get-signer ctx)])
  ;;     ;; if signer !== target_player [] else db
  ;;     (:actions (db/call db/get-player-actions {:player_id (:id val)})))
  ;; :Avatar/widgets (fn [ctx args val]
  ;;   (println "get avatar actions" args val)
  ;;   (let [signer (get-signer ctx)])
  ;;     ;; if signer !== target_player [] else db
  ;;     (:widgets (db/call db/get-player-widgets {:player_id (:id val)})))

  :Query/get_playlists i/get-playlists


  ;;; Mutations
  ;; general game actions
  :Mutation/submit_data trans/multiplexer
  :Mutation/conjure_data i/conjure-data
  :Mutation/sync_provider_id i/sync-provider-id
  ;; meta game actions
  :Mutation/jinni_activate i/jinni-activate
  :Mutation/jinni_evolution i/jinni-evolution
  :Mutation/jinni_set_widget i/jinni-activate-widget
  ;; Code
  :Mutation/sync_repos i/sync-repos
  :Mutation/track_commits i/track-commits
  ;; Music
  ;; :Query/spotify_top_tracks i/spotify-top-tracks
  :Mutation/spotify_follow i/spotify-follow
  :Mutation/spotify_disco i/spotify-disco
  ;; :Mutation/spotify_create_playlist i/spotify-create-playlist
})

(defn jinni-schema
  []
  (-> types
      (util/inject-resolvers resolver-map)
      schema/compile))