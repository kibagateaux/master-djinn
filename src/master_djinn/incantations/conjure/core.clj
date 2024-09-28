(ns master-djinn.incantations.conjure.core
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid normalize-action-type]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.portal.logs :as log]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]
            [master-djinn.incantations.conjure.github :as github-c]))

;; Also figure out best way to use clj-http. ideally async bc then everything is in tail but had issues with that 
;; creating response in (let) then accessing is ok but not concurrent and i could see how it might not handle errors great
(defn get-provider-id
    "Uses portal/oauth-providers config to auto fetch players' profile and parse ID on integrated service"
    [player-id provider token]
    (let [config ((keyword provider) portal/oauth-providers)
            url (:user-info-uri config)
            track-spell (log/track-spell player-id provider "sync-provider-id" "0.0.1")]
    (try (let [res (client/get url (portal/oauthed-request-config token))]
        (println "C:GetProfile:" provider ":Response - status, body - " (:status res) (:body res))
        (track-spell {:stage "init"})
        (if (some? (:body res))
            (do (println "provider id sync succesful" player-id provider ((:user-info-parser config) res))
                (track-spell {:stage "success"})
                ((:user-info-parser config) res))
            (do (println (str "C:GetProfile:" provider ":Error syncing provider id sync") res)
                (track-spell {:stage "error" :error (:body res)})
                {:error "Unknown error requesting profile"})))
    (catch Exception err
        (println (str "C:GetProfile:" provider ":Error requesting provider id: ") (ex-message err) (ex-data err))
        (log/handle-error err "Conjure:Core:get-provider-id/1:ERROR" {:provder provider} player-id)
        (track-spell {:stage "error" :error (ex-data err)})
         (cond (= 401 (:status (ex-data err)))
            ;; returns 403 if cant authenticate at all so no chance of endless recursion if user hasnt authorized us
            (try (get-provider-id player-id provider (portal/refresh-access-token player-id provider))
                (catch Exception err (do
                    (track-spell {:stage "error" :error (ex-data err)})
                    (println (str "C:" provider ":GetPofile ERROR fetching profile with refreshed token"))
                    (log/handle-error err "Conjure:Core:get-provider-id/2:ERROR" {:provder provider} player-id)
                    {:error "Couldnt refresh access token. Relogin"})))
            (= 403 (:status (ex-data err))) (do
                (track-spell {:stage "unauthorized" :error (ex-data err)})
                (println "C:GetProfile:" provider " ERROR no OAuth permissions for player " player-id)
                {:error "Must equip item for " provider " before using"}))
    ))
))

(defn sync-provider-id
    "Gets players id on an integration and save to their in game :Identity"
    [player-id provider]
    (let [id (iddb/getid player-id provider)]
        (println "\n C:sync_id:params+id " provider player-id)
        (clojure.pprint/pprint id)
        (cond
            (not id) {:error "No id provider identity"}
            (not (:access_token id)) {:error "No id provider access token"}
            (some? (:id id)) id
            :else (try (let [provider-id (get-provider-id player-id provider (:access_token id))]
                (db/call iddb/sync-provider-id {
                    :pid player-id
                    :provider provider
                    :provider_id provider-id})
                provider-id))))) ;; return just provider id according to graphql spec

(defonce conjure-spells {
    :AndroidHealthConnect (fn [pid])
    :Github github-c/track-commits})

(defn conjure-data
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [player-id]
        (cond
            (nil? player-id) {:status 400 :error "Must input player to sync id with"}
            :else (flatten (map
                (fn [provider] (let [conjure ((keyword provider) conjure-spells)]
                    ;; check if spell available for provider before calling
                    ;; return [] if no provider for simple flatten
                    (if (nil? conjure) [] (:ids (conjure player-id)))))
                ;; @DEV: implicit standard of only 1 arg that is player_id and returns {:ids uuid[]} for conjure funcs
                (:providers (db/call db/get-player-providers {:player_id player-id}))))))

(defn get-home-config
    [player-id]
    (let [res (db/call db/get-home-config {:player_id player-id})
        config-map (reduce (fn [jinni j] (into jinni {
          (keyword (:id (:jinni j))) {
            :jinni_id (:id (:jinni j))
            :summoner (:id (:summoner j))
            :jinni_type (first (filter #(or (= %1 "p2p") (= %1 "p2c")) (:labels j)))
            :widgets (:widgets j)
            :last_divi_ts (:end_time (:divi j))
          }  
        })) {} (:jinni res))]
        (clojure.pprint/pprint config-map)
        ;; ideally return as json map but cant get lacinia to do dynamic key return vals so must return list
        (vals config-map)))