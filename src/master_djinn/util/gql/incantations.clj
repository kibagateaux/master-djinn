;; GQL resolvers wrapper around spells that return data or http errors
(ns master-djinn.util.gql.incantations
    (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [master-djinn.incantations.divine.mistral-dalle :as mistral-d]
            [master-djinn.incantations.manifest.jinni :as j]
            [master-djinn.incantations.manifest.spotify :as spotify-m]
            [master-djinn.incantations.conjure.spotify :as spotify-c]
            [master-djinn.incantations.conjure.github :as github-c]
            [master-djinn.incantations.conjure.core :as c]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.identity :as iddb]
            [master-djinn.util.core :refer [get-signer map->json]]
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MASTER_DJINNS]]))

(defonce providers portal/oauth-providers)

(defn jinni-activate
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
        (println "Gql:Resolv:ActivateJinni:ERROR - Unsigned API request")
        {:status 401 :body (map->json { :error "Player must give their majik to activation"})})
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
      :else (j/jinni-activate pid jid djinn))))

(defn jinni-activate-widget
    ;; TODO clojure.spec inputs and outputs
    [ctx args val]
    (let [pid (get-signer ctx)
        widgets (:widgets args)
        ;; provider (:provider setting)
        ;; id (iddb/getid pid provider)
        ]

    (println "activate widget args:" widgets)
    
    (cond
        ;; TODO validate that user has all provider accounts for widget selected
        (nil? pid) (do 
            (println "Gql:Resolv:ActivateWidget:ERROR - Unsigned API request")
            {:status 401 :body (map->json { :error "Player must give their majik to activation"})})
        ;; (nil? provider) (do 
        ;;     (println "Gql:Resolv:ActivateWidget:ERROR - Must include :Widget provider in widgets")
        ;;     {:status 400 :body (map->json { :error "No :Widget provider selected"})})
        ;; (nil? id) (do 
        ;;     (println "Gql:Resolv:ActivateWidget:ERROR No player :Identity for provider ")
        ;;     {:status 403 :body (map->json { :error (str "Item not equipped for " provider)})})
        (empty? widgets) (do 
            (println "Gql:Resolv:ActivateWidget:ERROR - Must provider :Widget configs to set")
            {:status 400 :body (map->json { :error "No :Widgets provided"})})
        ;; TODO clojure.spec/conform widgets ::widgets
        :else (j/activate-widget pid widgets))))


(defn get-home-config
    ;; TODO clojure.spec inputs and outputs
    "theoretically not jinni specific. Standard way of expressing gameplay preferences regardless of host or playground"
    [ctx args val]
    (let [pid (get-signer ctx)
        target_player (:player_id args)]    
    (cond
        (nil? pid) (do 
            (println "Gql:Resolv:GetHomeConfig:ERROR - Unsigned API request")
            {:status 401 :body (map->json { :error "Player must give their majik"})})
        (not= pid target_player) (do 
            (println "Gql:Resolv:GetHomeConfig:ERROR - signer id != target player id")
            {:status 403 :body (map->json { :error "Caster is not bonded to target player"})})
        ;; TODO clojure.spec/conform :HomeConfig
        :else (c/get-home-config pid))))

(defn sync-provider-id
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [ctx args val]
    (println "util:gql:incant " args val)
    (let [{:keys [provider player_id]} args]
        (cond
            (nil? player_id) {:status 400 :error "Must input player to sync id with"}
            (nil? provider) {:status 400 :error "Must input provider to sync id with"}
            ((set (keys providers)) (keyword provider)) ;; ensure valid provider
                (c/sync-provider-id player_id provider)
            :else {:status 400 :error "invalid provider to sync id with"})))


(defn conjure-data
    "@DEV: does NOT require auth because simple stateless function that mirrors data from external db"
    [ctx args val]
    (println "util:gql:incant:conj-data" args val)
    (let [{:keys [player_id]} args]
        (cond
            (nil? player_id) {:status 400 :error "Must input player to sync id with"}
            :else (c/conjure-data player_id))))

;; Tomogatchi Evolutions
(defn jinni-evolution
    "Allow anyone to initiate an evolution of a players jinni using their
    configured settings in maliksmajik-avatar-viewer widget"
    [ctx args val]
    (if-let [jid (:jinni_id args)]
        ;; TODO pull provider from widget settings and decide which to use
        (mistral-d/see-current-me jid)
        (map #(mistral-d/see-current-me %) (:jinn (db/call db/get-all-jinn)))))


;; Code Providers
(defn sync-repos
    [ctx args val]
    (let [caster (get-signer ctx)
            pid (:player_id args)
            provider (:provider args)
            id (iddb/getid pid provider)]
        (if (nil? caster)
            ;; technically dont need auth bc predefined repos pulled in, just for safety. If call sets specific repose then need auth
            {:status 401 :error "invalid provider to sync id with"}
            (do  ;; TODO cond->> refactor
                (if (nil? (:provider_id id)) (c/sync-provider-id pid provider) nil)
                (cond (= github-c/PROVIDER provider) (github-c/sync-repos pid))))))

(defn track-commits
    [ctx args val]
    (let [pid (get-signer ctx) provider (:provider args)]
        (cond (= github-c/PROVIDER provider) (github-c/track-commits pid)
            :else (println "util:gql:incant:track-commits:ERROR - invalid provider for spell " provider))))

;; Music Providers
(defn get-playlists
    [ctx args val]
    (let [pid (get-signer ctx)
        provider (:provider args)
        id (iddb/getid pid provider)]
        ;; if provider not synced yet then get player id before continuing
        (if (nil? (:provider_id id)) (c/sync-provider-id pid provider) nil)
        (cond (= spotify-c/PROVIDER provider)
                (spotify-c/get-playlists pid (:target_player args)))))

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
        