(ns test.master-djinn.gql 
    (:require [clojure.spec.alph :as spec]
            [clojure.spec.test.alpha :as test]
                [master-jinn.incantations.transmute.core :as transmuter]
                [master-jinn.util.types.core :as types]))

(->> test/check 'transmuter/multiplexer
    test/summarize-results)
