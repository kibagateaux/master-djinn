
(ns master-djinn.manifester.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [master-djinn.util.gql.schema :as schema]
            [master-djinn.manifester.identity :as id]

            ;; for default lacinia config
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            
            ;; for manual serverr config
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.body-params :as body-params]

            ;; [com.walmartlabs.lacinia :refer [execute]]
            ;; [clojure.data.json :as json]
            ;; [clojure.string :as str]
            ))

(def gql-service (-> (schema/jinni-schema)
                 (lp/default-service nil)))
;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call server/start and server/stop on this service
(defonce runnable-gql-service (http/create-server gql-service))

(defn runnable-dev-gql-service
  [& args]
  (-> gql-service
      http/create-server
      http/start))


(defonce dev-server-options {
    :gql-path "/graphql"
    :ide-path "/graphigql"
    :oauth-init-path "/oauth/init"
    :oauth-cb-path "/oauth/callback"
    :oauth-refresh-path "/oauth/refresh"
    :gql-asset-path "/assets/graphiql" ;; TODO figure out what thismeans
    :port 8000
    :host (or (System/getenv "app.host") "localhost")
})

(defn ^:private create-custom-service
  "
  https://lacinia-pedestal.readthedocs.io/en/latest/interceptors.html
  TODO graphql/graphiql routes arent working for some reason but auth ones are.
  Even though its a 'bug' i actually like it because it basically makes them 2 different micro services on the same server
  they use same codebase (kinda not really, just db.core) and makes it more modular for the future.
  Can theoretically host same codebase on two diff servers to separate social from database secrets  
  "
  [compiled-schema options]
  (println "compiled structured schema")
  (clojure.pprint/pprint  compiled-schema)
  (clojure.pprint/pprint  (get-in compiled-schema [:objects]))
  (clojure.pprint/pprint  (get-in compiled-schema [:objects :Query]))

  (let [{:keys [port oauth-init-path oauth-cb-path oauth-refresh-path]} options
        routes #{[oauth-init-path :get (conj [(body-params/body-params)] id/oauth-init-handler) :route-name ::oauth-init]
                [oauth-cb-path :post (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-post]
                [oauth-cb-path :get (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-get]
                [oauth-refresh-path :post (conj [(body-params/body-params)] id/oauth-refresh-token-handler) :route-name ::oauth-refresh]
                }]
    (->  {:env :dev
          ::http/routes routes
          ::http/port port
          ;;  ::http/allowed-origins ["http://localhost:8080"]
          ::http/type :jetty
          ::http/join? false})))

;; I think i might need to run two servers. One for GQL and one normal one since GQL interceptors prevent default pedestal ones from being created and they block any request without a :query
(defonce runnable-custom-service
  (-> (schema/jinni-schema)
      (create-custom-service dev-server-options)
      http/create-server))