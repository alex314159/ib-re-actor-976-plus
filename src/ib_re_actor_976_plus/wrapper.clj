(ns ib-re-actor-976-plus.wrapper
  (:require
    [clojure.tools.logging :as log]
    [ib-re-actor-976-plus.mapping :refer [->map]]
    [ib-re-actor-976-plus.translation :refer [boolean-account-value? integer-account-value? numeric-account-value? translate tws-version]])
  (:import
    ;(com.github.javaparser StaticJavaParser)
    ;(com.github.javaparser.ast.body MethodDeclaration)
    ;(com.github.javaparser.ast.comments Comment)
    (com.ib.client EWrapper)                                ;this is important
    (java.io StringWriter PrintWriter)))                         ; Bar TickAttrib Contract ContractDetails ;(java.io ByteArrayInputStream)



(defn- get-stack-trace [ex]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace ex pw)
    (.toString sw)))


(defn- log-exception
  ([ex msg]
   (log/error msg ": " (.getMessage ex))
   (log/error "Stack Trace: " (get-stack-trace ex)))
  ([ex]
   (log-exception ex "Error")))


(defn- is-finish? [date-string]
  (.startsWith date-string "finished"))


(defn matching-message? [handle-type id
                         {:keys [type request-id order-id ticker-id] :as message}]
  (and (= handle-type type)
       (or (nil? id)
           (= id (or request-id order-id ticker-id)))))


(defn warning-code?
  "One would think that warnings start at 2100 but there are error codes
  starting at 10000."
  [code]
  (<= 2100 code 2200))


(defn error-code? [code]
  (complement warning-code?))


(defn connection-error-code? [code]
  (#{504                                ; Not connected
     1100                               ; Connectivity between IB and TWS has been lost
     } code))


