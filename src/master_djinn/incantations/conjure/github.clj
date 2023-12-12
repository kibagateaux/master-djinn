(ns master-djinn.incantations.conjure.github
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid action-type->name]]
            [master-djinn.portal.core :as portal]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.incantations.transmute.github :as trans]
            [master-djinn.util.types.core :refer [json->map map->json]]
            [master-djinn.util.db.identity :as iddb]))

;; Github app vs OAuth app https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps#github-apps-that-act-on-their-own-behalf
(defonce PROVIDER "Github")
(defonce CONFIG ((keyword PROVIDER) portal/oauth-providers))

(defonce get-player-repos "query(
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
            res (client/post (:api-uri CONFIG) { :body (map->json {
                :query get-player-repos
                :variables {:username (:provider_id id) :visibility "PUBLIC"}})})]
        (println "C:Github:SyncRepo:Response - status, body - " (:status res))
        (clojure.pprint/pprint (json->map (:body res)))
        (if (some? (:body res))
            (db/call db/batch-create-resources {:resources (map
                #(trans/Repo->Resource player-id (:provider_id id) PROVIDER %)
                (-> (:body res) json->map :data :user :repositories :nodes))})
            {:error "C:Github:SyncRepos:ERROR failed to fetch - "}
        ))
    (catch Exception err
        (println "C:Github:SyncRepo:ERROR requesting" (ex-data err))
        (cond 
        (= 401 (:status (ex-data err))) (portal/refresh-access-token player-id PROVIDER)
        )
    ))
    ;; store repos as :Resources provider_id = (:node_id repo)
)

(defn get-commits
    "DOCS: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28"
    [player-id]
    ;; TODO repos stored as resources (allows multiple players to contribute to them
    ;; get all respos that a user stewards (get-player-resources :Github)
    ;; url (str "/repos/" (:ext_owner repo) "/" (:name repo) "/commits?author=" (:provider_id id) "&since=" ???)
    ;; commits (map #({:description (get-in % [:commit :message]) :start-time/end-time (get-in % [:commit :author :date])} )(parse (:body response) )
)