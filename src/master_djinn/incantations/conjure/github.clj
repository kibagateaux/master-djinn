(ns master-djinn.incantations.conjure.github
    (:require [clj-http.client :as client]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as iddb]
            [neo4j-clj.core :as neo4j]
            [master-djinn.util.core :refer [now]]
            [master-djinn.portal.logs :as log]
            [master-djinn.incantations.transmute.github :as trans]
            [master-djinn.util.core :refer [json->map map->json]]))

;; Github app vs OAuth app https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps#github-apps-that-act-on-their-own-behalf
(defonce PROVIDER "Github")
(defonce CONFIG ((keyword PROVIDER) portal/oauth-providers))

(defonce qu-get-player-repos "query(
    $username: String!,
    $visibility: RepositoryPrivacy!
) {
  user(login: $username) {
    repositories(
      first: 5,
      orderBy: {field: PUSHED_AT, direction: DESC},
      privacy: $visibility
    ) {
    nodes {
        id
        name
        description
        url
        createdAt
        pushedAt
        visibility
        fundingLinks {
          url
        }
        owner {
          username: login
        }
        collaborators {
          nodes {
            username: login
          }
        }
      }
    }
  }
}")

(defn sync-repos
    "DOCS: https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#list-repositories-for-a-user"
    [player-id]
    (println "C:Github:SyncRepos:Init")
    (let [id (iddb/getid player-id PROVIDER)
            track-spell (log/track-spell player-id PROVIDER "sync-repos" "0.0.1")
            aaa (println "C:Github:SyncRepo:id " id)
            config (assoc (portal/oauthed-request-config (:access_token id)) :body (map->json {
                :query qu-get-player-repos
                :variables {:username (:provider_id id) :visibility "PUBLIC"}}))]
    (track-spell {:stage "init"})
    (try (let [res (client/post (:graphql-uri CONFIG) config)
            body (json->map (:body res))]
        (track-spell {:stage "response"})
        (clojure.pprint/pprint res)
        (println "C:Github:SyncRepo:Response - status, body - " (:status res))
        ;; (clojure.pprint/pprint (json->map (:body res)))
        (clojure.pprint/pprint (-> (:body res) json->map :data :user :repositories :nodes))
        (if (and (:status res) (some? (:data body))) 
        ;;   (-> body :data :user :repositories :nodes
        ;;     #(trans/Repo->Resource player-id (:provider_id id) PROVIDER %)
        ;;     #(db/call db/batch-create-resources {:resources %}))
          (let [rs (map 
                (fn [repo] (trans/Repo->Resource player-id (:provider_id id) PROVIDER repo))
                (-> body :data :user :repositories :nodes))
                _ (track-spell {:stage "success"}) 
                aaa (println "\n\nC:Github:SyncRepos:post-trans" rs)
                data (db/call db/batch-create-resources {:resources rs})]
            (println "\n\nC:Github:SyncRepos:return" (:resources data))
            ;; (clojure.pprint/pprint rs)
            (:resources data))
              
            (do (track-spell {:stage "error" :error body})
              {:status 400 :error "C:Github:SyncRepos:ERROR failed to fetch - "})
            
        ))
    (catch Exception err
        (println "C:Github:SyncRepo:ERROR requesting" (ex-message err) )
        (clojure.pprint/pprint (ex-data err))
        (track-spell {:stage "error" :error (ex-data err)})
        (cond 
        (= 401 (:status (ex-data err))) (try 
          (portal/refresh-access-token player-id PROVIDER)
          (sync-repos player-id)
          (catch Exception err 
            (println (str "Conjure:Github:SyncRepo: ERROR retrieving with refreshed token") (ex-message err) (ex-data err))
            (log/handle-error err "Conjure:Github:sync-repos:ERROR" {:provder PROVIDER} player-id)
            (if (= "bad_refresh_token" (ex-message err))
              (do (track-spell {:stage "unauthorized" :error (ex-data err)})
                {:status 403 :error (ex-message err)})
              (do (track-spell {:stage "error" :error (ex-data err)})
                {:status 501 :error (ex-message err)})
        )))
      ))
    )
))

(defonce qu-get-player-commits "query($owner: String!, $repo: String!, $since: GitTimestamp!) {
    repository(owner: $owner, name: $repo) {
      name
      refs(refPrefix:\"refs/heads/\", first: 10) {
        nodes {
          target {
          ... on Commit {
            history(first: 100, since: $since) {
              nodes {
                	oid
                  committedDate
                  message
                  author {
                    name
                  }
                }
              }
          }
        }	
      }    
    }
  }
}")

(neo4j/defquery get-repos-names "
  MATCH (Avatar {id: $player_id})-->(r:Resource)--(:Provider {provider: $provider})
  RETURN COLLECT(r.name) as repo_names
")

(defn track-commits
    "DOCS: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28
    Gets all commits for ten branches (no particular order or filtering) and filters them for player Github id
    Commits are added as :Actions to database that :GENERATES the repo :Resource"
    [player-id]
    (println "tracking commits on " PROVIDER " for " player-id)
    (let [id (iddb/getid player-id PROVIDER)
          track-spell (log/track-spell player-id PROVIDER "track-commits" "0.0.1")
          _ (track-spell {:stage "init"})
          repos (:repo_names (db/call get-repos-names {:player_id player-id :provider PROVIDER}))]
          (println "track commits on repos for player" player-id repos)
      
      (cond
        (nil? id) (do (track-spell {:stage "unauthorized"})
          {:status 403 :error "unequipped"})
        (empty? repos) (do (track-spell {:stage "error" :error "must sync respos"})
          {:status 400 :error "must sync repos"})
        :else (try (let [
          since (or (:start_time (db/call db/get-last-action-time {:player_id player-id :provider PROVIDER}) db/PORTAL_DAY))
          params (println "C:Github:track-commits:params" since repos)
          reqs  (map #(client/post (:graphql-uri CONFIG)
                    (assoc (portal/oauthed-request-config (:access_token id)) :body (map->json {
                    :query qu-get-player-commits
                    :variables {:repo % :owner (:provider_id id) :since since}})))
                  repos)
          _ (track-spell {:stage "response"})
          ;;  first (println "C:Github:travk-commits:res" (first reqs))   
          
            actions (flatten (map (fn [res] (let [repo (-> res :body json->map :data :repository)
                                prepo (clojure.pprint/pprint repo)
                                ;; commits (-> repo :refs :nodes :target :history :nodes)  ; (flatten) assumes no vals in graph tree above commit
                                pcom (println "\n\n parsing commits on repo" (:name repo) "\n\n")
                                commits (flatten (map #(->> % :target :history :nodes) (->> repo :refs :nodes))) ; get array of commits nested in array of git branches
                                ;; TODO filter by author === (provider_id id) before transmuting
                                pcom (println "\n\n transmuting on repo" (:name repo) (count commits) "\n\n")
                                ;; pcom (clojure.pprint/pprint commits)
                                ]
                                (map #(trans/Commit->Action player-id (:name repo) %) commits) ;; TODO remove drop
                                ;; TODO return actions-resource-relations obj too
                                ))
                reqs))]
            (println "\n\n all commits as actions")
            (track-spell {:stage "success"})
            ;; (clojure.pprint/pprint actions)
            (db/call db/batch-create-actions {:actions actions}))
          (catch Exception err 
            (track-spell {:stage "error" :error (ex-data err)})
            (log/handle-error err "Conjure:Github:track-commit:ERROR" {:provder PROVIDER} player-id)
            (println "C:Github:trackj-commits:err" err)))
      
    ;; TODO repos stored as resources (allows multiple players to contribute to them
    ;; get all respos that a user stewards (get-player-resources :Github)
    ;; url (str "/repos/" (:ext_owner repo) "/" (:name repo) "/commits?author=" (:provider_id id) "&since=" ???)
    ;; commits (map #({:description (get-in % [:commit :message]) :start-time/end-time (get-in % [:commit :author :date])} )(parse (:body response) )
)))


;; TODO for GitApp auth flow, not OAuth flow
;; (defn get-req-config [access_token]
;;   (update (portal/oauthed-request-config access_token) :headers merge {"X-GitHub-Api-Version" "2022-11-28"}))

;; (defn request-access-token
;;   "Custom auth flow for Github apps vs OAuth Apps
;;     https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation"
;;   [gitapp-id]

;;   (try (let [config (assoc-in (get-req-config) [:headers "Authorization"] (:github-app-token load-config))
;;     res (client/get (str (:api-uri CONFIG) "/app/installations/"gitapp-id"/access_tokens") )]))

;;   )

;; (defn sync-repos
;;     "DOCS: https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#list-repositories-for-a-user
;;      diff between gql query is this gets repos that player specifically authorized app on. Not important for reads or writing as user but if we want to write as Jinni then becomes issue not having this repo list"
;;      TODO requires github specific access-token flow via Github web install not oauth flow
;;      
;;     [player-id]
;;     (println "C:Github:SyncRepos:Init")
;;     (try (let [id (iddb/getid player-id PROVIDER)
;;             aaa (println "C:Github:SyncRepo:id " id)
;;             aaa (println "C:Github:SyncRepo:req" get-req-config)
;;             res (client/get (str (:api-uri CONFIG) "/user/installations") (get-req-config))]
;;         (clojure.pprint/pprint res)
;;         (println "C:Github:SyncRepo:Response - status, body - " (:status res))
;;         (clojure.pprint/pprint (json->map (:body res)))
;;         (if (some? (:body res)) (let [rs (map 
;;                 #(trans/Repo->Resource player-id (:provider_id id) PROVIDER %)
;;                 (-> (:body res) json->map :data :user :repositories :nodes))]
;;             (println "\n\nC:Github:SyncRepos:post-trans")
;;             (clojure.pprint/pprint rs)
;;             (db/call db/batch-create-resources {:resources rs}))
;;             {:status 400 :error "C:Github:SyncRepos:ERROR failed to fetch - "}
;;         )
;;     )))
