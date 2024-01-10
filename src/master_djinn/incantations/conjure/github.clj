(ns master-djinn.incantations.conjure.github
    (:require [clj-http.client :as client]
            [master-djinn.util.types.core :refer [action->uuid normalize-action-type]]
            [master-djinn.portal.core :refer [refresh-access-token]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.core :refer [json->map]]
            [master-djinn.util.db.identity :as iddb]))

;; Github app vs OAuth app https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps#github-apps-that-act-on-their-own-behalf
(defonce CONFIG ((keyword PROVIDER) portal/oauth-providers))
(defonce PROVIDER "github")

(defn sync-repos
    "DOCS: https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#list-repositories-for-a-user"
    [player-id]
    (let [id (iddb/getid player-id)
        type "all" ;; owned and contributor repos
        sort "pushed" ;; get most recently contributed repos
        url (:api-uri "/users/" (:id id) "/repos")
    ])
    ;; store repos as :Resources id = (:node_id repo)
)

(defn get-commits
    "DOCS: https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28"
    [player-id]
    ;; TODO repos stored as resources (allows multiple players to contribute to them
    ;; get all respos that a user stewards (get-player-resources :Github)
    ;; url (str "/repos/" (:ext_owner repo) "/" (:name repo) "/commits?author=" (:id id) "&since=" ???)
    ;; commits (map #({:description (get-in % [:commit :message]) :start-time/end-time (get-in % [:commit :author :date])} )(parse (:body response) )
)