(defn warning?
  "A message is a warning if it has :type :error and has a code that is a
  warning code.

  IB also sends warnings with error codes but with a message containing
  \"Warning:\". For example, when you submit an order outside the trading hours
  you get an error code 399 but the message indicates that it is only a warning.
  "
  [{:keys [type code message] :as msg}]
  (and (= :error type)
       (or (and code (warning-code? code))
           (and message (re-seq #"Warning:" message)))))


(defn error? [{:keys [type] :as message}]
  (and (= :error type)
       (not (warning? message))))


(defn error-end?
  "Determines if a message is an error message that ends a request.

  id is the request, order or ticker id for the request."
  ([msg]
   (error-end? nil msg))
  ([req-id {:keys [type code id] :as msg}]
   (and (error? msg)
        (= req-id id))))


(def end-message-type {:tick :tick-snapshot-end
                       :open-order :open-order-end
                       :update-account-value :account-download-end
                       :update-portfolio :account-download-end
                       :account-summary :account-summary-end
                       :position :position-end
                       :contract-details :contract-details-end
                       :execution-details :execution-details-end
                       :price-bar :price-bar-complete
                       :scan-result :scan-end})


(defn request-end?
  "Predicate to determine if a message indicates a series of responses for a
  request is done.

  message-type is the type of the data coming in. For example: :price-bar
  or :open-order."
  [message-type req-id
   {:keys [type request-id order-id ticker-id] :as msg}]
  (and (= type (end-message-type message-type))
       (or (nil? req-id)
           (= req-id (or request-id order-id ticker-id)))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;Parsing EWrapper.java to implement interface;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn camel-to-kebab [s]
  (-> s
      (clojure.string/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (clojure.string/replace #"([A-Z])([A-Z])(?=[a-z])" "$1-$2")
      (.toLowerCase)))

(def text-replacements [["Map<Integer, Entry<String, Character>>" "nestedType"] ;special types, difficult to filter for
                        [#"[,\(\)]" " "]                    ;comma and parentheses become spaces
                        [#"[\t\n]" ""]                      ;get rid of tabs and new lines
                        [#"// protobuf" ""]                 ;get rid of protobuf comment line
                        [#"  +" " "]])                      ;get rid of double spaces

(defn replace-all [text] (reduce #(clojure.string/replace %1 (first %2) (second %2)) text text-replacements))

(defn remove-header [s] (subs s (.indexOf s "void")))



(def ewrapper-java-methods
  "Default version is managed in translation.clj"
  (mapv clojure.string/trim
        (drop-last
          (-> (slurp (clojure.java.io/resource (str "EWrapper_" tws-version ".java")))
              (remove-header)
              (replace-all)
              (clojure.string/split #";")))))

(defn- dispatch-message [cb msg] (cb msg))

(def error-methods
  "These methods need to be implemented separately as they're overloaded - same name with different signature.
  Interestingly, even though the type hints don't appear in the REPL, they're there and removing them makes reify fail.
  cb is the name of the dispatch function"
  (let [tv (read-string (clojure.string/replace (subs tws-version 0 5) "." ""))]
    [(if
       (> tv 1011)
       (if (>= tv 1033)
         (list (quote ^void error) [(quote this) (quote ^int id) (quote ^long errorTime) (quote ^int errorCode) (quote ^String errorMsg) (quote ^String advancedOrderRejectJson)]
               (list dispatch-message (quote cb) {:type :error :id (quote id) :time (quote errorTime) :code (quote errorCode) :message (quote errorMsg) :advanced-order-reject-json (quote advancedOrderRejectJson)}))
         (list (quote ^void error) [(quote this) (quote ^int id) (quote ^int errorCode) (quote ^String errorMsg) (quote ^String advancedOrderRejectJson)]
               (list dispatch-message (quote cb) {:type :error :id (quote id) :code (quote errorCode) :message (quote errorMsg) :advanced-order-reject-json (quote advancedOrderRejectJson)})))
       (list (quote ^void error) [(quote this) (quote ^int id) (quote ^int errorCode) (quote ^String message)]
             (list dispatch-message (quote cb) {:type :error :id (quote id) :code (quote errorCode) :message (quote message)})))

     (list (quote ^void error) [(quote this) (quote ^Exception ex)]
           (list dispatch-message (quote cb) {:type :error :ex (quote ex)}))

     (list (quote ^void error) [(quote this) (quote ^String message)]
           (list dispatch-message (quote cb) {:type :error :message (quote message)}))
     ]))

(def reification
  (let [method-entries
        (into [] (for [mdata ewrapper-java-methods :when (not= (subs mdata 0 11) "void error ")] ;filtering for error methods which are implemented separately. The space after error is to avoid also filtering for errorProto
                   (let [elements (clojure.string/split mdata #" ")
                         [mname arglist] [(second elements) (drop 2 elements)] ; first is return type, not needed
                         arg-names (mapv second (partition 2 arglist)) ; first would be type, not needed
                         arg-names-kebab-kw (map (comp keyword camel-to-kebab) arg-names)]
                     (list (symbol mname) (into [] (concat [(quote this)] (mapv symbol arg-names)))
                           (list (quote dispatch-message) (quote cb) (merge {:type (keyword (camel-to-kebab mname))} (zipmap arg-names-kebab-kw (mapv symbol arg-names))))))))]
    (concat [(quote reify) (quote EWrapper)] (concat error-methods method-entries))))

(defmacro reify-ewrapper [this cb] reification)

(defn create [cb] (eval (reify-ewrapper this cb)))

(comment
  "Work in progress to parse EWrapper.java and generate a reify form. Should be less brittle than the current."
  (defn get-method-signatures
    "Extracts method signatures from a Java interface AST."
    [parsed-unit]
    (->> (.getTypes parsed-unit)
         (mapcat #(.getMethods %))
         (mapv (fn [^MethodDeclaration method]
                 (let [comment (let [c (.getComment method)]
                                 (if (.isEmpty c) "" (str c)))
                       params (.getParameters method)
                       param-details (mapv (fn [p] {:name (.getNameAsString p) :type (.toString (.getType p))}) params)]
                   {:name        (.getNameAsString method)
                    :return-type (.toString (.getType method))
                    :parameters  param-details
                    :signature   (.toString (.getSignature method))
                    :comment     comment}))))))

(defn generate-reify
  "Generates a reify form for the EWrapper interface."
  [methods]
  `(reify com.ib.client.EWrapper
     ~@(map (fn [{:keys [name parameters]}]
              (let [param-names (mapv (fn [p] (symbol (:name p))) parameters)]
                `(~(symbol name) [~@param-names]
                   (println (str "Called " ~name " with " ~param-names)))))
            methods)))
