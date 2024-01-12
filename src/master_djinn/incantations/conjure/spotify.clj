(ns master-djinn.incantations.conjure.spotify
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid normalize-action-type]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now json->map]]
            [master-djinn.util.db.identity :as iddb]))


(defonce PROVIDER "spotify")
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
                (db/call db/batch-create-actions {:actions [{
                    :name  (normalize-action-type :Perceiving)
                    :provider db/MASTER_DJINN_DATA_PROVIDER
                    :player_id player-id
                    :player_relation "DID"
                    :data {
                        ;; TODO need to add startTime to uuid using java and then convert to ISO locale string
                        :players [target-player-id]
                        :uuid (action->uuid player-id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (normalize-action-type :Perceiving) start-time version)
                        :start_time start-time
                        :end_time start-time
                        :data_source db/MOBILE_APP_DATA_SOURCE
                    }
                }]})
                ;; TODO should this be a transmute into :Resource?
                (doall (map (fn [track]
                    (let [{:keys [name id preview_url href artists]} track
                            by (map #({:id (:id %) :name (:name %) :href (:href %)}) artists)
                            ]
                        {:name name :id id :href href :image preview_url :creators by}))
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