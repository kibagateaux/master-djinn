(ns master-djinn.incantations.conjure.core
    (:require [clj-http.client :as client]
            [master-djinn.util.graphql-schema :refer [action->uuid action-type->name]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.graphql-schema :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

;; Also figure out best way to use clj-http. ideally async bc then everything is in tail but had issues with that 
;; creating response in (let) then accessing is ok but not concurrent and i could see how it might not handle errors great
(defn get-provider-id
    "DOCS: "
    [player-id provider token]
    (try (let [config ((keyword provider) portal/oauth-providers)
                url (:user-info-uri config)
                res (client/get url (portal/oauthed-request-config token))]
        (println "C:GetProfile:" provider ":Response - status, body - " (:status res) (:body res))
        (if (some? (:body res))
            ((:user-info-parser config) res)
            (do 
                (println (str "C:GetProfile:" provider ":Error syncing provider id sync") res)
                {:error "Unknown error requesting profile"})))
    (catch Exception err
         (println (str "C:GetProfile:" provider ":Error requesting provider id: ") (ex-message err) (ex-data err))
         (cond (= 401 (:status (ex-data err)))
            ;; returns 403 if cant authenticate at all so no chance of endless recursion if user hasnt authorized us
            (try (get-provider-id player-id provider (portal/refresh-access-token player-id provider))
                (catch Exception err (do
                    (println (str "C:" provider ":GetPofile ERROR fetching profile with refreshed token"))
                    {:error "Couldnt refresh access token. Relogin"})))
            (= 403 (:status (ex-data err))) (do
                (println "C:GetProfile:" provider " ERROR no OAuth permissions for player " player-id)
                {:error "Must equip item for " provider " before using"}))
    ))
)

(defn sync-provider-id
    [player-id provider]
    (let [id (iddb/getid player-id provider)]
        (cond
            (not id) {:error "No id provider identity"}
            (not (:access_token id)) {:error "No id provider access token"}
            (some? (:provider_id id)) {:error "Already synced id from provider"} ;; return 200 since didnt fail?
            :else (db/call iddb/sync-provider-id {
                :pid player-id
                :provider provider
                :provider_id (get-provider-id player-id provider (:access_token id))}))))
