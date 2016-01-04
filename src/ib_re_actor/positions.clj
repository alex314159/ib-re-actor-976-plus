(ns ib-re-actor.positions)

(defonce positions (atom nil))

(defn handle-portfolio-update [{:keys [type contract] :as msg}]
  (when (= type :update-portfolio)
    (swap! positions assoc contract
           (select-keys msg [:position :market-price :market-value
                             :average-cost :unrealized-gain-loss
                             :realized-gain-loss]))))

