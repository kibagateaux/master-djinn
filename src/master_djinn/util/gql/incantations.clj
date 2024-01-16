;; GQL resolvers wrapper around spells that return data or http errors
(ns master-djinn.util.gql.incantations
    (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [master-djinn.incantations.manifest.jinni :as j]
            [master-djinn.incantations.manifest.spotify :as spotify-m]
            [master-djinn.incantations.conjure.spotify :as spotify-c]
            [master-djinn.incantations.conjure.github :as github-c]
            [master-djinn.incantations.conjure.core :as c]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.core :refer [get-signer map->json]]
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
    ;; TODO check that pid doesnt already have a jid already
    (cond
      ;; TODO throw API errors. create resolver wrapper
      ;; TODO define in clojure.specs not code here
      (nil? pid) (do 
        (println "Gql:Resolv:ActivateJinni:ERROR - Player must give their majik to activation")
        {:status 400 :body (map->json { :error "Player must give their majik to activation"})})
      (not= (:player_id args) pid) (do 
        (println "Gql:Resolv:ActivateJinni:ERROR - Signer !== Registrant")
        {:status 401 :body (map->json { :error "Signer !== Registrant"})})
      (not (MASTER_DJINNS djinn)) (do 
        (println "Gql:Resolv:ActivateJinni:ERROR - majik msg not from powerful enough djinn")
        {:status 403 :body (map->json { :error "majik msg not from powerful enough djinn"})})
    ;;   (nil? pid) (println "Player must give their majik to activation")
    ;;   (not= (:player_id args) pid) (println "Signer !== Registrant")
    ;;   (not (MASTER_DJINNS djinn)) (println "majik msg not from powerful enough djinn")
      ;; TODO query db to make ensure they dont have a jinn already. App sepcific logic that we want to remove so no DB constaint
      :else (j/activate-jinni pid jid))))

(defn sync-provider-id
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [ctx args val]
    (println "util:gql:incant " args val)
    (let [{:keys [provider player_id]} args]
        (cond
            (nil? player_id) {:status 400 :error "Must input player to sync id with"}
            (nil? provider) {:status 400 :error "Must input provider to sync id with"}
            ((set (keys providers)) (keyword provider))
                (c/sync-provider-id player_id provider)
            :else {:status 400 :error "invalid provider to sync id with"})))

(defn sync-repos
    [ctx args val]
    (let [pid (get-signer ctx) provider (:provider args)]
        (cond (= github-c/PROVIDER provider) (github-c/sync-repos pid))))

(defn spotify-follow
    [ctx args val]
    (if-let [pid (get-signer ctx)] ;; most be authed request
        (spotify-m/follow-players pid (:target_players args))))

(defn spotify-disco
    [ctx args val]
    (let [pid (get-signer ctx)] ;; most be authed request
        (spotify-m/create-silent-disco pid (:playlist_id args))))

(defn spotify-top-tracks
    [ctx args val]
    (let [pid (get-signer ctx)]
        (spotify-c/top-tracks pid (:target_player args))))
        