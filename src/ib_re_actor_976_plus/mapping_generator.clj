(ns ib-re-actor-976-plus.mapping-generator
  "Auto-generates mapping declarations from IB Java classes using JavaParser.

  This namespace provides tooling to automatically generate defmapping declarations
  by parsing Java source files. The goal is to eliminate manual maintenance and
  ensure complete field coverage as the IB API evolves.

  Usage:
    (generate-mapping-for-class \"Contract\")
    (generate-all-mappings)
    (write-generated-mappings!)"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.pprint :refer [pprint]])
  (:import
    (com.github.javaparser StaticJavaParser)
    (com.github.javaparser.ast.body ClassOrInterfaceDeclaration FieldDeclaration MethodDeclaration)
    (com.github.javaparser.ast.type PrimitiveType ClassOrInterfaceType)
    (java.io File)))

(defn camel-to-kebab
  "Convert camelCase to kebab-case"
  [s]
  (-> s
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (str/replace #"([A-Z])([A-Z])(?=[a-z])" "$1-$2")
      (.toLowerCase)))

(defn remove-m-prefix
  "Remove m_ prefix from IB field names"
  [field-name]
  (if (str/starts-with? field-name "m_")
    (subs field-name 2)
    field-name))

(defn extract-enum-names
  "Parse Types.java and extract all enum names.
  Returns a set of enum class names."
  []
  (try
    (let [types-file (io/file "resources/com/ib/client/Types.java")]
      (if (.exists types-file)
        (let [java-source (slurp types-file)
              ;; Simple regex to find "public enum ClassName" declarations
              enum-matches (re-seq #"public\s+enum\s+(\w+)" java-source)]
          (set (map second enum-matches)))
        #{}))
    (catch Exception e
      (println "Warning: Could not parse Types.java for enum names:" (.getMessage e))
      #{})))

(def ^:private types-enums
  "Set of all enum names defined in Types.java.
  Lazily initialized on first use."
  (delay (extract-enum-names)))

(defn parse-java-class
  "Parse a Java class file and extract field information.
  Returns a map with :class-name, :fields (list of field info maps)"
  [class-path]
  (try
    (let [java-source (slurp class-path)
          parsed-unit (StaticJavaParser/parse java-source)
          types (.getTypes parsed-unit)]
      (when-let [class-decl (first types)]
        (let [class-name (.getNameAsString class-decl)
              fields (->> (.getFields class-decl)
                          (mapcat (fn [^FieldDeclaration field-decl]
                                    (let [field-type (.toString (.getElementType field-decl))
                                          is-private (.isPrivate field-decl)
                                          is-final (.isFinal field-decl)
                                          is-static (.isStatic field-decl)]
                                      (mapv (fn [var]
                                              {:name (.getNameAsString var)
                                               :type field-type
                                               :is-private is-private
                                               :is-final is-final
                                               :is-static is-static})
                                            (.getVariables field-decl)))))
                          (filter :is-private)
                          (remove :is-static)
                          vec)
              methods (->> (.getMethods class-decl)
                          (mapv (fn [^MethodDeclaration method]
                                  {:name (.getNameAsString method)
                                   :return-type (.toString (.getType method))
                                   :parameters (mapv #(.getNameAsString %) (.getParameters method))
                                   :is-public (.isPublic method)}))
                          (filter :is-public)
                          vec)
              ; Check for public no-arg constructors (they're separate from methods in JavaParser)
              ; We need a no-arg constructor to use (new ClassName) in defmapping
              constructors (.getConstructors class-decl)
              has-public-noarg-ctor (some (fn [ctor]
                                           (and (.isPublic ctor)
                                                (zero? (.size (.getParameters ctor)))))
                                         constructors)]
          {:class-name class-name
           :full-class-name (str "com.ib.client." class-name)
           :fields fields
           :methods methods
           :has-public-constructor has-public-noarg-ctor})))
    (catch Exception e
      (println "Error parsing" class-path ":" (.getMessage e))
      nil)))

(defn find-getter-setter
  "Find getter and setter methods for a field.
  Returns {:getter method-name :setter method-name}

  IMPORTANT: IB classes often have BOTH parameterless accessors (right(), secType())
  AND JavaBean-style accessors (getRight(), getSecType()). The parameterless ones
  return typed enums while getXxx() returns raw strings. We prefer the parameterless
  accessor to work correctly with translation layers."
  [field-name methods]
  (let [field-base (remove-m-prefix field-name)
        ; Prefer parameterless accessor (e.g., 'right') over JavaBean style (e.g., 'getRight')
        ; This is critical for IB's pattern where both exist with different return types
        getter-candidates [field-base  ; FIRST: parameterless (returns typed enum)
                          (str "get" (str/capitalize field-base))  ; SECOND: JavaBean style
                          (str "is" (str/capitalize field-base))]  ; THIRD: boolean convention
        setter-candidates [field-base  ; Parameterless setter (accepts typed enum)
                          (str "set" (str/capitalize field-base))]
        getter (some (fn [candidate]
                      (when (some #(= (:name %) candidate) methods)
                        candidate))
                    getter-candidates)
        setter (some (fn [candidate]
                      (when (some #(and (= (:name %) candidate)
                                       (= 1 (count (:parameters %))))
                                 methods)
                        candidate))
                    setter-candidates)]
    {:getter getter :setter setter}))

(defn infer-translation
  "Infer the translation type based on field type.
  Returns a keyword for the translation table, or nil if no translation needed.

  Handles both fully-qualified types (Types$SecType) and simple names (SecType, Action)
  since JavaParser may show either depending on imports."
  [field-type field-name]
  (cond
    ; IB enum types - check both fully-qualified and simple names
    (or (str/includes? field-type "Types$SecType") (= field-type "SecType")) :security-type
    (or (str/includes? field-type "Types$Right") (= field-type "Right")) :right
    (or (str/includes? field-type "Types$SecIdType") (= field-type "SecIdType")) :security-id-type
    (or (str/includes? field-type "Types$Action") (= field-type "Action")) :order-action
    (or (str/includes? field-type "Types$TimeInForce") (= field-type "TimeInForce")) :time-in-force
    (str/includes? field-type "OrderType") :order-type
    (str/includes? field-type "OrderStatus") :order-status

    ; Decimal type (needs conversion)
    (str/includes? field-type "Decimal")
    (cond
      (re-find #"(?i)(qty|quantity|size)" field-name) :string-to-decimal
      :else :string-to-decimal)

    ; Date/time conversions (commented out in translation.clj for now)
    ; (re-find #"(?i)date" field-name) :date
    ; (re-find #"(?i)time" field-name) :timestamp

    ; String multiplier should be converted to double
    (and (= field-type "String") (= field-name "m_multiplier")) :double-string

    ; Lists of order types or exchanges
    (and (str/includes? field-type "String")
         (re-find #"(?i)(ordertype|validexchange)" field-name))
    (cond
      (re-find #"(?i)ordertype" field-name) :order-types
      (re-find #"(?i)exchange" field-name) :exchanges
      :else nil)

    :else nil))

(def ^:private primitive-and-common-types
  "Types that should never be treated as nested objects"
  #{"String" "int" "Integer" "double" "Double" "boolean" "Boolean"
    "long" "Long" "Decimal" "List" "ArrayList"})

(defn infer-nested-type
  "Check if field type is a nested IB object that needs :nested handling.
  Returns the class type if nested, nil otherwise.

  Handles nested classes like Types.FundDistributionPolicyIndicator which must be
  referenced as com.ib.client.Types$FundDistributionPolicyIndicator in Clojure.

  IMPORTANT: Simple class names without package prefixes are SKIPPED because they could be:
  - Nested enums in the current class (e.g., ComboLeg.OpenClose)
  - Enums from Types.java (e.g., SecType, Right)
  - Other types we can't reliably resolve

  We only handle:
  1. Fully qualified types: com.ib.client.Something
  2. Types.* patterns: Types.FundDistributionPolicyIndicator → Types$FundDistributionPolicyIndicator"
  [field-type]
  (if (and (not-any? #(str/includes? field-type %) primitive-and-common-types)
           (or (str/starts-with? field-type "com.ib")
               (str/starts-with? field-type "Types.")))
    ; Handle different type patterns
    (cond
      ; Fully qualified: com.ib.client.Something
      (str/starts-with? field-type "com.ib.client.")
      (symbol field-type)

      ; Nested class in Types: Types.FundDistributionPolicyIndicator
      ; Must convert to: com.ib.client.Types$FundDistributionPolicyIndicator
      (str/starts-with? field-type "Types.")
      (let [nested-name (subs field-type 6)]  ; Remove "Types." prefix
        ; Check if it's actually an enum - skip if so
        (if (contains? @types-enums nested-name)
          nil
          (symbol (str "com.ib.client.Types$" nested-name))))

      :else nil)
    nil))

(defn generate-field-mapping
  "Generate a field mapping entry for defmapping.
  Returns a vector like [:kebab-key javaMethodName :translation :type] or nil if unmappable"
  [{:keys [name type]} methods]
  (let [{:keys [getter setter]} (find-getter-setter name methods)]
    (when (and getter setter)
      (let [field-base (remove-m-prefix name)
            kebab-key (keyword (camel-to-kebab field-base))
            ; Get the getter's return type - important for enums stored as strings!
            getter-method (first (filter #(= (:name %) getter) methods))
            getter-return-type (:return-type getter-method)
            ; Try translation inference on getter return type first, then field type
            translation (or (infer-translation getter-return-type name)
                           (infer-translation type name))
            nested-type (or (infer-nested-type getter-return-type)
                           (infer-nested-type type))
            base-mapping [kebab-key (symbol getter)]]
        (cond
          translation (conj base-mapping :translation translation)
          nested-type (conj base-mapping :nested nested-type)
          :else base-mapping)))))

(defn generate-mapping-declaration
  "Generate a defmapping or defmapping-readonly declaration for a parsed class.
  Returns the form as a list."
  [{:keys [class-name full-class-name fields methods has-public-constructor]}]
  (let [field-mappings (keep #(generate-field-mapping % methods) fields)
        macro-name (if has-public-constructor 'defmapping 'defmapping-readonly)
        class-symbol (symbol full-class-name)]
    (when (seq field-mappings)
      (cons macro-name (cons class-symbol field-mappings)))))

(defn find-ib-classes
  "Find all Java class files in resources/com/ib/client/"
  []
  (let [resources-dir (io/file "resources/com/ib/client")]
    (when (.exists resources-dir)
      (->> (.listFiles resources-dir)
           (filter #(and (.isFile %)
                        (str/ends-with? (.getName %) ".java")))
           (sort-by #(.getName %))))))

(defn generate-mapping-for-class
  "Generate mapping for a specific class by name (e.g., \"Contract\")"
  [class-name]
  (let [class-file (io/file (str "resources/com/ib/client/" class-name ".java"))]
    (when (.exists class-file)
      (when-let [parsed (parse-java-class class-file)]
        (generate-mapping-declaration parsed)))))

(defn generate-all-mappings
  "Generate mappings for all IB client classes.
  Returns a sequence of defmapping forms."
  []
  (let [classes (find-ib-classes)]
    (keep (fn [class-file]
            (when-let [parsed (parse-java-class class-file)]
              (generate-mapping-declaration parsed)))
          classes)))

(defn format-mapping-declaration
  "Format a mapping declaration for pretty printing"
  [decl]
  (let [macro-name (first decl)
        class-name (second decl)
        fields (drop 2 decl)
        ; Group fields into rows of reasonable width
        formatted-fields (map (fn [field]
                               (str "            " (pr-str field)))
                             fields)]
    (str "(" macro-name " " class-name "\n"
         (str/join "\n" formatted-fields)
         ")\n")))

(defn write-generated-mappings!
  "Generate all mappings and write to src/ib_re_actor_976_plus/generated_mappings.clj"
  []
  (let [mappings (generate-all-mappings)
        output-file (io/file "src/ib_re_actor_976_plus/generated_mappings.clj")
        timestamp (str (java.time.Instant/now))
        header (str "(ns ib-re-actor-976-plus.generated-mappings
  \"AUTO-GENERATED mapping declarations from IB Java classes.

  DO NOT EDIT THIS FILE MANUALLY!

  This file is generated by ib-re-actor-976-plus.mapping-generator.
  To regenerate, run: (require 'ib-re-actor-976-plus.mapping-generator)
                      (ib-re-actor-976-plus.mapping-generator/write-generated-mappings!)

  Generated on: " timestamp "

  Usage:
    (require '[ib-re-actor-976-plus.mapping-auto :refer [->map map->]])
    (require '[ib-re-actor-976-plus.generated-mappings])  ; Loads implementations

    (->map java-object)              ; Java → Clojure map
    (map-> Class clojure-map)        ; Clojure map → Java
  \"
  (:require
    [ib-re-actor-976-plus.mapping-auto :refer [defmapping defmapping-readonly]]))\n\n")]
    (spit output-file
          (str header
               (str/join "\n\n" (map format-mapping-declaration mappings))))
    (println "Generated mappings written to:" (.getPath output-file))
    (println "Total classes mapped:" (count mappings))
    :done))

(defn compare-field-coverage
  "Compare generated mappings against existing manual mappings.
  Useful for validation and identifying gaps."
  [class-name]
  (when-let [generated (generate-mapping-for-class class-name)]
    (let [generated-fields (set (map first (drop 2 generated)))]
      {:class class-name
       :generated-count (count generated-fields)
       :generated-fields (sort generated-fields)})))

(comment
  ; Usage examples:

  ; Generate mapping for a single class
  (generate-mapping-for-class "Contract")
  (generate-mapping-for-class "Order")
  (generate-mapping-for-class "ContractDetails")

  ; See field coverage comparison
  (compare-field-coverage "Order")
  (compare-field-coverage "Contract")

  ; Generate all mappings and write to file
  (write-generated-mappings!)

  ; Just see what would be generated
  (count (generate-all-mappings))
  (take 3 (generate-all-mappings))
  )