;; GQL resolvers wrapper around spells that return data or http errors
(ns master-djinn.util.gql.incantations
    (:require [master-djinn.incantations.evoke.jinn :as j]
            [master-djinn.incantations.evoke.spotify :as spotify]
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MASTER_DJINN_ADDY]]))

(defn activate-jinni
    ;; TODO clojure.spec inputs and outputs
  [ctx args val]
  (println "activate jinn arhs:" args val)
  (let [djinn (ecrecover (:majik_msg args) (:player_id args))
        pid (get-in ctx [:request :graphql-vars :signer])
        res (println "test or nil" (or nil 1))
        jid (uuid nil pid (str (java.util.UUID/randomUUID)))]
    (println djinn (MASTER_DJINN_ADDY djinn))
    (println pid jid)
        ;; TODO calc kin, archetype, tone for human + jinn bdays and add to Avatar model
    (cond
      ;; TODO throw API errors. create resolver wrapper
      ;; TODO define in specs not code here
      (nil? pid) (println "Player must give their majik to activation")
      (not= (:player_id args) pid) (println "Signer !== Registrant")
      (not (MASTER_DJINN_ADDY djinn)) (println "majik msg not from powerful enough djinn")
      ;; TODO query db to make ensure they dont have a jinn already. App sepcific logic that we want to remove so no DB constaint
      :else (j/activate-jinni pid jid))))

(defn sync-provider-id
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [ctx args val]
    (let [{:keys [provider player_id]} args]
        (cond
        (nil? player_id) (println "Must input player to sync id with")
        (nil? provider) (println "Must input provider to sync id with")
        (= provider spotify/PROVIDER) (spotify/sync-provider-id player_id)
        ;; (= provider github/PROVIDER) (github/sync-provider-id player_id)
        :else (println "invalid provider to sync id with"))))