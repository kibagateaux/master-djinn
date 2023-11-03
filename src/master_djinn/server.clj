(ns master-djinn.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [master-djinn.manifester.service :as services]))

(defn run-dev [& args]
  (let [gql (future (http/start services/custom-gql-service)) 
        http (future (http/start services/custom-service))]
        [@gql @http]))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  ;; (http/start services/default-gql-service) ;; default-service
  (http/start services/custom-gql-service)
  (http/start services/custom-service))