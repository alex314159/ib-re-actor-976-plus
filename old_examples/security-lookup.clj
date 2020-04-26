(ns ib-re-actor-976-plus.examples.security-lookup
  (:use [ib-re-actor-976-plus.securities]
        [clj-time.core :only [date-time]]
        [clojure.pprint :only [pprint]]))

(defn print-contracts-details [contract-details]
  (pprint contract-details))

(-> (lookup-security {:security-type :future :symbol "ES" :exchange "GLOBEX"})
    print-contracts-details)