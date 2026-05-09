(ns ib-re-actor-976-plus.protobuf
  (:import [com.google.protobuf Descriptors$FieldDescriptor$JavaType Message]))

(def field-cache (atom {}))

(defn get-cached-fields
  [proto-msg]
  (let [msg-class (class proto-msg)]
    (or (@field-cache msg-class)
        (try
          (let [fields (.getFields (.getDescriptorForType proto-msg))]
            (swap! field-cache assoc msg-class fields)
            fields)
          (catch IllegalArgumentException e
            (do
              (swap! field-cache assoc msg-class nil)
              nil))))))

(defn protobuf->map
  "Simple benchmarking shows little advantage for transients."
  ([proto-msg] (protobuf->map proto-msg false false))
  ([proto-msg include-defaults?] (protobuf->map proto-msg include-defaults? false))
  ([proto-msg include-defaults? use-transients?]
   (letfn [(convert-single-value [value field]
             (condp = (.getJavaType field)
               Descriptors$FieldDescriptor$JavaType/MESSAGE (protobuf->map value include-defaults?)
               Descriptors$FieldDescriptor$JavaType/ENUM (.getName value)
               Descriptors$FieldDescriptor$JavaType/BYTE_STRING (.toByteArray value)
               value))
           (convert-value [value field]
             (if (.isRepeated field)
               (mapv #(convert-single-value % field) value)
               (convert-single-value value field)))]
     (if-let [fields (get-cached-fields proto-msg)]
       (if use-transients?
         (persistent!
          (reduce (fn [acc field]
                    (let [value (.getField proto-msg field)]
                      (if (or include-defaults?
                              (.isRepeated field)
                              (= (.getJavaType field) Descriptors$FieldDescriptor$JavaType/MESSAGE)
                              (not= value (.getDefaultValue field)))
                        (assoc! acc (.getName field) (convert-value value field))
                        acc)))
                  (transient {})
                  fields))
         (reduce (fn [acc field]
                   (let [value (.getField proto-msg field)]
                     (if (or include-defaults?
                             (.isRepeated field)
                             (= (.getJavaType field) Descriptors$FieldDescriptor$JavaType/MESSAGE)
                             (not= value (.getDefaultValue field)))
                       (assoc acc (.getName field) (convert-value value field))
                       acc)))
                 {}
                 fields))
       {}))))

(defn decode-protobuf-vals
  "Replace any protobuf Message values in the map with their decoded form."
  [m]
  (update-vals m #(if (instance? Message %) (protobuf->map %) %)))