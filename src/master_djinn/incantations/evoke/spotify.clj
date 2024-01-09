(ns master-djinn.incantations.evoke.spotify
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid normalize-action-type]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

(defonce PROVIDER "spotify")
(defonce CONFIG ((keyword PROVIDER) portal/oauth-providers))

(defn follow-players
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/follow-artists-users"
    [player-id target-players]
    (let [version "0.0.1" start-time (now)
            id (iddb/getid player-id PROVIDER) ;; TODO checjk if null
            targets (map #(:provider_id %) (filter some? (map #(iddb/getid % PROVIDER) target-players)))
            url (str (:api-uri CONFIG)
                    "/me/following?type=user&ids="
                    (clojure.string/join "," targets))]
        (try (let [res (client/put url (portal/oauthed-request-config (:access_token id)))]    
            (cond
                (= 204 (:status res)) (db/call db/batch-create-actions {:actions [{
                    :name (normalize-action-type :Socializing)
                    :data_provider db/MASTER_DJINN_DATA_PROVIDER
                    :player_id player-id
                    :player_relation "DID"
                    :data {
                        :players  (clojure.string/join "," target-players)
                        :uuid (action->uuid player-id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (normalize-action-type :Socializing) start-time version)
                        :start_time start-time
                        :end_time start-time
                        :data_source db/MOBILE_APP_DATA_SOURCE
                    }
                }]})
                :else  (println (str "Error requesting following players on *" PROVIDER "*: ") res)))  
        (catch Exception err
            (println (str "Error following players on *" PROVIDER "*: ") (ex-data err)) 
            (cond
                (= 400 (:status (ex-data err))) (println (str "Malformed request on *" PROVIDER "*: "))
                (= 401 (:status (ex-data err))) (try
                            (portal/refresh-access-token player-id PROVIDER) 
                            (follow-players player-id target-players)
                            (catch Exception err (println
                                (str "Evoke:Spotify:Follow ERROR following with refreshed token")
                                (ex-message err) (ex-data err))))
                :else (println (str "Error processing response following players on *" PROVIDER "*: ") (ex-data err) err))
        ))
))



(defn create-silent-disco
    "No API call required to Spotify, happens locally on their device. Just here to track actions
        TODO would be dope to somehow get the jam share url and target players to join jam from inside Jinni"
    [player-id playlist-id]
    (let [version "0.0.1" start-time (now)]
        (println "spotify create silent disco" player-id playlist-id)
        (db/call db/batch-create-actions {:actions [{
            :name  (normalize-action-type :Partying)
            :data_provider db/MASTER_DJINN_DATA_PROVIDER
            :player_id player-id
            :player_relation "DID"
            :data {
                ;; :players [target_player_ids]
                :uuid (action->uuid player-id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (normalize-action-type :Perceiving) start-time version)
                :start_time start-time
                :end_time start-time ;; TODO ideally webhook when jam ends to log
                :data_source db/MOBILE_APP_DATA_SOURCE
            }
        }]})
))

(defn create-playlist
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/create-playlist
    @DEV: cant programatically add collaborators to a playlist via spotify API. 
    https://github.com/spotify/web-api/issues/637
    Could enable this feature in my app (great use case) by tracking :Resource owner and letting them add :Stewards
    when any :Steward adds a song it pulls owners access token
    "
    [player-id name desc collaborators]
    ;; get username + access_token from db for player id
    ;; 
    ;; (let [url (str (:api-uri CONFIG) "/users/" {provider_id} "/playlists")
    
    ;; response (client/post url {:name name
                                    ;; :desc (if desc desc (str "Jinni playlist made with majik by " username))
                                    ;; :collaborative true 
                                    ;; :public false})] ;; collab playlists MUST be private
    ;; TODO abstract action creating resource to DB action with
    ;; create :Action in db for user creating playlist
    ;; create resource UUID integration->type->integration_id
    ;; create :Resource in db for playlist. add uuid, external_url integration_uri, 
    ;; add relations :Action[creates] and :Avatar[stewards] to :Resource
    
    ;; TODO cant find this in their API. Was kinda the whole point of adding spotify RIP
    ;; (if collaborators )
    ;; (loop add them as playlist collarborators

    ;; return playlist id
)
