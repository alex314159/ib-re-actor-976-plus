(ns ib-re-actor-976-plus.util
  (:require
   [ib-re-actor-976-plus.translation :as t]))

(def ^:const option-flag-keywords #{:read-only})

(defmacro field-props [& property-descriptors]
  (reduce (fn [implementation [name field & options]]
            (let [option-flags (set (take-while option-flag-keywords options))
                  kw-args (drop-while option-flag-keywords options)
                  {:keys [translation]} (apply hash-map kw-args)
                  this (gensym "this")
                  val (gensym "val")]
              (assoc implementation (keyword name)
                     `(fn
                        ([~this]
                           ~(if translation
                              `(t/translate :from-ib ~translation (. ~this ~field))
                              `(. ~this ~field)))
                        ~@(if (not (option-flags :read-only))
                            `(([~this ~val]
                                 (set! (. ~this ~field)
                                       ~(if translation
                                          `(t/translate :to-ib ~translation ~val)
                                          val))
                                 ~this)))))))
          {} property-descriptors))

(defn assoc-if [map key val]
  (if val
    (assoc map key val)
    map))
