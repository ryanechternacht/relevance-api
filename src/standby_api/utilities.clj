(ns standby-api.utilities
  (:require [autoclave.core :as ac]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.string :as str]
            [honey.sql.helpers :as h]
            [java-time.api :as jt]
            [lambdaisland.uri :as uri]
            [nano-id.core :as nano]
            [standby-api.db :as db])
  (:import com.fasterxml.uuid.Generators)
  (:import com.devskiller.friendly_id.FriendlyId))

(defn get-next-ordering-query
  "generates a query designed to be used as a subquery in an `insert into`
   statement for a table like `persona` or `pain_point` that will get the
   1 + the max ordering for the current org"
  ([table organization-id]
   (-> (h/select [[:raw "COALESCE(MAX(ordering), 0) + 1"]])
       (h/from table)
       (h/where [:= :organization_id organization-id])))
  ([table organization-id & other-wheres]
   (-> (h/select [[:raw "COALESCE(MAX(ordering), 0) + 1"]])
       (h/from table)
       (h/where (apply conj
                       [:and [:= :organization_id organization-id]]
                       other-wheres)))))

(defn get-next-ordering-value
  "Similar to `get-next-ordering-query`, but returns the actual next id. This is
   more useful when you're adding multiple items at once and will set the values yourself"
  ([db table organization-id]
   (let [query (-> (h/select [[:raw "COALESCE(MAX(ordering), 0) + 1"] :ordering])
                   (h/from table)
                   (h/where [:= :organization_id organization-id]))]
     (->> query
          (db/execute db)
          first
          :ordering)))
  ([db table organization-id & other-wheres]
   (let [query (-> (h/select [[:raw "COALESCE(MAX(ordering), 0) + 1"] :ordering])
                   (h/from table)
                   (h/where (apply conj
                                   [:and [:= :organization_id organization-id]]
                                   other-wheres)))]
     (->> query
          (db/execute db)
          first
          :ordering))))

(comment
  (db/->format (get-next-ordering-query :persona 1))
  (db/->format (get-next-ordering-query :buyersphere_user_account 1 [:= :buyersphere-id 1] [:= :team "seller"]))
  (db/->>execute db/local-db (get-next-ordering-query :buyersphere_user_account -1))

  (get-next-ordering-value db/local-db :buyersphere_link 1 [:= :buyersphere_id 3])
  ;
  )

(def ^:private sanitation-policy (ac/html-merge-policies :BLOCKS :FORMATTING :LINKS))

(def sanitize-html (partial ac/html-sanitize sanitation-policy))

(comment
  (sanitize-html "<b>hello</b>")
  (sanitize-html "<script src=''></script><i>hello, world<i>")
  (sanitize-html "<div>hello, world</div><style>background: blue;</style>")
  ;
  )

