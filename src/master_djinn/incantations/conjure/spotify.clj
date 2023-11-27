(ns master-djinn.incantations.conjure.spotify
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid action-type->name]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

;; TODO function to construct Auth headers. probs in protal.identity since thats most abstract atm
;; just use neo4j/execute not defquery bc they wont be reused anywhere else

;; Also figure out best way to use clj-http. ideally async bc then everything is in tail but had issues with that 
;; creating response in (let) then accessing is ok but not concurrent and i could see how it might not handle errors great
(defonce API_URL "https://api.spotify.com/v1")
(defonce PROVIDER "spotify")

;; TODO this should be in conjure im pretty sure
(defn get-user-profile
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile"
    [player-id token]
    (try (let [url (str API_URL "/me")
                res (client/get url (portal/oauthed-request-config token))]
        (cond
;; TODO abstract into helper on portal? (make-request url method token player_id provider onData retryFunc)
            (not (nil? (:body res))) (db/call iddb/sync-provider-id {
                :pid player-id ;; TODO player_id once init-auth-handler fixed
                :provider PROVIDER
                :provider_id (:id (json->map (:body res)))})
            (= 401 (:status (ex-data res)))
            ;; returns 403 if cant authenticate at all so no chance of endless recursion if user hasnt authorized us
                (try (get-user-profile player-id (portal/refresh-access-token player-id PROVIDER))
                    (catch Exception err (println
                        (str "Evoke:Spotify:GetPofile ERROR fetching profile with refreshed token")
                        (ex-message err) (ex-data err))))
            :else  (println (str "Error syncing provider id on *" PROVIDER "*: ") (.getMessage res))))
    ;; 4/500 codes are going thru success path so this isnt neccessary but here just in case
    (catch Exception err
         (println (str "Error syncing provider id on *" PROVIDER "*: ") (ex-message err) (ex-data err))
    ))
)

;; TODO abstract to use non-hardcoded provider vals. add base API url to oauth-providers in id
(defn sync-provider-id
    [player-id]
    (let [id (iddb/getid player-id PROVIDER)]
        (cond
            (not id) {:error "no id provider identity"}
            (not (:access_token id)) {:error "no id provider access token"}
            (not (nil? (:provider_id id))) {:error "already synced id from provider"} ;; already added synced id
            ;; TODO should move state updates into this func and make this just fetching profile data
            :else (get-user-profile player-id (:access_token id)))))

(defn get-top-tracks
    "DOCS: https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks
    player-id"
    [player-id target-player-id]
    ;; get access_token for target_player
    (let [version "0.0.1" start-time (now) limit 20
        id (iddb/getid target-player-id) ;; can only get top items as self.
        range "short_term" ;; short_term = 4 weeks medium_term = 6 months})
        url (str API_URL "/me/top/tracks?limit="limit"&time_range="range)]
    (try (let [res (client/get url (portal/oauthed-request-config (:access_token id)))]
        (cond
            ;; create action recording they visited their profile?
        ;; This will be tracked in frontend already via segment
        ;; however fits with playground model of multiple apps and selfhosted  data access
            (= 200 (:status (ex-data res))) (do
                ;; log action in db on completion
                (db/call db/batch-create-actions [{
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
                    }
                }])
            ;; (map #() (:items response))
            ;; is_local, name, href, (map :name (:artis %))
            )
            (= 401 (:status (ex-data res))) (try
                    (portal/refresh-access-token player-id PROVIDER) 
                    (get-top-tracks player-id target-player-id)
                    (catch Exception err (println
                        (str "Evoke:Spotify:Follow ERROR following with refreshed token")
                        (ex-message err) (ex-data err))))
            :else  (println (str "Error syncing provider id on *" PROVIDER "*: ") (.getMessage res)))
    ))
    )
)