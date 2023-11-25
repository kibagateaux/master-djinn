




(defproject master-djinn "0.0.1-SNAPSHOT"
  :description "Backend services for self-actualization game 
                using interactive real-world data from your phone to take care of your 
                digital twin guiding angel tomogatchi"
  :url "https://nootype.substack.com/t/jinni"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.10.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                ;; basic project dependencies
                [environ "1.2.0"]
                [clj-http "3.12.3"]
                [cheshire "5.12.0"]
                ;; HTTP Server 
                [io.pedestal/pedestal.service "0.6.1"]
                [io.pedestal/pedestal.jetty "0.6.1"]
                [com.walmartlabs/lacinia "1.2.1"]
                [com.walmartlabs/lacinia-pedestal "1.2"]
                ;; Database
                [danlentz/clj-uuid "0.1.9"]
                [gorillalabs/neo4j-clj "4.1.0"]
                [joplin.core "0.3.11"]
                ;; Cryptography
                [org.web3j/core "4.10.3"]
                ;; tbh no idea where these came from. pretty sure part of pedestal template. Look at other projects to see if we can delete
                [ch.qos.logback/logback-classic "1.2.10" :exclusions [org.slf4j/slf4j-api]]
                [org.slf4j/jul-to-slf4j "1.7.35"]
                ;; [org.slf4j/jcl-over-slf4j "1.7.35"]
                [org.slf4j/log4j-over-slf4j "1.7.35"]
                ]
  :plugins [[lein-environ "0.4.0"]]
  :resource-paths ["config", "resources"]
  :aliases {"serve" ["run" "-m" "master-djinn.server/-main"]
            "dev" ["run" "-m" "master-djinn.server/run-dev"]}
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev [:project/dev :profiles/dev] ;; merge profiles.clj into project.clj config
           ;; only edit :profiles/* in profiles.clj
           :profiles/dev  {}
           :project/dev {:aliases {"run-dev" ["trampoline" "run" "-m" "master-djinn.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.6.1"]]}

          ;;  :test [:project/test :profiles/test] ;; merge profiles.clj into project.clj config
          ;;  :profiles/test {}
          ;;  :project/test {}
          ;;  :staging [:project/staging :profiles/staging] ;; merge profiles.clj into project.clj config
          ;;  :profiles/staging {}
          ;;  :project/staging {}
          ;;  :prod [:project/prod :profiles/prod] ;; merge profiles.clj into project.clj config
          ;;  :profiles/prod {}
          ;;  :project/prod {}
            :uberjar {:aot [master-djinn.server]}}
  :main ^{:skip-aot true} master-djinn.server)