(defn coerce-to-bool
  "'1', 'true', 'on' are coerced to true. everything else is nil"
  [str]
  (#{"1" "true" "on"} str))

(comment
  (coerce-to-bool "1")
  (coerce-to-bool "true")
  (coerce-to-bool "on")
  (coerce-to-bool "tru")
  (coerce-to-bool "0")
  ;
  )

(defn is-provided?
  "Used to evaluate if a query param is provided. 
   Returns false on nil, '', and false; returns true otherwise"
  [str]
  (cond
    (nil? str) false
    (false? str) false
    (string? str) (not (str/blank? str))
    :else true))

(comment
  (is-provided? 0)
  (is-provided? false)
  (is-provided? "")
  (is-provided? "a")
  (is-provided? nil)
  (is-provided? "    ")
  ;
  )

(defn kebab-case [coll]
  (cske/transform-keys csk/->kebab-case-keyword coll))

(comment
  (kebab-case {:a_b 1 :c_d "hello"})
  (kebab-case [{:a_b 1 :c_d "hello"}
               {:a_c 3 :d_e "b"}])
  (kebab-case {:a_b {:cD :e_f}})
  ;
  )

(defn to-date-string
  "Converts a inst (normally from the db) into a date string
   (e.g. '2020-02-03). This is normally for when the true value
   is a date, but it keeps getting converted into a datetime
   by honey, our db provider, etc."
  [dt]
  (-> dt
      jt/local-date
      .toString))

(def read-date-string jt/local-date)

(comment
  (read-date-string "2023-10-05")
  ;
  )

(defn index-by
  "Takes a collection and returns a map of the result of applying
   f to each entry, mapped to the entry that matches the result. 
   Only 1 result will be returned for entry (the last processed). 
   Entries where the result of applying f to item returns nil are
   dropped. 
   
   Ex: turn a set of db results into a map of id -> the result
   (normally to build lookups for intermediate processing). 
   
   If the optional vf is supplied, this fn will be applied to applied
   to each value as it is mapped. E.g. `(index-by :id :id2 db-rows)` 
   will return a map of :id -> :id2 for rows with an :id value"
  ([f coll]
   (index-by f identity coll))
  ([f vf coll]
   (->> coll
        (group-by f)
        (reduce (fn [m [k v]]
                  (if k
                    (assoc m k (vf (first v)))
                    m))
                {}))))

(comment
  (index-by :crm_opportunity_id [{:id 20, :crm_opportunity_id "006Hs00001H8xaUIAR"}
                                 {:id 19 :crm_opportunity_id "abc123"}
                                 {:id 18 :crm_opportunity_id nil}
                                 {:id 17}])
  (index-by :crm_opportunity_id :id [{:id 20, :crm_opportunity_id "006Hs00001H8xaUIAR"}
                                     {:id 19 :crm_opportunity_id "abc123"}
                                     {:id 18 :crm_opportunity_id nil}
                                     {:id 17}])
  ;
  )

;; with generous help from
;; https://stackoverflow.com/questions/11825444/clojure-base64-encoding
(defn base-64-encode-clj
  "Base64 encode a clojure data structure. Intended to be used with
   base-64-decode-clj to return a clojure data structure"
  [val]
  (->> val
       str
       .getBytes
       (.encodeToString (java.util.Base64/getEncoder))))

(defn base-64-decode-clj
  "Base64 decodes a value and runs it through the reader to get a 
   clj data structure. Intended to be used with `bsae-64-encode-clj"
  [encoded]
  (->> encoded
       (.decode (java.util.Base64/getDecoder))
       String.
       read-string))

(comment
  (base-64-encode-clj {:a 1 :b 2})
  (base-64-decode-clj "ezphIDEsIDpiIDJ9")
  (let [data {:c 1 :b 2}]
    (= data
       (base-64-decode-clj (base-64-encode-clj data))))
  ;
  )

(defn make-link
  "NOTE: ensure to include a trailing / on base-url and do not include 
   a leading / on the path or you might overwrite path infor"
  [base-url path]
  (str (uri/join base-url path)))

(comment
  (make-link "http://www.google.com/base-path/" "more/path")

  ;; BAD usages: 1. no trailing / on base-url, 2. leading '/' in path
  (make-link "http://www.google.com/base-path" "more/path")
  (make-link "http://www.google.com/base-path/" "/more/path")
  ;
  )

(defn get-domain [url]
  (->> url
       uri/uri
       ;; "www.google.com" is assumed to be all path, so we
       ;; prefer host, but use path if that's all that's set
       ((juxt :host :path))
       (keep identity)
       first
       (#(str/split % #"\."))
       (take-last 2)
       (str/join ".")))

(comment
  (get-domain "https://www.google.com")
  (get-domain "https://www.google.com/asdf/asdf/asdf")
  (get-domain "stark.api.google.com")
  ;
  )

(defn update-if-exists
  "similar to core update, but if the key doesn't exist, won't call the
   supplied fn"
  [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defn update-if-not-nil
  "similar to core update, but if the key is nil (or doesn't exit), 
   won't call the supplied fn"
  [m k f & args]
  (if (m k)
    (apply update m k f args)
    m))

(comment
  (update-if-exists {:a 1} :a inc)
  (update-if-exists {:a 1} :b inc)
  (update-if-exists {:a nil} :a inc) ;; should error!

  (update-if-not-nil {:a 1} :a inc)
  (update-if-not-nil {:a 1} :b inc)
  (update-if-not-nil {:a nil} :a inc)
  ;
  )

(defn try-parse-long
  "wraps `parse-long` to swallow exceptions from `parse-long` and return
   nil when an exception occurs."
  [s]
  (try
    (parse-long s)
    (catch Exception _ nil)))

(comment
  (try-parse-long "123")
  (try-parse-long "abc")
  (try-parse-long "123abc")
  (try-parse-long nil)
  (try-parse-long [])
  ;
  )


(def ^:private gen (Generators/timeBasedEpochGenerator))

(defn uuid-v7
  "Constructs a uuid-v7, which is a uuid that uses epoch timestamps to make
   the uuids sortable for db performance (and just it's nice to sort them). 
   A timestamp (as a java.util.Timestamp) can be supplied or the current 
   time will be used"
  ([]
   (.generate gen))
  ([timestamp]
   (.construct gen (.getTime timestamp))))

(comment
  (uuid-v7)

  (uuid-v7 (java.sql.Timestamp/valueOf "2015-07-24 09:45:44.000"))
  ;
  )

(def uuid->friendly-id #(FriendlyId/toFriendlyId %))

(def friendly-id->uuid #(FriendlyId/toUuid %))

(defn coerce-friendly-id->uuid
  "A function to convert friendly-id format to uuids. Designed for
   compojure so when no value is supplied, returns an empty string."
  [friendly-id]
  (if (is-provided? friendly-id)
    (friendly-id->uuid friendly-id)
    ""))

(comment
  (-> "019012df-88df-74cf-b543-4eb44243937c"
      java.util.UUID/fromString
      uuid->friendly-id)
  ;
  )

(def ^:private nano-alphabet "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
(def ^:private nano-id-gens (atom {}))

(defn get-nano-id [len]
  (when (not (contains? @nano-id-gens len))
    (swap! nano-id-gens assoc len (nano/custom nano-alphabet len)))
  ((@nano-id-gens len)))

(def ^:private nano-alphabet-lowercase "0123456789abcdefghijklmnopqrstuvwxyz")
(def ^:private nano-id-gens-lowercase (atom {}))

(defn get-nano-id-lowercase [len]
  (when (not (contains? @nano-id-gens-lowercase len))
    (swap! nano-id-gens-lowercase assoc len (nano/custom nano-alphabet-lowercase len)))
  ((@nano-id-gens-lowercase len)))

(comment
  (get-nano-id 8)
  @nano-id-gens

  (get-nano-id-lowercase 8)
  @nano-id-gens-lowercase
  ;
  )
