(ns ib-re-actor.account
  " Account details
  This namespace deals monitoring and updating account details.")

;; This atom contains the account details at all times
(defonce account-details (atom nil))

(defn update-account-details
  "Update a partical key in the account details"
  [{:keys [type key value currency]}]
  (case type
    :update-account-value
    (swap! account-details assoc key
           (if (nil? currency)
             value
             (vector value currency)))

    :update-account-time
    (swap! account-details assoc :last-updated value)))
