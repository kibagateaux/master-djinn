(ns master-djinn.incantations.conjure.spotify
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid action-type->name]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))


(defonce PROVIDER "Spotify")
(defonce CONFIG ((keyword PROVIDER) portal/oauth-providers))

(defn top-tracks
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks
    TODO: (priorty mega-low) add tracks as :Resources to DB so we can map players with similar music tastes
    "
    [player-id target-player-id]
    (let [version "0.0.1" start-time (now) limit 20
        id (iddb/getid target-player-id PROVIDER) ;; can only get top items as self.
        range "short_term" ;; short_term = 4 weeks medium_term = 6 months})
        url (str (:api-uri CONFIG) "/me/top/tracks?limit="limit"&time_range="range)]
    (try (let [res (client/get url (portal/oauthed-request-config (:access_token id)))]
        (println "get top track response" (:status res) )
        (cond
            ;; create action recording they visited their profile
            ;; This will be tracked in frontend already via segment
            ;; But fits with playground model of multiple apps and selfhosted data access
            (= 200 (:status res)) (do
                ;; TODO should this just be a relation btw players instead of action?
                (if (not player-id target-player-id)
                    (db/call db/batch-create-actions {:actions [{
                    :name  (action-type->name :Perceiving)
                    :data_provider db/MASTER_DJINN_DATA_PROVIDER
                    :player_id player-id
                    :player_relation "DID"
                    :data {
                        ;; TODO need to add startTime to uuid using java and then convert to ISO locale string
                        :players [target-player-id]
                        :uuid (action->uuid player-id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (action-type->name :Perceiving) start-time version)
                        :start_time start-time
                        :end_time start-time
                        :data_source db/MOBILE_APP_DATA_SOURCE
                        :action_id "spotify_top_tracks"
                    }
                }]}) nil)
                ;; TODO should this be a transmute into :Resource?
                (doall (map (fn [track]
                    (let [{:keys [name id preview_url href artists]} track
                            by (map #({:id (:id %) :name (:name %) :href (:href %)}) artists)
                            ]
                        {:name name :provider_id id :href href :image preview_url :creators by
                        :accessibility "PUBLIC" :provider PROVIDER}))
                (:items (json->map (:body res)))))
            )
            (= 401 (:status res)) (try
                    (portal/refresh-access-token player-id PROVIDER) 
                    (top-tracks player-id target-player-id)
                    (catch Exception err (println
                        (str "Conjure:Spotify:TopTracks ERROR #1 retrieving with refreshed token")
                        (ex-message err) (ex-data err))))
            :else  (println (str "Error requesting top tracks on *" PROVIDER "*: ") res)))
    (catch Exception err
        (println (str "Error gettign top tracks on *" PROVIDER "*: ") (ex-data err)) 
        (cond 
            (= 401 (:status (ex-data err))) (try
                        (portal/refresh-access-token player-id PROVIDER) 
                        (top-tracks player-id target-player-id)
                        (catch Exception err (println
                            (str "Conjure:Spotify:TopTracks ERROR #2 retrieving with refreshed token")
                            (ex-message err) (ex-data err))))
            :else  (println (str "Error processing top tracks on *" PROVIDER "*: ") (ex-data err))
    )))
))

(defn top-playlists
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/get-list-users-playlists
    TODO: (priorty mega-low) add playlist + tracks as :Resources to DB so we can map players with similar music tastes and add follows
    "
    [player-id target-player-id]
    (let [version "0.0.1" start-time (now) limit 20
        id (iddb/getid target-player-id PROVIDER) ;; can only get top items as self.
        url (str (:api-uri CONFIG) "/users/" (:provider_id id) "/playlists?limit="limit)]
    (try (let [res (client/get url (portal/oauthed-request-config (:access_token id)))]
        (println "get top track response" (:status res) )
        (cond
            ;; create action recording they visited their profile
            ;; This will be tracked in frontend already via segment
            ;; But fits with playground model of multiple apps and selfhosted data access
            (= 200 (:status res)) (do
                ;; TODO should this just be a relation btw players instead of action?
                (if (not player-id target-player-id)
                    (db/call db/batch-create-actions {:actions [{
                    :name  (action-type->name :Perceiving)
                    :data_provider db/MASTER_DJINN_DATA_PROVIDER
                    :player_id player-id
                    :player_relation "DID"
                    :data {
                        ;; TODO need to add startTime to uuid using java and then convert to ISO locale string
                        :players [target-player-id]
                        :uuid (action->uuid player-id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (action-type->name :Perceiving) start-time version)
                        :start_time start-time
                        :end_time start-time
                        :data_source db/MOBILE_APP_DATA_SOURCE
                        :action_id "spotify_top_playlists"
                    }
                }]}) nil)
                ;; TODO should this be a transmute into :Resource?
                (doall (map (fn [track]
                    (let [{:keys [name id images href]} track]
                        {:name name :provider_id id :href href :image (:url (first images)) :creators [player-id]
                        :accessibility "PUBLIC" :provider PROVIDER}))
                (filter :public (:items (json->map (:body res))))))
            )
            (= 401 (:status res)) (try
                    (portal/refresh-access-token player-id PROVIDER) 
                    (top-playlists player-id target-player-id)
                    (catch Exception err (println
                        (str "Conjure:Spotify:TopTracks ERROR #1 retrieving with refreshed token")
                        (ex-message err) (ex-data err))))
            :else  (println (str "Error requesting top playlists on *" PROVIDER "*: ") res)))
    (catch Exception err
        (println (str "Error gettign top playlists on *" PROVIDER "*: ") (ex-data err)) 
        (cond 
            (= 401 (:status (ex-data err))) (try
                        (portal/refresh-access-token player-id PROVIDER) 
                        (top-playlists player-id target-player-id)
                        (catch Exception err (println
                            (str "Conjure:Spotify:TopTracks ERROR #2 retrieving with refreshed token")
                            (ex-message err) (ex-data err))))
            :else  (println (str "Error processing top playlists on *" PROVIDER "*: ") (ex-data err))
    )))
))