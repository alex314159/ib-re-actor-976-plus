(ns ib-re-actor-976-plus.ibflex
  (:require [clojure.xml :as xml]))

(def ib-flex-query-url "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/")

(defn ib-flex-query->request-id
  "This makes a call to the IB server to prepare the request.
  It happens within xml/parse."
  [token query]
  (->> (xml/parse (str ib-flex-query-url "SendRequest?t=" token "&q=" query "&v=3"))
       (:content)
       (filter #(= (:tag %) :ReferenceCode))
       (first)
       (:content)
       (first)))

(defn ib-flex-query->result
  "Loops every 5 seconds in case the report takes time to generate
  Default timeout after 60s"
  ([token query] (ib-flex-query->result token query 12))
  ([token query maxloops]
   (loop [attempts 0]
     (let [body (slurp (str ib-flex-query-url "GetStatement?t=" token "&q=" (ib-flex-query->request-id token query) "&v=3"))]
       (cond
         (not (re-find #"<ErrorCode>1019</ErrorCode>" body)) body
         (>= attempts maxloops) (throw (ex-info "IB Flex timeout" {:attempts attempts :body body}))
         :else (do (Thread/sleep 5000)
                   (recur (inc attempts))))))))
