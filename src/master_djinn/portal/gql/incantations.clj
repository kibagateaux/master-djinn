;; GQL resolvers wrapper around spells that return data or http errors
(ns master-djinn.portal.gql.incantations
    (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [master-djinn.incantations.divine.openrouter-dalle :as openrouter-d]
            [master-djinn.incantations.manifest.jinni :as j]
            [master-djinn.incantations.manifest.spotify :as spotify-m]
            [master-djinn.incantations.conjure.spotify :as spotify-c]
            [master-djinn.incantations.conjure.github :as github-c]
            [master-djinn.incantations.conjure.core :as c]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as iddb]
            [master-djinn.util.core :refer [get-signer map->json]]
            [master-djinn.util.types.core :refer [load-config uuid juuid avatar->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MASTER_DJINNS]]))

(defonce providers portal/oauth-providers)

(defn jinni-activate
    ;; TODO clojure.spec inputs and outputs
  [ctx args val]
  (println "activate jinn arhs:" args val)
  (let [djinn (ecrecover (:majik_msg args) (:player_id args))
        pid (get-signer ctx)
        jid (juuid pid)]
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

(defn jinni-waitlist-npc
    ;; TODO clojure.spec inputs and outputs
  [ctx args val]
  (println "waitlist npc args:" args val)
  (let [pid (get-signer ctx)]
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
    ;;   (nil? pid) (println "Player must give their majik to activation")
    ;;   (not= (:player_id args) pid) (println "Signer !== Registrant")
    ;;   (not (MASTER_DJINNS djinn)) (println "majik msg not from powerful enough djinn")
      ;; TODO query db to make ensure they dont have a jinn already. App sepcific logic that we want to remove so no DB constaint
      :else (j/jinni-waitlist-npc pid))))

(defn jinni-activate-widget
    ;; TODO clojure.spec inputs and outputs
    [ctx args val]
    (let [pid (get-signer ctx)
        widgets (:widgets args)
        jid (:jinni_id args)
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
        :else (j/activate-widget pid jid widgets))))


(defn jinni-join-summoning-circle
     "If proof of acceptance (majik_msg) is not included then treated as an application and jinni_id for an existing circle is required 
     If signer is the same as player_id then creates a new circle for them if one does not exist already
     "
    [ctx args val]
    (let [{:keys [majik_msg player_id jinni_id]} args
        signer (get-signer ctx)
        ; TODO if no circle yet then only target-player a.k.a creator in majik-msg. if circle exists then creator signs jinni + player
        jubmoji (ecrecover majik_msg (str "summon:" (if jinni_id (str jinni_id "." player_id) player_id)))
        {:keys [jinni summoner]} (j/get-summoning-circle jubmoji)]
        (println "joining circle player, jubmoji, summoner" player_id " : " jubmoji " : " (:id (or summoner {})))
        (cond
            (and (not= signer player_id) (not= signer (:id summoner)))
                {:status 401  :body (map->json {:error "Only summoner or summonee can request access to circle"})}

            (and (nil? jinni_id) (nil? majik_msg))
                {:status 400  :body (map->json {:error "Must include proof of acceptance or circle id to apply to"})}

            ; ensure jubmoji's circle is same one being requested for async application
            ; checked on the acceptance *from* jubmoji not on application from player
            (and (not (nil? jinni_id)) (not= jinni_id (:id jinni)))
                {:status 400 :body (map->json {:error "Invalid Jinni id for summoner who signed circle approval"})}
            
            ; should both be impossible. only need check for jinni technically if verifying jinni + jinni_id already
            ;; (and jinni_id (nil? summoner)) {:status 500  :body (map->json {:error " Probably invalid data"})}
            ;; (and jinni (nil? summoner)) {:status 500  :body (map->json {:error "Impossible. Jinni returned with no summoner"})} 
            
            (and (= player_id (:id summoner)) jinni)
                {:status 200  :body (map->json {:error "Already joined"})} ; creator of summoning circle cant apply/join
            
            ; no jinni yet for jubmoji, implies nil summoner.
            ; player that sends first join request becomes creator
            (nil? jinni) (try
                {:status 200 :body (map->json {:jid (j/create-summoning-circle player_id jubmoji)})}
            (catch Exception err
                {:status 500 :body (map->json {:error  "Error creating summoning circle"})}))

            ; if circle exists but no proof then process as application to be approved later
            (nil? majik_msg) (try
                {:status 200 :body (map->json {:jid (j/apply-summoning-circle player_id jinni_id)})}
            (catch Exception err
                {:status 500 :body (map->json {:error  "Error applying summoning circle"})}))

            ; verified 1. circle exists 2. jubmoji is owner of circle 3. specific player was approved by signer
            :else (try
                {:status 200 :body (map->json {:jid (j/join-summoning-circle player_id (:id jinni))})}
            (catch Exception err
                {:status 500 :body (map->json {:error  "Error joining summoning circle"})}))
            )))

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
    configured settings in maliksmajik-avatar-viewer widget.
    If no jinni specified then tries to upate entire game state."
    [ctx args val]
    (if-let [jid (:jinni_id args)]
        ;; TODO pull provider from widget settings and route to proper file
        (openrouter-d/see-current-me jid)
        (map #(openrouter-d/see-current-me %) (:jinn (db/call db/get-all-jinn)))))


;; Code Providers
(defn sync-repos
    [ctx args val]
    (let [caster (get-signer ctx)
            pid (:player_id args)
            provider (:provider args)
            id (iddb/getid pid provider)]
        (if (nil? id)
            ;; technically dont need auth bc predefined repos pulled in, just for safety. If call sets specific repose then need auth
            {:status 401 :error "Must be a player to feed data to jinni"}
            (do  ;; TODO do-> try + cond-> refactor
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
        