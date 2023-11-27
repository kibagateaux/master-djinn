(ns master-djinn.incantations.evoke.github
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid action-type->name]]
            [master-djinn.portal.core :refer [refresh-access-token get-provider-auth-token]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

;; TODO function to construct Auth headers. probs in protal.identity since thats most abstract atm
;; just use neo4j/execute not defquery bc they wont be reused anywhere else

;; Also figure out best way to use clj-http. ideally async bc then everything is in tail but had issues with that 
;; creating response in (let) then accessing is ok but not concurrent and i could see how it might not handle errors great
(defonce API_URL "https://api.github.com/v1")
(defonce PROVIDER "github")

(defn get-user-profile
    "DOCS: https://docs.github.com/en/rest/users/users?apiVersion=2022-11-28"
    [player_id token]
    (try (let [url (str API_URL "/me")
                res (client/get url {:accept :json :async? false
                                    :headers {"Authorization" (str "Bearer "token) ;; (get-provider-auth-token PROVIDER);; (str "Bearer "token)
                                        "Content-Type" "application/json"}})]
        (cond
;; TODO abstract into helper on portal? (make-request url method token player_id provider onData retryFunc)
            (not (nil? (:body res))) (db/call iddb/sync-provider-id {
                :pid player_id ;; TODO player_id once init-auth-handler fixed
                :provider PROVIDER
                :provider_id (:id (json->map (:body res)))})
            (= 401 (:status (ex-data res)))
            ;; returns 403 if cant authenticate at all so no chance of endless recursion if user hasnt authorized us
                (try (get-user-profile player_id (refresh-access-token player_id PROVIDER))
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
    [player_id]
    (let [id (iddb/getid player_id PROVIDER)]
        (cond
            (not id) {:error "no id provider identity"}
            (not (:access_token id)) {:error "no id provider access token"}
            (not (nil? (:provider_id id))) {:error "already synced id from provider"} ;; already added synced id
            ;; TODO should move state updates into this func and make this just fetching profile data
            :else (get-user-profile player_id (:access_token id)))))


(defn get-commits
    "DOCS: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28"
    [player_id]
    ;; TODO repos stored as resources (allows multiple players to contribute to them
    ;; get all respos that a user stewards (get-player-resources :Github)
    ;; url (str "/repos/" (:ext_owner repo) "/" (:name repo) "/commits?author=" (:provider_id id) "&since=" ???)
    ;; commits (map #({:description (get-in % [:commit :message]) :start-time/end-time (get-in % [:commit :author :date])} )(parse (:body response) )
)