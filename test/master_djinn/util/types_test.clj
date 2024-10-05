(ns master-djinn.util.types-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as test]
            [clojure.test :refer :all]
            [master-djinn.util.types.core :as types])
    (:import  (org.web3j.crypto Keys)))

;; Test the specification with example data
(deftest primitive-types-test
  (testing "signer is eth address"
    (let [gen-addy (Keys/getAddress "some key thing")
        hard-addy "0xdead00000000000000000000000000000000beef"
        long-addy "0xdead00000000000000000000000000000000beefdead00000000000000000000000000000000beef"]
        (is (not (spec/valid? :master-djinn.util.types.core/signer (Keys/getAddress gen-addy))))
        (is (spec/valid? :master-djinn.util.types.core/signer (str "0x" (Keys/getAddress gen-addy))))
        (is (spec/valid? :master-djinn.util.types.core/signer (Keys/toChecksumAddress gen-addy)))
        (is (spec/valid? :master-djinn.util.types.core/signer (str "0x" gen-addy)))
        
        (is (not (spec/valid? :master-djinn.util.types.core/signer (Keys/getAddress hard-addy))))
        (is (spec/valid? :master-djinn.util.types.core/signer (str "0x" (Keys/getAddress hard-addy))))
        (is (spec/valid? :master-djinn.util.types.core/signer (Keys/toChecksumAddress hard-addy)))
        (is (spec/valid? :master-djinn.util.types.core/signer hard-addy))
        
        (is (not (spec/valid? :master-djinn.util.types.core/signer (Keys/getAddress long-addy))))
        (is (spec/valid? :master-djinn.util.types.core/signer (str "0x" (Keys/getAddress long-addy))))
        (is (thrown? Exception (Keys/toChecksumAddress long-addy)))
        (is (not (spec/valid? :master-djinn.util.types.core/signer long-addy)))
    ))
    
)