;; GQL resolvers wrapper around spells that return data or http errors
(ns master-djinn.util.gql.incantations
    (:require [master-djinn.incantations.evoke.jinn :as j]
            [master-djinn.incantations.evoke.spotify :as spotify-e]
            [master-djinn.incantations.conjure.spotify :as spotify-c]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.core :refer [get-signer]]
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MASTER_DJINNS]]))
(defonce providers portal/oauth-providers)

(defn activate-jinni
    ;; TODO clojure.spec inputs and outputs
  [ctx args val]
  (println "activate jinn arhs:" args val)
  (let [djinn (ecrecover (:majik_msg args) (:player_id args))
        pid (get-signer ctx)
        jid (uuid nil pid (str (java.util.UUID/randomUUID)))]
    (println djinn (MASTER_DJINNS djinn))
    (println pid jid)
        ;; TODO calc kin, archetype, tone for human + jinn bdays and add to Avatar model
    (cond
      ;; TODO throw API errors. create resolver wrapper
      ;; TODO define in specs not code here
      (nil? pid) (println "Player must give their majik to activation")
      (not= (:player_id args) pid) (println "Signer !== Registrant")
      (not (MASTER_DJINNS djinn)) (println "majik msg not from powerful enough djinn")
      ;; TODO query db to make ensure they dont have a jinn already. App sepcific logic that we want to remove so no DB constaint
      :else (j/activate-jinni pid jid))))

(defn sync-provider-id
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [ctx args val]
    (let [{:keys [provider player_id]} args]
        (cond
        (nil? player_id) (println "Must input player to sync id with")
        (nil? provider) (println "Must input provider to sync id with")
        (= provider (:id (:spotify providers))) (spotify-c/sync-provider-id player_id)
        ;; (= provider github/PROVIDER) (github/sync-provider-id player_id)
        :else (println "invalid provider to sync id with"))))

(defn spotify-follow
    [ctx args val]
    (let [pid (get-signer ctx)]
        (spotify-e/follow-players pid (:target_players args))))

(defn spotify-disco
    [ctx args val]
    (let [pid (get-signer ctx)]
        (spotify-e/create-silent-disco pid (:playlist_id args))))

(defn spotify-top-tracks
    [ctx args val]
    (let [pid (get-signer ctx)]
        (spotify-c/top-tracks pid (:target_player args))))
        