(ns master-djinn.incantations.evoke.spotify
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid action-type->name]]
            [master-djinn.portal.identity :refer [refresh-access-token get-provider-auth-token]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.types.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

;; TODO function to construct Auth headers. probs in protal.identity since thats most abstract atm
;; just use neo4j/execute not defquery bc they wont be reused anywhere else

;; Also figure out best way to use clj-http. ideally async bc then everything is in tail but had issues with that 
;; creating response in (let) then accessing is ok but not concurrent and i could see how it might not handle errors great
(defonce API_URL "https://api.spotify.com/v1")
(defonce PROVIDER "spotify")

(defn get-user-profile
    [url player_id token]
    (try (let [res (client/get url {:accept :json :async? false
                                    :headers {"Authorization" (str "Bearer "token) ;; (get-provider-auth-token PROVIDER);; (str "Bearer "token)
                                        "Content-Type" "application/json"}})]
        (cond
            (not (nil? (:body res))) (db/call iddb/sync-provider-id {
                :pid player_id ;; TODO player_id once init-auth-handler fixed
                :provider PROVIDER
                :provider_id (:id (json->map (:body res)))})
            (= 401 (:status (ex-data res)))
                (try (refresh-access-token player_id PROVIDER)
                    (get-user-profile url player_id)
                    (catch Exception err
                        (println (str "Error fetching profile with refreshed token for *" PROVIDER "*: ") (ex-message err) (ex-data err))))
            :else  (println (str "Error syncing provider id on *" PROVIDER "*: ") (.getMessage res))))
    ;; 4/500 codes are going thru success path so this isnt neccessary but here just in case
    (catch Exception err
        (println "get spotify profile fail" (ex-data err))
        (cond
            (= 401 (:status (ex-data err)))
                (try (refresh-access-token player_id PROVIDER)
                    (get-user-profile url player_id)
                    (catch Exception err
                        (println (str "Error fetching profile with refreshed token for *" PROVIDER "*: ") (ex-message err) (ex-data err))))
            :else (println (str "Error syncing provider id on *" PROVIDER "*: ") (ex-message err) (ex-data err))))
    )
)

;; TODO abstract to use non-hardcoded provider vals. add base API url to oauth-providers in id
(defn sync-provider-id
    [player_id]
    (let [id (iddb/getid player_id PROVIDER)
          url (str API_URL "/me")]
        (cond
            (not id) {:error "no id provider identity"}
            (not (:access_token id)) {:error "no id provider access token"}
            (not (nil? (:provider_id id))) {:error "already synced id from provider"} ;; already added synced id
            :else (get-user-profile url player_id (:access_token id)))))

(defn create-playlist
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/create-playlist"
    [player_id name desc collaborators]
    ;; get username + access_token from db for player id
    ;; 
    ;; (let [url (str API_URL "/users/" {provider_id} "/playlists")
    
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

(defn follow-player
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/follow-artists-users"
    [player_id target_players]
    (let [player (iddb/getid player_id PROVIDER)
        targets (map #(:provider_id (iddb/getid % PROVIDER)) target_players)
        url (str API_URL "/me/following?type=user&ids="(clojure.string/join targets ","))
        response (client/put url)
        ])
    
    ;; if not error then return nil
    ;; else return error msg
)


(defn get-top-tracks
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks"
    [player_id target_player_id]
    ;; get access_token for target_player
    (let [version "0.0.1"
        limit 20
        range "short_term" ;; short_term = 4 weeks medium_term = 6 months})
        url (str API_URL "/me/top/tracks?limit="limit"&time_range="range)
        response (client/get url)]
        ;; create action recording they visited their profile?
        ;; This will be tracked in frontend already via segment
        ;; however fits with playground model of multiple apps and selfhosted  data access
        (db/call db/batch-create-actions [{
            :player_id player_id
            :data_provider db/MASTER_DJINN_DATA_PROVIDER
            :data {
                ;; TODO need to add startTime to uuid using java and then convert to ISO locale string
                :uuid (action->uuid player_id db/MASTER_DJINN_DATA_PROVIDER db/MOBILE_APP_DATA_SOURCE (action-type->name :Perceiving) version)
                :start_time
                :end_time ;; same as startime
                :data_source db/MOBILE_APP_DATA_SOURCE
            }
        }])

    ;; (map #() (:items response))
    ;; is_local, name, href, (map :name (:artis %))
    )
)

(defn create-silent-disco
    "No API call required to Spotify, happens locally on their device. Just here to track actions"
    [player_id playlist_id]
    ;; create :Action for tracking usage
    ;; calc uuid "spotify"->"playlist"->playlist_id
    ;; MERGE Resource 
    ;; :Action[uses] :Resource
)