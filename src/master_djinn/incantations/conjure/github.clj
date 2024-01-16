(ns master-djinn.incantations.conjure.github
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.incantations.transmute.github :as trans]
            [master-djinn.util.core :refer [json->map map->json]]
            [master-djinn.util.db.identity :as iddb]))

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
    (try (let [id (iddb/getid player-id PROVIDER)
            aaa (println "C:Github:SyncRepo:id " id)
            config (assoc (portal/oauthed-request-config (:access_token id)) :body (map->json {
                :query qu-get-player-repos
                :variables {:username (:provider_id id) :visibility "PUBLIC"}}))
            res (client/post (:graphql-uri CONFIG) config)
            body (json->map (:body res))]
        (clojure.pprint/pprint res)
        (println "C:Github:SyncRepo:Response - status, body - " (:status res))
        ;; (clojure.pprint/pprint (json->map (:body res)))
        (clojure.pprint/pprint (-> (:body res) json->map :data :user :repositories :nodes))
        (if (and (:status res) (some? (:data body))) 
        ;;   (-> body :data :user :repositories :nodes
        ;;     (fn [in] (println "post parse" in) in)
        ;;     (fn [innn] trans/Repo->Resource player-id (:provider_id id) PROVIDER innn)
        ;;     (fn [inn] (println "post trans" inn) inn)
        ;;     (fn [innnn] db/call db/batch-create-resources {:resources innnn}))
          (let [rs (do (map 
                (fn [repo] (trans/Repo->Resource player-id (:provider_id id) PROVIDER repo))
                (-> body :data :user :repositories :nodes)))
                aaa (println "\n\nC:Github:SyncRepos:post-trans" rs)
                data (db/call db/batch-create-resources {:resources rs})]
            (println "\n\nC:Github:SyncRepos:return" (:resources data))
            ;; (clojure.pprint/pprint rs)
            (:resources data))
            {:status 400 :error "C:Github:SyncRepos:ERROR failed to fetch - "}
        ))
    (catch Exception err
        (println "C:Github:SyncRepo:ERROR requesting" (ex-message err) )
        (clojure.pprint/pprint (ex-data err))
        (cond 
        (= 401 (:status (ex-data err))) (try 
          (portal/refresh-access-token player-id PROVIDER)
          (println "new access token")
          (sync-repos player-id)
          (catch Exception err (println
            (str "Conjure:Github:SyncRepo: ERROR #1 retrieving with refreshed token")
            (ex-message err) (ex-data err))
            (if (= "bad_refresh_token" (ex-message err))
              {:status 403 :error (ex-message err)}
              {:status 501 :error (ex-message err)})
        ))
  ))
))

(defonce qu-get-player-commits "query($owner: String!, $repo: String!, $since: String!) {
    repository(owner: $owner, name: $repo) {
        name
      refs(refPrefix:'refs/heads/', first: 10) {
        nodes {
          name
          target {
          ... on Commit {
            history(first: 50, since: $since) {
              pageInfo {
                hasNextPage
              }
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

(defn get-commits
    "DOCS: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28
    Gets all commits for ten branches (no particular order or filtering) and filters them for player Github id
    Commits are added as :Actions to database that :GENERATES the repo :Resource"
    [player-id]
    ;; TODO repos stored as resources (allows multiple players to contribute to them
    ;; get all respos that a user stewards (get-player-resources :Github)
    ;; url (str "/repos/" (:ext_owner repo) "/" (:name repo) "/commits?author=" (:provider_id id) "&since=" ???)
    ;; commits (map #({:description (get-in % [:commit :message]) :start-time/end-time (get-in % [:commit :author :date])} )(parse (:body response) )
)


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
